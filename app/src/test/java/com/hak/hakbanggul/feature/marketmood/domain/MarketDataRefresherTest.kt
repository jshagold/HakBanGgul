package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import com.hak.hakbanggul.core.network.ApiKeyAvailability.AVAILABLE
import com.hak.hakbanggul.core.network.ApiKeyAvailability.INVALID
import com.hak.hakbanggul.core.network.ApiKeyAvailability.MISSING
import com.hak.hakbanggul.core.network.FakeApiKeyProvider
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MarketDataRefresherTest {

    private val now = Instant.parse("2026-11-24T01:00:00Z")
    private val repository = FakeMarketDataRepository()
    private val rulesRepository = FakeMoodRulesRepository()
    private val clock = FakeClock(now)
    private val keys = FakeApiKeyProvider()

    private fun TestScope.newRefresher(): MarketDataRefresher {
        val calculator = MoodCalculator()
        val observeMood = ObserveMoodUseCase(rulesRepository, repository, calculator)
        val observeKeys = ObserveSourceKeysUseCase(rulesRepository, keys)
        val refresh = RefreshMarketDataUseCase(repository, rulesRepository, calculator, clock)
        // 앱 범위를 흉내 낸다. backgroundScope의 일은 advanceUntilIdle이 돌리지 않아 따로 둔다.
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        return MarketDataRefresher(refresh, observeMood, observeKeys, repository, clock, appScope)
    }

    private fun setKeys(twelveData: ApiKeyAvailability, fred: ApiKeyAvailability) {
        keys.availability.value = mapOf(TWELVE_DATA to twelveData, FRED to fred)
    }

    private val started get() = repository.fetchCalls.isNotEmpty()

    private fun TestScope.shownWith(stored: MoodTestData.Builder?, lastSuccessAt: Instant?): Boolean {
        repository.setStored(stored, lastSuccessAt)
        val refresher = newRefresher()
        runCurrent()
        refresher.onScreenShown()
        advanceUntilIdle()
        return started
    }

    // R-20 받을지 판정

    @Test
    fun `R-20 받아 둔 값이 없으면 받는다`() = runTest {
        assertTrue(shownWith(null, null))
    }

    @Test
    fun `R-20 판정을 낼 수 없으면 1시간 전에 받았어도 받는다`() = runTest {
        assertTrue(shownWith(dataA().removeSeries("XLK"), now.minus(Duration.ofHours(1))))
    }

    @Test
    fun `R-20 성공한 적이 없으면 받는다`() = runTest {
        assertTrue(shownWith(dataA(), null))
    }

    @Test
    fun `R-20 6시간 1분 전이면 받는다`() = runTest {
        assertTrue(shownWith(dataA(), now.minus(Duration.ofHours(6).plusMinutes(1))))
    }

    @Test
    fun `R-20 꼭 6시간 전이면 받지 않는다`() = runTest {
        assertFalse(shownWith(dataA(), now.minus(Duration.ofHours(6))))
    }

    @Test
    fun `R-20 5시간 59분 전이면 받지 않는다`() = runTest {
        assertFalse(shownWith(dataA(), now.minus(Duration.ofHours(5).plusMinutes(59))))
    }

    @Test
    fun `R-20 성공한 때가 지금보다 뒤면 받는다`() = runTest {
        assertTrue(shownWith(dataA(), now.plus(Duration.ofMinutes(1))))
    }

    @Test
    fun `R-20 FRED의 키가 없으면 받지 않는다`() = runTest {
        setKeys(AVAILABLE, MISSING)
        assertFalse(shownWith(null, null))
    }

    @Test
    fun `R-20 Twelve Data의 키가 틀림이면 받지 않는다`() = runTest {
        setKeys(INVALID, AVAILABLE)
        assertFalse(shownWith(null, null))
    }

    // R-21

    @Test
    fun `R-21 받는 중이 아니면 refresh로 받기가 시작되고 받는 중이 된다`() = runTest {
        repository.setStored(dataA(), now.minus(Duration.ofMinutes(1)))
        val wait = CompletableDeferred<Unit>()
        val remote = repository.remote
        repository.fetchAnswer = { _, series, _ ->
            wait.await()
            FetchResult.Success(remote.build(series.map { it.seriesId }))
        }
        val refresher = newRefresher()
        runCurrent()

        refresher.refresh()
        runCurrent()

        assertTrue(started)
        assertTrue(refresher.state.value.isRefreshing)
        wait.complete(Unit)
    }

    // R-28

    @Test
    fun `R-28 연결 안 됨으로 끝난 뒤 refresh로 다시 받는다`() = runTest {
        repository.fetchAnswer = { _, _, _ -> FetchResult.Failure(DataFetchError.NO_CONNECTION) }
        val refresher = newRefresher()
        refresher.refresh()
        advanceUntilIdle()
        assertEquals(RefreshFailure.NO_CONNECTION, refresher.state.value.lastFailure)
        repository.fetchCalls.clear()

        refresher.refresh()
        advanceUntilIdle()

        assertTrue(started)
    }

    // R-22

    @Test
    fun `R-22 받는 중에는 refresh와 onScreenShown이 받기를 겹쳐 시작하지 않는다`() = runTest {
        val wait = CompletableDeferred<Unit>()
        val remote = repository.remote
        repository.fetchAnswer = { _, series, _ ->
            wait.await()
            FetchResult.Success(remote.build(series.map { it.seriesId }))
        }
        val refresher = newRefresher()
        refresher.refresh()
        runCurrent()

        refresher.refresh()
        refresher.onScreenShown()
        runCurrent()

        assertEquals(listOf(TWELVE_DATA, FRED), repository.fetchCalls.map { it.source }.sortedBy { it.ordinal })
        wait.complete(Unit)
        advanceUntilIdle()
    }

    // R-55

    @Test
    fun `R-55 refresh를 부른 쪽이 취소되어도 받기는 끝까지 이어져 저장된다`() = runTest {
        val remote = repository.remote
        repository.fetchAnswer = { _, series, _ ->
            delay(1_000)
            FetchResult.Success(remote.build(series.map { it.seriesId }))
        }
        val refresher = newRefresher()
        val caller = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        caller.launch { refresher.refresh() }
        runCurrent()

        caller.cancel()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, repository.saveCalls.size)
        assertFalse(refresher.state.value.isRefreshing)
    }

    // R-47

    @Test
    fun `R-47 실패 뒤 성공하면 lastFailure가 null이 된다`() = runTest {
        repository.fetchAnswer = { _, _, _ -> FetchResult.Failure(DataFetchError.NO_CONNECTION) }
        val refresher = newRefresher()
        refresher.refresh()
        advanceUntilIdle()
        assertEquals(RefreshFailure.NO_CONNECTION, refresher.state.value.lastFailure)

        val remote = repository.remote
        repository.fetchAnswer = { _, series, _ -> FetchResult.Success(remote.build(series.map { it.seriesId })) }
        refresher.refresh()
        advanceUntilIdle()

        assertNull(refresher.state.value.lastFailure)
    }

    // R-57

    @Test
    fun `R-57 새로 만든 받기 관리자에는 지난 실패가 남지 않는다`() = runTest {
        repository.fetchAnswer = { _, _, _ -> FetchResult.Failure(DataFetchError.RATE_LIMITED) }
        val first = newRefresher()
        first.refresh()
        advanceUntilIdle()
        assertEquals(RefreshFailure.RATE_LIMITED, first.state.value.lastFailure)

        val second = newRefresher()

        assertEquals(RefreshState(isRefreshing = false, lastFailure = null), second.state.value)
        assertTrue(repository.saveCalls.isEmpty())
    }

    // R-53 모두 저장됨이 되면 받기

    @Test
    fun `R-53 키가 모두 AVAILABLE로 바뀌면 받는다`() = runTest {
        setKeys(AVAILABLE, MISSING)
        newRefresher()
        runCurrent()

        keys.set(FRED, AVAILABLE)
        advanceUntilIdle()

        assertTrue(started)
    }

    @Test
    fun `R-53 처음부터 모두 AVAILABLE이면 받지 않는다`() = runTest {
        newRefresher()
        advanceUntilIdle()

        assertFalse(started)
    }

    @Test
    fun `R-53 하나만 AVAILABLE로 바뀌면 받지 않는다`() = runTest {
        setKeys(INVALID, MISSING)
        newRefresher()
        runCurrent()

        keys.set(TWELVE_DATA, AVAILABLE)
        advanceUntilIdle()

        assertFalse(started)
    }
}
