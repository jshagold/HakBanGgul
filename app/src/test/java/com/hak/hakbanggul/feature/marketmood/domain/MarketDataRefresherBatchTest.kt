package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.core.network.ApiKeyAvailability.MISSING
import com.hak.hakbanggul.core.network.FakeApiKeyProvider
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

// 받기 관리자의 나눠 받기, 전체 시장의 값을 받음, 받는 조건 (단위 4)
// 시험용 규칙, 시계 2026-11-24T01:00:00Z(가상 시간에 묶음), 키 둘 AVAILABLE, 받아 둔 값 데이터 A(성공한 때 1시간 전), 세 때 없음, 출처 데이터 A
@OptIn(ExperimentalCoroutinesApi::class)
class MarketDataRefresherBatchTest {

    private val now = Instant.parse("2026-11-24T01:00:00Z")
    private val repository = FakeMarketDataRepository().apply { setStored(dataA(), now.minus(Duration.ofHours(1))) }
    private val rulesRepository = FakeMoodRulesRepository()
    private val keys = FakeApiKeyProvider()
    private val calculator = MoodCalculator(RegimeCalculator(), ValuationCalculator())

    // 묶음 둘 규칙의 나눠 받기: [SPY, QQQ], [SMH, XLK]
    private val twoBatches = FetchPacing(2, 65.seconds, 10.seconds)

    private lateinit var observeMood: ObserveMoodUseCase

    private fun TestScope.newRefresher(pacing: FetchPacing = DEFAULT_PACING): MarketDataRefresher {
        val clock = SchedulerClock(now, testScheduler)
        observeMood = ObserveMoodUseCase(rulesRepository, repository, calculator, UnconfinedTestDispatcher(testScheduler))
        val observeKeys = ObserveSourceKeysUseCase(rulesRepository, keys)
        val refresh = RefreshMarketDataUseCase(repository, rulesRepository, calculator, pacing, clock)
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        return MarketDataRefresher(refresh, observeMood, observeKeys, repository, rulesRepository, clock, appScope).also { runCurrent() }
    }

    private fun TestScope.collectStates(refresher: MarketDataRefresher): MutableList<RefreshState> {
        val states = mutableListOf<RefreshState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { refresher.state.toList(states) }
        return states
    }

    private fun answerFirstBatch(result: FetchResult) {
        val remote = repository.remote
        repository.fetchAnswer = { source: DataSource, requests ->
            val ids = requests.map { it.series.seriesId }
            if (source == TWELVE_DATA && "SPY" in ids) result else FetchResult.Success(remote.build(ids))
        }
    }

    // R-108, R-61 전체 시장의 값을 받음

    @Test
    fun `R-108 FRED와 첫 묶음이 저장되면 받는 중인 채 참이 되고 끝나면 거짓이다`() = runTest {
        repository.remote = dataA().set("SPY", "2026-11-23", 99.0)
        val refresher = newRefresher(twoBatches)
        val states = collectStates(refresher)

        refresher.refresh()
        runCurrent()

        assertEquals(
            listOf(
                RefreshState(isRefreshing = false, lastFailure = null, broadMarketReceived = false),
                RefreshState(isRefreshing = true, lastFailure = null, broadMarketReceived = false),
                RefreshState(isRefreshing = true, lastFailure = null, broadMarketReceived = true),
            ),
            states,
        )
        assertEquals(0L, currentTime)
        // 그때의 판정은 새로 받은 SPY(2026-11-23 99.0)로 낸 것이다.
        assertEquals(MarketStage.NEUTRAL, observeMood().first()?.computedStage)

        advanceUntilIdle()

        assertEquals(RefreshState(isRefreshing = false, lastFailure = null, broadMarketReceived = false), states.last())
        assertEquals(3, repository.fetchCalls.size)
    }

