package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.network.ApiKeyAvailability.MISSING
import com.hak.hakbanggul.core.network.FakeApiKeyProvider
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.date
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// 다른 기능에 내놓는 세 UseCase(ObserveMoodUseCase, RequestRefreshUseCase, ObserveRefreshStateUseCase)
@OptIn(ExperimentalCoroutinesApi::class)
class MarketMoodExposedUseCaseTest {

    private val now = Instant.parse("2026-11-24T01:00:00Z")
    private val repository = FakeMarketDataRepository()
    private val rulesRepository = FakeMoodRulesRepository()
    private val clock = FakeClock(now)
    private val keys = FakeApiKeyProvider()

    private fun TestScope.newRefresher(): MarketDataRefresher {
        val calculator = MoodCalculator(ValuationCalculator())
        val observeMood = ObserveMoodUseCase(rulesRepository, repository, calculator, UnconfinedTestDispatcher(testScheduler))
        val observeKeys = ObserveSourceKeysUseCase(rulesRepository, keys)
        val refresh = RefreshMarketDataUseCase(repository, rulesRepository, calculator, clock)
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        return MarketDataRefresher(refresh, observeMood, observeKeys, repository, clock, appScope)
    }

    private fun holdFetch(answer: FetchResult? = null): CompletableDeferred<Unit> {
        val wait = CompletableDeferred<Unit>()
        val remote = repository.remote
        repository.fetchAnswer = { _, series ->
            wait.await()
            answer ?: FetchResult.Success(remote.build(series.map { it.series.seriesId }))
        }
        return wait
    }

    // R-58 ObserveMoodUseCase. 받기 관리자와 ViewModel 없이 가짜 저장소와 규칙, 계산기만으로 만든다.

    // 계산용 디스패처로 이 테스트의 스케줄러를 쓰는 시험용 디스패처를 넘긴다.
    private fun TestScope.observeMood(): Flow<MoodAssessment?> =
        ObserveMoodUseCase(rulesRepository, repository, MoodCalculator(ValuationCalculator()), UnconfinedTestDispatcher(testScheduler))()

    private suspend fun TestScope.firstMoodWith(stored: MoodTestData.Builder?): MoodAssessment? {
        repository.setStored(stored, null)
        return observeMood().first()
    }

    @Test
    fun `R-58 받아 둔 값이 없으면 null`() = runTest {
        assertNull(firstMoodWith(null))
    }

    @Test
    fun `R-58 데이터 A면 표시 단계 BULL, 기준 날짜 2026-11-23`() = runTest {
        val mood = firstMoodWith(dataA())
        assertNotNull(mood)
        assertEquals(MarketStage.BULL, mood!!.displayedStage)
        assertEquals(date("2026-11-23"), mood.asOfDate)
    }

    @Test
    fun `R-58 하루만 나쁜 날이면 계산 단계는 NEUTRAL이어도 표시 단계는 BULL`() = runTest {
        val mood = firstMoodWith(dataA().badDay("2026-11-23"))!!
        assertEquals(MarketStage.BULL, mood.displayedStage)
        assertEquals(MarketStage.NEUTRAL, mood.computedStage)
        assertEquals(date("2026-11-23"), mood.asOfDate)
    }

    @Test
    fun `R-58 나쁜 날이 사흘이면 표시 단계 NEUTRAL`() = runTest {
        val mood = firstMoodWith(dataA().badDay("2026-11-19", "2026-11-20", "2026-11-23"))!!
        assertEquals(MarketStage.NEUTRAL, mood.displayedStage)
        assertEquals(date("2026-11-23"), mood.asOfDate)
    }

    @Test
    fun `R-58 SPY가 통째로 빠지면 null`() = runTest {
        assertNull(firstMoodWith(dataA().removeSeries("SPY")))
    }

    @Test
    fun `R-58 받아 둔 값이 생기면 판정이 이어서 나온다`() = runTest {
        repository.setStored(null, null)
        val received = mutableListOf<MoodAssessment?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeMood().toList(received) }
        assertEquals(listOf<MoodAssessment?>(null), received)

        repository.setStored(dataA(), null)

