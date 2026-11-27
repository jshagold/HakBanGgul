package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.core.network.FakeApiKeyProvider
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

// 받기 관리자의 진행(받을 차례인 ETF, 아직 보내지 않은 종목 수, 전체 시장의 값을 받음) (버전업-004 단위 2)
// 시험용 규칙, 시계 2026-11-24T01:00:00Z(가상 시간에 묶음), 키 둘 AVAILABLE, 받아 둔 값 데이터 A,
// FRED 받은 때 지금 - 1시간, ETF는 테스트마다 정한다(stale에 든 것만 받은 때 없음, 나머지 최신). 세 때 없음, 출처 데이터 A.
@OptIn(ExperimentalCoroutinesApi::class)
class MarketDataRefresherProgressTest {

    private val now = Instant.parse("2026-11-24T01:00:00Z")
    private val etfs = MoodTestData.STOCKS
    private val fredIds = listOf(MoodTestData.YIELD_CURVE, MoodTestData.HIGH_YIELD) + MoodTestData.VALUATION_SERIES
    private val repository = FakeMarketDataRepository()
    private val rulesRepository = FakeMoodRulesRepository()
    private val keys = FakeApiKeyProvider()
    private val calculator = MoodCalculator(RegimeCalculator(), ValuationCalculator())

    // 묶음 둘 규칙의 나눠 받기
    private val twoBatches = FetchPacing(2, 65.seconds, 10.seconds)

    private fun storedWithStale(vararg stale: String) {
        repository.setStored(dataA(), now.minusSeconds(3600))
        repository.setFetchedAt(etfs, Instant.parse("2026-11-23T22:00:00Z"))
        repository.setFetchedAt(stale.toList(), null)
    }

    private fun TestScope.newRefresher(pacing: FetchPacing = twoBatches): MarketDataRefresher {
        val clock = SchedulerClock(now, testScheduler)
        val observeMood = ObserveMoodUseCase(rulesRepository, repository, calculator, UnconfinedTestDispatcher(testScheduler))
        val observeKeys = ObserveSourceKeysUseCase(rulesRepository, keys)
        val refresh = RefreshMarketDataUseCase(repository, rulesRepository, calculator, FetchFreshness(), pacing, clock)
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        return MarketDataRefresher(refresh, observeMood, observeKeys, repository, rulesRepository, FetchFreshness(), clock, appScope)
            .also { runCurrent() }
    }

    private fun TestScope.collectStates(refresher: MarketDataRefresher): MutableList<RefreshState> {
        val states = mutableListOf<RefreshState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { refresher.state.toList(states) }
        return states
    }

    // Twelve Data 묶음마다의 답. null이면 출처의 값을 돌려준다.
    private fun answerBatch(answer: suspend (List<String>) -> FetchResult?) {
        val remote = repository.remote
        repository.fetchAnswer = { source: DataSource, requests ->
            val ids = requests.map { it.series.seriesId }
            (if (source == TWELVE_DATA) answer(ids) else null) ?: FetchResult.Success(remote.build(ids))
        }
    }

    // R-113 받을 차례인 ETF

    @Test
    fun `R-113 받을 차례는 0초에 QQQ, SMH, XLK, 첫 묶음이 끝나면 XLK, 끝나면 비고 받는 중이 아니다`() = runTest {
        storedWithStale("QQQ", "SMH", "XLK")
        val refresher = newRefresher()
        val states = collectStates(refresher)

        refresher.refresh()
        runCurrent()

        assertTrue(states.any { it.isRefreshing && it.pendingSeriesIds == setOf("QQQ", "SMH", "XLK") })
        assertEquals(setOf("XLK"), refresher.state.value.pendingSeriesIds)
        advanceTimeBy(30_000)
        assertEquals(setOf("XLK"), refresher.state.value.pendingSeriesIds)

        advanceUntilIdle()
        assertEquals(65_000L, currentTime)
        assertEquals(emptySet<String>(), refresher.state.value.pendingSeriesIds)
        assertFalse(refresher.state.value.isRefreshing)
        assertEquals(
            listOf(emptySet(), setOf("QQQ", "SMH", "XLK"), setOf("XLK"), emptySet()),
            states.map { it.pendingSeriesIds }.distinctConsecutive(),
        )
    }

    @Test
    fun `R-113 첫 묶음이 한도 넘음이어도 그 묶음이 끝나면 XLK만 남는다`() = runTest {
        storedWithStale("QQQ", "SMH", "XLK")
        answerBatch { ids -> if ("QQQ" in ids) FetchResult.Failure(DataFetchError.RATE_LIMITED) else null }
        val refresher = newRefresher()

        refresher.refresh()
        runCurrent()

        assertEquals(setOf("XLK"), refresher.state.value.pendingSeriesIds)
        assertTrue(refresher.state.value.isRefreshing)
        advanceUntilIdle()
    }