    @Test
    fun `R-108 첫 묶음이 한도 넘음이면 받는 동안 계속 거짓이고 끝나면 RATE_LIMITED`() = runTest {
        answerFirstBatch(FetchResult.Failure(DataFetchError.RATE_LIMITED))
        val refresher = newRefresher(twoBatches)
        val states = collectStates(refresher)

        refresher.refresh()
        advanceUntilIdle()

        assertTrue(states.none { it.broadMarketReceived })
        assertEquals(RefreshState(isRefreshing = false, lastFailure = RefreshFailure.RATE_LIMITED), states.last())
    }

    @Test
    fun `R-108 앞 받기에서 참이 되었어도 다음 받기가 시작할 때는 거짓이다`() = runTest {
        val refresher = newRefresher()
        val states = collectStates(refresher)
        refresher.refresh()
        advanceUntilIdle()
        assertTrue(states.any { it.broadMarketReceived })
        val firstRunEnd = states.size

        refresher.refresh()
        runCurrent()

        val secondStart = states.drop(firstRunEnd).first { it.isRefreshing }
        assertFalse(secondStart.broadMarketReceived)
        advanceUntilIdle()
    }

    // R-22, R-55, R-59 묶음 사이를 기다리는 동안

    @Test
    fun `R-22 둘째 묶음을 기다리는 30초에 새로 고침을 다시 눌러도 새 받기를 시작하지 않는다`() = runTest {
        val refresher = newRefresher(twoBatches)
        refresher.refresh()
        advanceTimeBy(30_000)
        assertTrue(refresher.state.value.isRefreshing)

        refresher.refresh()
        advanceUntilIdle()

        assertEquals(3, repository.fetchCalls.size)
    }