        assertEquals(2, received.size)
        assertEquals(MarketStage.BULL, received[1]!!.displayedStage)
    }

    // R-59 RequestRefreshUseCase

    @Test
    fun `R-59 RequestRefreshUseCase는 받기가 끝난 뒤에 REFRESHED로 돌아온다`() = runTest {
        val wait = holdFetch()
        val useCase = RequestRefreshUseCase(newRefresher())
        runCurrent()

        val result = async { useCase(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        runCurrent()
        assertFalse(result.isCompleted)

        wait.complete(Unit)
        advanceUntilIdle()
        assertEquals(RefreshRequestResult.REFRESHED, result.await())
    }

    // R-60 받지 않았음을 돌려줌

    @Test
    fun `R-60 조건에 맞지 않으면 기다리지 않고 SKIPPED이고 받는 중이 된 적이 없다`() = runTest {
        keys.set(FRED, MISSING)
        val refresher = newRefresher()
        val useCase = RequestRefreshUseCase(refresher)
        val states = mutableListOf<RefreshState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { refresher.state.toList(states) }
        runCurrent()

        val result = async { useCase(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        runCurrent()

        assertTrue(result.isCompleted)
        assertEquals(RefreshRequestResult.SKIPPED, result.await())
        advanceUntilIdle()
        assertTrue(states.none { it.isRefreshing })
        assertEquals(0, repository.fetchCalls.size)
    }

    // R-61 ObserveRefreshStateUseCase

    private fun TestScope.collectState(refresher: MarketDataRefresher): List<RefreshState> {
        val states = mutableListOf<RefreshState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ObserveRefreshStateUseCase(refresher)().toList(states) }
        return states
    }

    @Test
    fun `R-61 받기가 없으면 첫 값은 받는 중 아님, 실패 없음`() = runTest {
        val states = collectState(newRefresher())
        assertEquals(RefreshState(isRefreshing = false, lastFailure = null), states.first())
    }

    @Test
    fun `R-61 화면에서 시작한 받기가 끝나면 받는 중이 풀린다`() = runTest {
        val wait = holdFetch()
        val refresher = newRefresher()
        val states = collectState(refresher)
        refresher.refresh()
        runCurrent()
        assertTrue(states.last().isRefreshing)

        wait.complete(Unit)
        advanceUntilIdle()

        assertEquals(RefreshState(isRefreshing = false, lastFailure = null), states.last())
    }

    @Test
    fun `R-61 청으로 시작한 받기가 실패로 끝나면 받는 중이 풀리고 실패가 남는다`() = runTest {
        val wait = holdFetch(FetchResult.Failure(DataFetchError.NO_CONNECTION))
        val refresher = newRefresher()
        val states = collectState(refresher)
        runCurrent()
        val result = async { refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        runCurrent()
        assertTrue(states.last().isRefreshing)

        wait.complete(Unit)
        advanceUntilIdle()

        assertEquals(RefreshState(isRefreshing = false, lastFailure = RefreshFailure.NO_CONNECTION), states.last())
        result.await()
    }

    @Test
    fun `R-61 청으로 시작한 받기가 성공으로 끝나면 받는 중이 풀리고 실패가 없다`() = runTest {
        val wait = holdFetch()
        val refresher = newRefresher()
        val states = collectState(refresher)
        runCurrent()
        val result = async { refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        runCurrent()

        wait.complete(Unit)
        advanceUntilIdle()

        assertEquals(RefreshState(isRefreshing = false, lastFailure = null), states.last())
        result.await()
    }

    // R-23 계산용 디스패처에서

    @Test
    fun `R-23 계산은 넘긴 디스패처에서 돈다`() = runTest {
        repository.setStored(dataA(), null)
        val computeDispatcher = StandardTestDispatcher(testScheduler)
        val useCase = ObserveMoodUseCase(rulesRepository, repository, MoodCalculator(ValuationCalculator()), computeDispatcher)
        val received = mutableListOf<MoodAssessment?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase().toList(received) }

        // 넘긴 디스패처는 아직 돌리지 않았다.
        assertTrue(received.isEmpty())

        runCurrent()

        assertEquals(1, received.size)
        assertNotNull(received.single())
    }

    // R-58, R-81 매매 판단

    @Test
    fun `R-58 R-81 데이터 A면 첫 값에 매매 판단이 있다`() = runTest {
        val mood = firstMoodWith(dataA())!!
        assertEquals(50, mood.valuation.pricePosition.score)
        assertEquals(ValuationStage.MIDDLE, mood.valuation.pricePosition.displayedStage)
        assertEquals(50, mood.valuation.rateBurden.score)
        assertEquals(ValuationStage.MIDDLE, mood.valuation.rateBurden.displayedStage)
    }

    @Test
    fun `R-58 R-81 DFF가 없으면 null`() = runTest {
        assertNull(firstMoodWith(dataA().removeSeries(MoodTestData.POLICY_RATE)))
    }
}