    @Test
    fun `R-113 첫 묶음이 키 거절이면 보내지 않을 묶음의 ETF도 빼 그때 비어 있다`() = runTest {
        storedWithStale("QQQ", "SMH", "XLK")
        answerBatch { ids -> if ("QQQ" in ids) FetchResult.Failure(DataFetchError.KEY_REJECTED) else null }
        val refresher = newRefresher()
        val states = collectStates(refresher)

        refresher.refresh()
        advanceUntilIdle()

        assertEquals(emptySet<String>(), states.last { it.isRefreshing }.pendingSeriesIds)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `R-113 ETF 넷 최신이면 받는 동안 받을 차례는 늘 비어 있다`() = runTest {
        storedWithStale()
        val refresher = newRefresher()
        val states = collectStates(refresher)

        refresher.refresh()
        advanceUntilIdle()

        assertTrue(states.any { it.isRefreshing })
        assertTrue(states.all { it.pendingSeriesIds.isEmpty() })
    }

    @Test
    fun `R-113 첫 묶음의 응답을 붙잡아 두면 그동안 셋이고 저장된 뒤에야 QQQ, SMH가 빠진다`() = runTest {
        storedWithStale("QQQ", "SMH", "XLK")
        val hold = CompletableDeferred<Unit>()
        answerBatch { ids ->
            if ("QQQ" in ids) hold.await()
            null
        }
        val refresher = newRefresher()
        // 저장할 때 받을 차례를 본다.
        val pendingAtSave = mutableListOf<Set<String>>()
        repository.failOnSaveIf = { values ->
            if ("QQQ" in values) pendingAtSave += refresher.state.value.pendingSeriesIds
            false
        }

        refresher.refresh()
        advanceTimeBy(5_000)
        assertEquals(setOf("QQQ", "SMH", "XLK"), refresher.state.value.pendingSeriesIds)

        hold.complete(Unit)
        runCurrent()

        assertEquals(listOf(setOf("QQQ", "SMH", "XLK")), pendingAtSave)
        assertEquals(setOf("XLK"), refresher.state.value.pendingSeriesIds)
        advanceUntilIdle()
    }

    // R-114 아직 보내지 않은 종목 수

    @Test
    fun `R-114 첫 묶음을 35초에 보내기 전에는 0, 보낸 뒤 2, 100초에 둘째 묶음을 보낸 뒤 0, 끝나면 0`() = runTest {
        storedWithStale(*etfs.toTypedArray())
        repository.stamps[RefreshStamp.TWELVE_DATA_REQUESTED] = Instant.parse("2026-11-24T00:59:30Z")
        val refresher = newRefresher()

        refresher.refresh()
        runCurrent()
        assertEquals(0, refresher.state.value.unsentCount)
        advanceTimeBy(34_000)
        assertEquals(0, refresher.state.value.unsentCount)

        advanceTimeBy(1_001)
        assertEquals(2, refresher.state.value.unsentCount)
        advanceTimeBy(64_000)
        assertEquals(2, refresher.state.value.unsentCount)

        advanceTimeBy(1_000)
        assertEquals(0, refresher.state.value.unsentCount)
        advanceUntilIdle()
        assertEquals(
            listOf(Instant.parse("2026-11-24T01:00:35Z"), Instant.parse("2026-11-24T01:01:40Z")),
            repository.savedStamps(RefreshStamp.TWELVE_DATA_REQUESTED),
        )
        assertFalse(refresher.state.value.isRefreshing)
        assertEquals(0, refresher.state.value.unsentCount)
    }

    @Test
    fun `R-114 기본 규칙과 기본 나눠 받기에서 열넷 모두 최신이 아니면 첫 묶음 뒤 7, 65초에 0`() = runTest {
        rulesRepository.rules.value = DefaultMoodRules.rules
        repository.setStored(null, null)
        val refresher = newRefresher(DEFAULT_PACING)

        refresher.refresh()
        runCurrent()
        assertEquals(7, refresher.state.value.unsentCount)

        advanceTimeBy(65_001)
        assertEquals(0, refresher.state.value.unsentCount)
        advanceUntilIdle()
    }

    @Test
    fun `R-114 묶음이 하나뿐이면 늘 0이다`() = runTest {
        storedWithStale(*etfs.toTypedArray())
        val refresher = newRefresher(DEFAULT_PACING)
        val states = collectStates(refresher)

        refresher.refresh()
        advanceUntilIdle()

        assertTrue(states.any { it.isRefreshing })
        assertTrue(states.all { it.unsentCount == 0 })
    }

    @Test
    fun `R-114 첫 묶음이 키 거절이면 끝난 뒤 0이고 진행이 비어 있다`() = runTest {
        storedWithStale(*etfs.toTypedArray())
        answerBatch { ids -> if ("SPY" in ids) FetchResult.Failure(DataFetchError.KEY_REJECTED) else null }
        val refresher = newRefresher()

        refresher.refresh()
        advanceUntilIdle()

        val state = refresher.state.value
        assertFalse(state.isRefreshing)
        assertEquals(0, state.unsentCount)
        assertEquals(emptySet<String>(), state.pendingSeriesIds)
    }

    // R-108 전체 시장의 값을 받음

    @Test
    fun `R-108 넷 모두 최신이 아니면 FRED와 첫 묶음이 저장된 뒤 0초에 한 번 참`() = runTest {
        storedWithStale(*etfs.toTypedArray())
        val refresher = newRefresher()
        val states = collectStates(refresher)

        refresher.refresh()
        runCurrent()

        assertTrue(refresher.state.value.broadMarketReceived)
        assertEquals(0L, currentTime)
        advanceUntilIdle()
        assertEquals(listOf(false, true, false), states.map { it.broadMarketReceived }.distinctConsecutive())
    }

    @Test
    fun `R-108 SPY가 최신이면 FRED가 저장된 뒤 0초에 참이고 첫 묶음을 기다리지 않는다`() = runTest {
        storedWithStale("QQQ", "SMH", "XLK")
        val hold = CompletableDeferred<Unit>()
        answerBatch { ids ->
            if ("QQQ" in ids) hold.await()
            null
        }
        val refresher = newRefresher()

        refresher.refresh()
        runCurrent()

        assertTrue(refresher.state.value.broadMarketReceived)
        assertEquals(0L, currentTime)
        hold.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `R-108 SPY가 최신이어도 FRED가 연결 안 됨이면 묶음이 성공해도 거짓 그대로다`() = runTest {
        storedWithStale("QQQ", "SMH", "XLK")
        val remote = repository.remote
        repository.fetchAnswer = { source, requests ->
            if (source == FRED) FetchResult.Failure(DataFetchError.NO_CONNECTION)
            else FetchResult.Success(remote.build(requests.map { it.series.seriesId }))
        }
        val refresher = newRefresher()
        val states = collectStates(refresher)

        refresher.refresh()
        advanceUntilIdle()

        assertTrue(states.none { it.broadMarketReceived })
        assertEquals(RefreshFailure.NO_CONNECTION, refresher.state.value.lastFailure)
    }

    @Test
    fun `R-108 ETF 넷 최신이면 FRED가 저장되면 참, 끝나면 거짓`() = runTest {
        storedWithStale()
        val refresher = newRefresher()
        val states = collectStates(refresher)

        refresher.refresh()
        advanceUntilIdle()

        assertEquals(listOf(false, true, false), states.map { it.broadMarketReceived }.distinctConsecutive())
        assertFalse(refresher.state.value.broadMarketReceived)
    }

    // R-59, R-61, R-22 청과 진행

    @Test
    fun `R-59 둘째 묶음을 기다리는 30초에 청하면 65초의 묶음까지 끝난 뒤 JOINED이고 진행은 비어 있다`() = runTest {
        storedWithStale(*etfs.toTypedArray())
        val refresher = newRefresher()
        refresher.refresh()
        advanceTimeBy(30_000)

        val result = async { refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE) to refresher.state.value }
        advanceUntilIdle()

        val (r, state) = result.await()
        assertEquals(RefreshRequestResult.JOINED, r)
        assertEquals(65_000L, currentTime)
        assertEquals(emptySet<String>(), state.pendingSeriesIds)
        assertEquals(0, state.unsentCount)
    }

    @Test
    fun `R-59 ETF 넷 최신이면 청하면 FRED만 받고 0초에 REFRESHED`() = runTest {
        storedWithStale()
        val refresher = newRefresher()

        val result = refresher.request(RefreshCondition.WHEN_KEYS_AVAILABLE)

        assertEquals(RefreshRequestResult.REFRESHED, result)
        assertEquals(listOf(FRED), repository.fetchCalls.map { it.source })
        assertEquals(fredIds.toSet(), repository.fetchCalls.single().requests.map { it.first }.toSet())
        assertEquals(0L, currentTime)
    }

    private fun <T> List<T>.distinctConsecutive(): List<T> =
        fold(emptyList()) { acc, item -> if (acc.isNotEmpty() && acc.last() == item) acc else acc + item }
}