    @Test
    fun `R-59 기다리는 동안 청하면 둘째 묶음까지 끝난 뒤 JOINED`() = runTest {
        val refresher = newRefresher(twoBatches)
        refresher.refresh()
        advanceTimeBy(30_000)

        val result = async { refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        advanceTimeBy(34_000)
        assertFalse(result.isCompleted)

        assertEquals(RefreshRequestResult.JOINED, result.await())
        assertEquals(65_000L, currentTime)
        assertEquals(3, repository.fetchCalls.size)
    }

    @Test
    fun `R-55 기다리는 동안 청한 쪽이 기다리기를 그만둬도 둘째 묶음까지 받아 저장한다`() = runTest {
        val refresher = newRefresher(twoBatches)
        val caller = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        caller.launch { refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        advanceTimeBy(30_000)

        caller.cancel()
        advanceUntilIdle()

        assertEquals(3, repository.fetchCalls.size)
        assertEquals(setOf("SMH", "XLK"), repository.saveCalls.last().values.keys)
        assertFalse(refresher.state.value.isRefreshing)
    }

    // R-20, P-45 받아 둔 종가가 없는 대상

    @Test
    fun `R-20 받아 둔 값에 XLK가 없으면 화면이 보이게 될 때 받는다`() = runTest {
        repository.setStored(dataA().removeSeries("XLK"), now.minus(Duration.ofHours(1)))
        val refresher = newRefresher()
        refresher.onScreenShown()
        advanceUntilIdle()
        assertTrue(repository.fetchCalls.isNotEmpty())
    }

    @Test
    fun `R-20 받아 둔 값에 XLK가 없으면 WHEN_STALE은 REFRESHED`() = runTest {
        repository.setStored(dataA().removeSeries("XLK"), now.minus(Duration.ofHours(1)))
        assertEquals(RefreshRequestResult.REFRESHED, newRefresher().request(RefreshCondition.WHEN_STALE))
    }

    @Test
    fun `R-20 받아 둔 XLK가 값 하나뿐이면 받지 않는다`() = runTest {
        repository.setStored(dataA().keepOnly("XLK") { it == MoodTestData.END }, now.minus(Duration.ofHours(1)))
        assertEquals(RefreshRequestResult.SKIPPED, newRefresher().request(RefreshCondition.WHEN_STALE))
    }

    @Test
    fun `R-20 데이터 A 그대로면 받지 않는다`() = runTest {
        assertEquals(RefreshRequestResult.SKIPPED, newRefresher().request(RefreshCondition.WHEN_STALE))
    }

    @Test
    fun `R-20 XLK가 없어도 키 하나가 MISSING이면 받지 않는다`() = runTest {
        repository.setStored(dataA().removeSeries("XLK"), now.minus(Duration.ofHours(1)))
        keys.set(FRED, MISSING)
        val refresher = newRefresher()
        refresher.onScreenShown()
        advanceUntilIdle()
        assertTrue(repository.fetchCalls.isEmpty())
        assertEquals(RefreshRequestResult.SKIPPED, refresher.request(RefreshCondition.WHEN_STALE))
    }

    // R-60, R-62 시작한 때와 끝난 때 가운데 늦은 때로부터 1분

    private suspend fun TestScope.requestNotRecent(startedAgo: Long?, finishedAgo: Long?): RefreshRequestResult {
        startedAgo?.let { repository.stamps[RefreshStamp.STARTED] = now.minusSeconds(it) }
        finishedAgo?.let { repository.stamps[RefreshStamp.FINISHED] = now.minusSeconds(it) }
        return newRefresher().request(RefreshCondition.WHEN_NOT_RECENTLY_STARTED)
    }

    @Test
    fun `R-60 시작 10분 전, 끝 30초 전이면 SKIPPED`() = runTest {
        assertEquals(RefreshRequestResult.SKIPPED, requestNotRecent(600, 30))
    }

    @Test
    fun `R-60 시작 3분 전, 끝 61초 전이면 REFRESHED`() = runTest {
        assertEquals(RefreshRequestResult.REFRESHED, requestNotRecent(180, 61))
    }

    @Test
    fun `R-60 시작 3분 전, 끝 60초 전이면 SKIPPED`() = runTest {
        assertEquals(RefreshRequestResult.SKIPPED, requestNotRecent(180, 60))
    }

    @Test
    fun `R-60 시작 3분 전, 끝 59초 전이면 SKIPPED`() = runTest {
        assertEquals(RefreshRequestResult.SKIPPED, requestNotRecent(180, 59))
    }

    @Test
    fun `R-60 시작 30초 전, 끝 5분 전(받는 중에 앱이 꺼짐)이면 SKIPPED`() = runTest {
        assertEquals(RefreshRequestResult.SKIPPED, requestNotRecent(30, 300))
    }

    @Test
    fun `R-60 시작 61초 전, 끝 5분 전이면 REFRESHED`() = runTest {
        assertEquals(RefreshRequestResult.REFRESHED, requestNotRecent(61, 300))
    }

    @Test
    fun `R-60 시작 2분 전, 끝난 때 없음이면 REFRESHED`() = runTest {
        assertEquals(RefreshRequestResult.REFRESHED, requestNotRecent(120, null))
    }

    @Test
    fun `R-60 둘 다 없으면 REFRESHED`() = runTest {
        assertEquals(RefreshRequestResult.REFRESHED, requestNotRecent(null, null))
    }

    @Test
    fun `R-60 끝난 때가 지금보다 1시간 뒤면 REFRESHED`() = runTest {
        assertEquals(RefreshRequestResult.REFRESHED, requestNotRecent(null, -3600))
    }

    // R-62, P-44 끝난 때는 받는 중이 풀리기 전

    @Test
    fun `R-62 받는 중이 풀린 첫 값을 받은 때 끝난 때가 이미 적혀 있고 곧바로 청하면 SKIPPED`() = runTest {
        val refresher = newRefresher()
        // 받는 중이 풀린 첫 값을 받은 때의 끝난 때(없으면 null)
        val finishedWhenReleased = mutableListOf<Instant?>()
        var sawRefreshing = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            refresher.state.collect { state ->
                if (state.isRefreshing) sawRefreshing = true
                if (sawRefreshing && !state.isRefreshing && finishedWhenReleased.isEmpty()) {
                    finishedWhenReleased += repository.stamps[RefreshStamp.FINISHED]
                }
            }
        }

        refresher.refresh()
        advanceUntilIdle()

        assertNotNull(finishedWhenReleased.single())
        assertEquals(RefreshRequestResult.SKIPPED, refresher.request(RefreshCondition.WHEN_NOT_RECENTLY_STARTED))
    }
}
