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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        val calculator = MoodCalculator(RegimeCalculator(), ValuationCalculator())
        val observeMood = ObserveMoodUseCase(rulesRepository, repository, calculator, UnconfinedTestDispatcher(testScheduler))
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
        assertTrue(shownWith(dataA().removeSeries("SPY"), now.minus(Duration.ofHours(1))))
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
        repository.fetchAnswer = { _, series ->
            wait.await()
            FetchResult.Success(remote.build(series.map { it.series.seriesId }))
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
        repository.fetchAnswer = { _, _ -> FetchResult.Failure(DataFetchError.NO_CONNECTION) }
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
        repository.fetchAnswer = { _, series ->
            wait.await()
            FetchResult.Success(remote.build(series.map { it.series.seriesId }))
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
        repository.fetchAnswer = { _, series ->
            delay(1_000)
            FetchResult.Success(remote.build(series.map { it.series.seriesId }))
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
        repository.fetchAnswer = { _, _ -> FetchResult.Failure(DataFetchError.NO_CONNECTION) }
        val refresher = newRefresher()
        refresher.refresh()
        advanceUntilIdle()
        assertEquals(RefreshFailure.NO_CONNECTION, refresher.state.value.lastFailure)

        val remote = repository.remote
        repository.fetchAnswer = { _, series -> FetchResult.Success(remote.build(series.map { it.series.seriesId })) }
        refresher.refresh()
        advanceUntilIdle()

        assertNull(refresher.state.value.lastFailure)
    }

    // R-57

    @Test
    fun `R-57 새로 만든 받기 관리자에는 지난 실패가 남지 않는다`() = runTest {
        repository.fetchAnswer = { _, _ -> FetchResult.Failure(DataFetchError.RATE_LIMITED) }
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

    // 받아 달라는 청(request)

    private val fetchCount get() = repository.fetchCalls.size

    // 테스트가 풀어 줄 때까지 fetch가 돌아오지 않게 한다. answer가 null이면 데이터 A를 돌려준다.
    private fun holdFetch(answer: FetchResult? = null): CompletableDeferred<Unit> {
        val wait = CompletableDeferred<Unit>()
        val remote = repository.remote
        repository.fetchAnswer = { _, series ->
            wait.await()
            answer ?: FetchResult.Success(remote.build(series.map { it.series.seriesId }))
        }
        return wait
    }

    private suspend fun TestScope.requestOn(refresher: MarketDataRefresher, condition: RefreshCondition): RefreshRequestResult {
        val result = async { refresher.request(condition) }
        advanceUntilIdle()
        return result.await()
    }

    private suspend fun TestScope.requestWith(condition: RefreshCondition): RefreshRequestResult {
        val refresher = newRefresher()
        runCurrent()
        return requestOn(refresher, condition)
    }

    // R-59 청으로 받기가 시작되고 끝난 뒤에 돌아옴

    @Test
    fun `R-59 청하면 받기가 시작되고 끝난 뒤에 REFRESHED로 돌아온다`() = runTest {
        val wait = holdFetch()
        val refresher = newRefresher()
        runCurrent()

        val result = async {
            val r = refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE)
            Triple(r, refresher.state.value, repository.stored.value)
        }
        runCurrent()

        assertFalse(result.isCompleted)
        assertTrue(refresher.state.value.isRefreshing)
        assertEquals(2, fetchCount)

        wait.complete(Unit)
        advanceUntilIdle()
        val (r, stateAtReturn, storedAtReturn) = result.await()
        assertEquals(RefreshRequestResult.REFRESHED, r)
        assertFalse(stateAtReturn.isRefreshing)
        assertNull(stateAtReturn.lastFailure)
        assertEquals(10, storedAtReturn.values.size)
        assertEquals(now, storedAtReturn.lastSuccessAt)
    }

    @Test
    fun `R-59 받기가 실패해도 예외 없이 REFRESHED이고 lastFailure가 반영되어 있다`() = runTest {
        repository.fetchAnswer = { _, _ -> FetchResult.Failure(DataFetchError.NO_CONNECTION) }
        val refresher = newRefresher()
        runCurrent()

        val result = async {
            refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) to refresher.state.value
        }
        advanceUntilIdle()

        val (r, stateAtReturn) = result.await()
        assertEquals(RefreshRequestResult.REFRESHED, r)
        assertFalse(stateAtReturn.isRefreshing)
        assertEquals(RefreshFailure.NO_CONNECTION, stateAtReturn.lastFailure)
    }

    // R-59 이미 받는 중이면 겹쳐 시작하지 않고 기다림

    @Test
    fun `R-59 refresh로 받는 중에 청하면 기다렸다가 JOINED이고 겹쳐 받지 않는다`() = runTest {
        val wait = holdFetch()
        val refresher = newRefresher()
        refresher.refresh()
        runCurrent()
        assertEquals(2, fetchCount)

        val result = async { refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        runCurrent()
        assertFalse(result.isCompleted)
        assertEquals(2, fetchCount)

        wait.complete(Unit)
        advanceUntilIdle()
        assertEquals(RefreshRequestResult.JOINED, result.await())
        assertEquals(2, fetchCount)
    }

    @Test
    fun `R-59 받는 중이면 WHEN_NOT_RECENTLY_STARTED도 조건을 따지지 않고 JOINED`() = runTest {
        val wait = holdFetch()
        val refresher = newRefresher()
        refresher.refresh()
        runCurrent()
        assertEquals(now, repository.lastStartedAt)

        val result = async { refresher.request(RefreshCondition.WHEN_NOT_RECENTLY_STARTED) }
        runCurrent()
        wait.complete(Unit)
        advanceUntilIdle()

        assertEquals(RefreshRequestResult.JOINED, result.await())
        assertEquals(2, fetchCount)
    }

    @Test
    fun `R-59 청이 둘 한꺼번에 오면 받기는 하나이고 REFRESHED 하나 JOINED 하나다`() = runTest {
        val wait = holdFetch()
        val refresher = newRefresher()
        runCurrent()

        val first = async { refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        val second = async { refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        runCurrent()
        wait.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, fetchCount)
        assertEquals(
            listOf(RefreshRequestResult.REFRESHED, RefreshRequestResult.JOINED).sorted(),
            listOf(first.await(), second.await()).sorted(),
        )
    }

    // R-59 새로 만든 받기 관리자

    @Test
    fun `R-59 새로 만든 받기 관리자는 아무것도 하지 않으면 받지 않는다`() = runTest {
        newRefresher()
        advanceUntilIdle()

        assertEquals(0, fetchCount)
    }

    @Test
    fun `R-59 새로 만든 받기 관리자에 청하면 받는다`() = runTest {
        assertEquals(RefreshRequestResult.REFRESHED, requestWith(RefreshCondition.WHEN_KEYS_AVAILABLE))
        assertEquals(10, repository.stored.value.values.size)
    }

    // R-59 청한 쪽이 기다리기를 그만둬도 끝까지 받아 저장

    @Test
    fun `R-59 청한 코루틴이 취소되어도 받기는 끝까지 이어져 저장된다`() = runTest {
        val wait = holdFetch()
        val refresher = newRefresher()
        runCurrent()
        val caller = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        caller.launch { refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        runCurrent()

        caller.cancel()
        wait.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.saveCalls.size)
        assertEquals(10, repository.stored.value.values.size)
        assertFalse(refresher.state.value.isRefreshing)
    }

    // R-60 WHEN_STALE

    @Test
    fun `R-60 WHEN_STALE 받아 둔 값이 없으면 받는다`() = runTest {
        assertEquals(RefreshRequestResult.REFRESHED, requestWith(RefreshCondition.WHEN_STALE))
    }

    @Test
    fun `R-60 WHEN_STALE 성공한 지 6시간 1분이면 받는다`() = runTest {
        repository.setStored(dataA(), now.minus(Duration.ofHours(6).plusMinutes(1)))
        assertEquals(RefreshRequestResult.REFRESHED, requestWith(RefreshCondition.WHEN_STALE))
    }

    @Test
    fun `R-60 WHEN_STALE 성공한 지 꼭 6시간이면 받지 않는다`() = runTest {
        repository.setStored(dataA(), now.minus(Duration.ofHours(6)))
        assertEquals(RefreshRequestResult.SKIPPED, requestWith(RefreshCondition.WHEN_STALE))
        assertEquals(0, fetchCount)
    }

    @Test
    fun `R-60 WHEN_STALE 성공한 때가 지금보다 뒤면 받는다`() = runTest {
        repository.setStored(dataA(), now.plus(Duration.ofMinutes(5)))
        assertEquals(RefreshRequestResult.REFRESHED, requestWith(RefreshCondition.WHEN_STALE))
    }

    @Test
    fun `R-60 WHEN_STALE 판정을 낼 수 없으면 1시간 전에 성공했어도 받는다`() = runTest {
        repository.setStored(dataA().removeSeries("SPY"), now.minus(Duration.ofHours(1)))
        assertEquals(RefreshRequestResult.REFRESHED, requestWith(RefreshCondition.WHEN_STALE))
    }

    @Test
    fun `R-60 WHEN_STALE FRED의 키가 없으면 받지 않는다`() = runTest {
        setKeys(AVAILABLE, MISSING)
        assertEquals(RefreshRequestResult.SKIPPED, requestWith(RefreshCondition.WHEN_STALE))
        assertEquals(0, fetchCount)
    }

    // R-60 WHEN_KEYS_AVAILABLE

    @Test
    fun `R-60 WHEN_KEYS_AVAILABLE 최근에 성공하고 시작했어도 받는다`() = runTest {
        repository.setStored(dataA(), now.minus(Duration.ofMinutes(1)))
        repository.lastStartedAt = now.minusSeconds(10)
        assertEquals(RefreshRequestResult.REFRESHED, requestWith(RefreshCondition.WHEN_KEYS_AVAILABLE))
    }

    @Test
    fun `R-60 WHEN_KEYS_AVAILABLE FRED의 키가 없으면 받지 않는다`() = runTest {
        setKeys(AVAILABLE, MISSING)
        assertEquals(RefreshRequestResult.SKIPPED, requestWith(RefreshCondition.WHEN_KEYS_AVAILABLE))
        assertEquals(0, fetchCount)
    }

    @Test
    fun `R-60 WHEN_KEYS_AVAILABLE Twelve Data의 키가 틀림이면 받지 않는다`() = runTest {
        setKeys(INVALID, AVAILABLE)
        assertEquals(RefreshRequestResult.SKIPPED, requestWith(RefreshCondition.WHEN_KEYS_AVAILABLE))
        assertEquals(0, fetchCount)
    }

    // R-60 WHEN_NOT_RECENTLY_STARTED

    @Test
    fun `R-60 WHEN_NOT_RECENTLY_STARTED 시작한 지 1분 1초면 받는다`() = runTest {
        repository.lastStartedAt = now.minusSeconds(61)
        assertEquals(RefreshRequestResult.REFRESHED, requestWith(RefreshCondition.WHEN_NOT_RECENTLY_STARTED))
    }

    @Test
    fun `R-60 WHEN_NOT_RECENTLY_STARTED 시작한 지 꼭 1분이면 받지 않는다`() = runTest {
        repository.lastStartedAt = now.minusSeconds(60)
        assertEquals(RefreshRequestResult.SKIPPED, requestWith(RefreshCondition.WHEN_NOT_RECENTLY_STARTED))
        assertEquals(0, fetchCount)
    }

    @Test
    fun `R-60 WHEN_NOT_RECENTLY_STARTED 시작한 지 59초면 받지 않는다`() = runTest {
        repository.lastStartedAt = now.minusSeconds(59)
        assertEquals(RefreshRequestResult.SKIPPED, requestWith(RefreshCondition.WHEN_NOT_RECENTLY_STARTED))
        assertEquals(0, fetchCount)
    }

    @Test
    fun `R-60 WHEN_NOT_RECENTLY_STARTED Twelve Data의 키가 없으면 받지 않는다`() = runTest {
        repository.lastStartedAt = now.minus(Duration.ofHours(2))
        setKeys(MISSING, AVAILABLE)
        assertEquals(RefreshRequestResult.SKIPPED, requestWith(RefreshCondition.WHEN_NOT_RECENTLY_STARTED))
        assertEquals(0, fetchCount)
    }

    @Test
    fun `R-60 WHEN_NOT_RECENTLY_STARTED 청으로 시작한 받기에서 1분을 넘어야 다시 받는다`() = runTest {
        val refresher = newRefresher()
        runCurrent()

        val first = requestOn(refresher, RefreshCondition.WHEN_NOT_RECENTLY_STARTED)
        clock.now = now.plusSeconds(30)
        val second = requestOn(refresher, RefreshCondition.WHEN_NOT_RECENTLY_STARTED)
        clock.now = now.plusSeconds(61)
        val third = requestOn(refresher, RefreshCondition.WHEN_NOT_RECENTLY_STARTED)

        assertEquals(
            listOf(RefreshRequestResult.REFRESHED, RefreshRequestResult.SKIPPED, RefreshRequestResult.REFRESHED),
            listOf(first, second, third),
        )
        assertEquals(4, fetchCount)
    }

    @Test
    fun `R-60 WHEN_NOT_RECENTLY_STARTED 화면에서 시작한 받기도 따진다`() = runTest {
        val refresher = newRefresher()
        refresher.refresh()
        advanceUntilIdle()

        clock.now = now.plusSeconds(30)

        assertEquals(RefreshRequestResult.SKIPPED, requestOn(refresher, RefreshCondition.WHEN_NOT_RECENTLY_STARTED))
    }

    @Test
    fun `R-60 WHEN_NOT_RECENTLY_STARTED 받기 관리자를 새로 만들어도 시작한 때를 따진다`() = runTest {
        requestWith(RefreshCondition.WHEN_NOT_RECENTLY_STARTED)
        val restarted = newRefresher()
        runCurrent()

        clock.now = now.plusSeconds(30)

        assertEquals(RefreshRequestResult.SKIPPED, requestOn(restarted, RefreshCondition.WHEN_NOT_RECENTLY_STARTED))
    }

    // R-62 시작한 때가 없거나 지금보다 뒤면 넘은 것으로 봄

    @Test
    fun `R-62 시작한 때가 없으면 WHEN_NOT_RECENTLY_STARTED로 받는다`() = runTest {
        assertNull(repository.getLastStartedAt())
        assertEquals(RefreshRequestResult.REFRESHED, requestWith(RefreshCondition.WHEN_NOT_RECENTLY_STARTED))
    }

    @Test
    fun `R-62 시작한 때가 지금보다 뒤면 WHEN_NOT_RECENTLY_STARTED로 받는다`() = runTest {
        repository.lastStartedAt = now.plus(Duration.ofMinutes(10))
        assertEquals(RefreshRequestResult.REFRESHED, requestWith(RefreshCondition.WHEN_NOT_RECENTLY_STARTED))
    }

    // R-20 판정을 낼 수 있는 값이 없음 (앱을 바꾸기 전에 받아 둔 값)

    @Test
    fun `R-20 받아 둔 값에 매매 판단의 시리즈가 없으면 1시간 전에 받았어도 받는다`() = runTest {
        val before = dataA()
        MoodTestData.VALUATION_SERIES.forEach { before.removeSeries(it) }
        assertTrue(shownWith(before, now.minus(Duration.ofHours(1))))
    }
}
