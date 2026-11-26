package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.HIGH_YIELD
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.STOCKS
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.YIELD_CURVE
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataB
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

// 받는 때 2026-11-24T01:00:00Z(미국 동부 2026-11-23), 받아 둔 값 없음, 출처 데이터 A, 세 때 없음.
// 시계는 가상 시간에 묶어 시각을 가상 시간의 처음(0초)으로부터 잰다.
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshMarketDataUseCaseTest {

    private val repository = FakeMarketDataRepository()
    private val rulesRepository = FakeMoodRulesRepository()
    private var start: Instant = Instant.parse("2026-11-24T01:00:00Z")
    private val startedAt = Instant.parse("2026-11-24T01:00:00Z")

    // 묶음 둘 규칙의 나눠 받기: Twelve Data가 [SPY, QQQ], [SMH, XLK] 두 묶음이 된다.
    private val twoBatches = FetchPacing(2, 65.seconds, 10.seconds)

    // FRED 시리즈 여섯: 장세 항목 둘과 매매 판단의 넷
    private val fredIds = listOf(YIELD_CURVE, HIGH_YIELD) + MoodTestData.VALUATION_SERIES

    // 보낸 요청: 출처, 시리즈 ID, 보낸 때(가상 시간 밀리초)
    private data class Sent(val source: DataSource, val ids: List<String>, val at: Long)

    private val sent = mutableListOf<Sent>()

    // 요청마다의 답. null이면 출처(repository.remote)의 값을 돌려준다.
    private var plan: suspend (DataSource, List<String>) -> FetchResult? = { _, _ -> null }

    private fun TestScope.useCase(pacing: FetchPacing = DEFAULT_PACING): RefreshMarketDataUseCase {
        repository.fetchAnswer = { source, requests ->
            val ids = requests.map { it.series.seriesId }
            sent += Sent(source, ids, testScheduler.currentTime)
            plan(source, ids) ?: FetchResult.Success(repository.remote.build(ids))
        }
        return RefreshMarketDataUseCase(
            repository,
            rulesRepository,
            MoodCalculator(RegimeCalculator(), ValuationCalculator()),
            pacing,
            SchedulerClock(start, testScheduler),
        )
    }

    private fun failure(error: DataFetchError) = FetchResult.Failure(error)

    // 출처마다(Twelve Data는 묶음마다) 답을 정한다. firstBatch는 SPY가 든 묶음, secondBatch는 그 밖의 묶음이다.
    private fun answer(firstBatch: FetchResult? = null, secondBatch: FetchResult? = null, fred: FetchResult? = null) {
        plan = { source, ids ->
            when {
                source == FRED -> fred
                "SPY" in ids -> firstBatch
                else -> secondBatch
            }
        }
    }

    private fun savedValues() = repository.saveCalls.flatMap { it.values.entries }.associate { it.toPair() }

    private fun twelveDataSent() = sent.filter { it.source == TWELVE_DATA }

    // R-103 묶음 나누기, 기다림

    @Test
    fun `R-103 묶음 둘이면 0초에 FRED와 첫 묶음, 65초에 둘째 묶음을 보낸다`() = runTest {
        val useCase = useCase(twoBatches)
        val result = async { useCase() }

        advanceTimeBy(64_001)
        assertFalse(result.isCompleted)

        val outcome = result.await()

        assertEquals(RefreshOutcome.Success, outcome)
        assertEquals(
            listOf(
                Sent(FRED, fredIds, 0),
                Sent(TWELVE_DATA, listOf("SPY", "QQQ"), 0),
                Sent(TWELVE_DATA, listOf("SMH", "XLK"), 65_000),
            ).sortedBy { it.at * 10 + it.source.ordinal },
            sent.sortedBy { it.at * 10 + it.source.ordinal },
        )
        assertEquals(fredIds.toSet(), sent.single { it.source == FRED }.ids.toSet())
        assertEquals(
            listOf(Instant.parse("2026-11-24T01:00:00Z"), Instant.parse("2026-11-24T01:01:05Z")),
            repository.savedStamps(RefreshStamp.TWELVE_DATA_REQUESTED),
        )
    }

    @Test
    fun `R-103 기본 규칙과 기본 나눠 받기면 일곱씩 두 묶음이고 둘째는 65초에 보낸다`() = runTest {
        rulesRepository.rules.value = DefaultMoodRules.rules
        useCase()()

        assertEquals(
            listOf(
                listOf("SPY", "QQQ", "SMH", "XLK", "XLF", "XLE", "XLV") to 0L,
                listOf("XLY", "XLP", "XLI", "XLB", "XLU", "XLRE", "XLC") to 65_000L,
            ),
            twelveDataSent().map { it.ids to it.at },
        )
    }

    @Test
    fun `R-103 Twelve Data에 마지막으로 보낸 때가 30초 전이면 첫 묶음은 35초, 둘째는 100초`() = runTest {
        repository.stamps[RefreshStamp.TWELVE_DATA_REQUESTED] = Instant.parse("2026-11-24T00:59:30Z")
        useCase(twoBatches)()

        assertEquals(0L, sent.single { it.source == FRED }.at)
        assertEquals(listOf(35_000L, 100_000L), twelveDataSent().map { it.at })
    }

    @Test
    fun `R-103 마지막으로 보낸 때가 65초보다 앞이면 첫 묶음은 0초`() = runTest {
        repository.stamps[RefreshStamp.TWELVE_DATA_REQUESTED] = Instant.parse("2026-11-24T00:58:00Z")
        useCase(twoBatches)()
        assertEquals(0L, twelveDataSent().first().at)
    }

    @Test
    fun `R-103 마지막으로 보낸 때가 지금보다 뒤면 첫 묶음은 0초`() = runTest {
        repository.stamps[RefreshStamp.TWELVE_DATA_REQUESTED] = Instant.parse("2026-11-24T01:05:00Z")
        useCase(twoBatches)()
        assertEquals(0L, twelveDataSent().first().at)
    }

    @Test
    fun `R-103 마지막으로 보낸 때를 읽지 못하면 첫 묶음은 0초`() = runTest {
        repository.stamps[RefreshStamp.TWELVE_DATA_REQUESTED] = Instant.parse("2026-11-24T00:59:30Z")
        repository.failOnReadStamp += RefreshStamp.TWELVE_DATA_REQUESTED
        useCase(twoBatches)()
        assertEquals(0L, twelveDataSent().first().at)
    }

    @Test
    fun `R-103 첫 묶음의 응답이 9초 걸려도 둘째 묶음은 보낸 때로부터 65초에 보낸다`() = runTest {
        plan = { source, ids ->
            if (source == TWELVE_DATA && "SPY" in ids) delay(9_000)
            null
        }
        useCase(twoBatches)()
        assertEquals(listOf(0L, 65_000L), twelveDataSent().map { it.at })
    }

    @Test
    fun `R-103 시험용 규칙과 기본 나눠 받기면 Twelve Data는 한 번이다`() = runTest {
        useCase()()
        assertEquals(listOf(listOf("SPY", "QQQ", "SMH", "XLK")), twelveDataSent().map { it.ids })
    }

    // R-41 시리즈마다의 시작 날짜

    @Test
    fun `R-41 시리즈마다 그 받는 기간만큼 앞의 미국 동부 날짜부터 받는다`() = runTest {
        useCase()()

        val calls = repository.fetchCalls.sortedBy { it.source.ordinal }
        assertEquals(listOf(TWELVE_DATA, FRED), calls.map { it.source })
        assertEquals(
            listOf("SPY", "QQQ", "SMH", "XLK").map { it to date("2017-11-23") },
            calls[0].requests,
        )
        assertEquals(
            mapOf(
                YIELD_CURVE to date("2024-11-23"),
                HIGH_YIELD to date("2024-11-23"),
                "DFII10" to date("2020-11-23"),
                "DGS10" to date("2024-11-23"),
                "DFF" to date("2024-11-23"),
                "CPIAUCSL" to date("2024-11-23"),
            ),
            calls[1].requests.toMap(),
        )
        assertEquals(6, calls[1].requests.size)
    }

    @Test
    fun `R-41 데이터 A를 받으면 시리즈 열 개를 저장하고 CPIAUCSL의 값 24개가 모두 저장된다`() = runTest {
        useCase()()

        val saved = savedValues()
        assertEquals(10, saved.size)
        assertEquals((STOCKS + fredIds).toSet(), saved.keys)
        assertEquals(24, saved.getValue("CPIAUCSL").size)
    }

    // R-23, R-54 묶음마다 저장

    @Test
    fun `R-23 받아 둔 값이 바뀌면 판정을 다시 계산해 내보낸다`() = runTest {
        repository.setStored(dataA(), null)
        val observe = ObserveMoodUseCase(
            rulesRepository, repository, MoodCalculator(RegimeCalculator(), ValuationCalculator()), UnconfinedTestDispatcher(testScheduler),
        )
        val received = mutableListOf<MoodAssessment?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe().toList(received) }

        repository.setStored(dataB(), null)

        assertEquals(listOf(0.766667, 0.15), received.map { it!!.compositeScore })
    }

    @Test
    fun `R-54 묶음 둘이면 save는 셋이고 성공한 때는 둘째 묶음의 save에만 실린다`() = runTest {
        val useCase = useCase(twoBatches)
        val result = async { useCase() }

        advanceTimeBy(30_000)
        // 둘째 묶음을 기다리는 동안 받아 둔 값에는 SPY, QQQ와 FRED의 새 값이 이미 있다.
        assertEquals((listOf("SPY", "QQQ") + fredIds).toSet(), repository.stored.value.values.keys)
        assertEquals(2, repository.saveCalls.size)
        assertEquals(
            setOf(setOf("SPY", "QQQ"), fredIds.toSet()),
            repository.saveCalls.map { it.values.keys }.toSet(),
        )
        assertTrue(repository.saveCalls.all { it.succeededAt == null })

        result.await()

        assertEquals(3, repository.saveCalls.size)
        val last = repository.saveCalls.last()
        assertEquals(setOf("SMH", "XLK"), last.values.keys)
        assertEquals(startedAt, last.succeededAt)
    }

    @Test
    fun `R-54 묶음 하나면 save는 둘이고 성공한 때는 나중에 끝난 쪽에만 실린다`() = runTest {
        val outcome = useCase()()

        assertEquals(RefreshOutcome.Success, outcome)
        assertEquals(2, repository.saveCalls.size)
        assertEquals(listOf(null, startedAt), repository.saveCalls.map { it.succeededAt })
        assertEquals((STOCKS + fredIds).toSet(), savedValues().keys)
    }

    // R-108 전체 시장의 값을 받음

    @Test
    fun `R-108 FRED와 첫 묶음의 저장이 끝난 뒤, 둘째 묶음을 보내기 전에 한 번 부른다`() = runTest {
        val calls = mutableListOf<Long>()
        val useCase = useCase(twoBatches)
        useCase { calls += currentTime; repository.callOrder += "notify" }

        assertEquals(listOf(0L), calls)
        val order = repository.callOrder.filter { it == "save" || it == "notify" || it == "fetch" }
        // FRED와 첫 묶음의 fetch, save 둘(차례는 상관없음) 뒤에 notify, 그 뒤에 둘째 묶음의 fetch와 save다.
        val notify = order.indexOf("notify")
        assertEquals(listOf("fetch", "fetch", "save", "save"), order.take(notify).sorted())
        assertEquals(listOf("fetch", "save"), order.drop(notify + 1))
    }

    @Test
    fun `R-108 첫 묶음이 한도 넘음이면 부르지 않는다`() = runTest {
        answer(firstBatch = failure(DataFetchError.RATE_LIMITED))
        var called = 0
        useCase(twoBatches).invoke { called++ }
        assertEquals(0, called)
    }

    @Test
    fun `R-108 FRED가 연결 안 됨이면 부르지 않는다`() = runTest {
        answer(fred = failure(DataFetchError.NO_CONNECTION))
        var called = 0
        useCase(twoBatches).invoke { called++ }
        assertEquals(0, called)
    }

    @Test
    fun `R-108 첫 묶음의 저장이 IOException이면 부르지 않는다`() = runTest {
        repository.failOnSaveIf = { "SPY" in it }
        var called = 0
        useCase(twoBatches).invoke { called++ }
        assertEquals(0, called)
    }

    @Test
    fun `R-108 둘째 묶음만 실패하면 0초에 부르고 결과는 그 실패다`() = runTest {
        answer(secondBatch = failure(DataFetchError.NO_CONNECTION))
        val calls = mutableListOf<Long>()
        val outcome = useCase(twoBatches).invoke { calls += currentTime }
        assertEquals(listOf(0L), calls)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), outcome)
    }

    // R-43 일부 묶음만 실패, 키 거절

    @Test
    fun `R-43 첫 묶음이 한도 넘음이어도 둘째 묶음과 FRED는 저장하고 성공한 때는 적지 않는다`() = runTest {
        answer(firstBatch = failure(DataFetchError.RATE_LIMITED))

        val outcome = useCase(twoBatches)()

        assertEquals(65_000L, twelveDataSent().last().at)
        assertEquals((listOf("SMH", "XLK") + fredIds).toSet(), savedValues().keys)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.RATE_LIMITED), outcome)
        assertTrue(repository.saveCalls.all { it.succeededAt == null })
    }

    @Test
    fun `R-43 첫 묶음이 키 거절이면 둘째 묶음은 보내지 않고 0초에 끝난다`() = runTest {
        answer(firstBatch = failure(DataFetchError.KEY_REJECTED))

        val outcome = useCase(twoBatches)()

        assertEquals(2, repository.fetchCalls.size)
        assertEquals(fredIds.toSet(), savedValues().keys)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.OTHER), outcome)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `R-43 첫 묶음이 틀린 키여도 둘째 묶음은 보내지 않고 0초에 끝난다`() = runTest {
        answer(firstBatch = failure(DataFetchError.KEY_INVALID))

        val outcome = useCase(twoBatches)()

        assertEquals(2, repository.fetchCalls.size)
        assertEquals(fredIds.toSet(), savedValues().keys)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.OTHER), outcome)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `R-43 둘째 묶음이 연결 안 됨이면 첫 묶음과 FRED는 저장되고 NO_CONNECTION`() = runTest {
        answer(secondBatch = failure(DataFetchError.NO_CONNECTION))
        val outcome = useCase(twoBatches)()
        assertEquals((listOf("SPY", "QQQ") + fredIds).toSet(), savedValues().keys)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), outcome)
    }

    @Test
    fun `R-43 성공한 출처의 시리즈만 성공한 때 없이 저장한다`() = runTest {
        repository.setStored(dataA(), null)
        repository.remote = dataB()
        answer(firstBatch = failure(DataFetchError.NO_CONNECTION))

        val outcome = useCase()()

        val save = repository.saveCalls.single()
        assertEquals(dataB().build(fredIds), save.values)
        assertNull(save.succeededAt)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), outcome)
    }

    // R-45 원인의 우선순위

    @Test
    fun `R-45 첫 묶음 연결 안 됨, 둘째 묶음 한도 넘음이면 RATE_LIMITED`() = runTest {
        answer(firstBatch = failure(DataFetchError.NO_CONNECTION), secondBatch = failure(DataFetchError.RATE_LIMITED))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.RATE_LIMITED), useCase(twoBatches)())
    }

    @Test
    fun `R-45 FRED 읽을 수 없음, 둘째 묶음 연결 안 됨이면 NO_CONNECTION`() = runTest {
        answer(fred = failure(DataFetchError.OTHER), secondBatch = failure(DataFetchError.NO_CONNECTION))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), useCase(twoBatches)())
    }

    @Test
    fun `R-45 한도 넘음이 연결 안 됨보다 먼저다`() = runTest {
        answer(firstBatch = failure(DataFetchError.NO_CONNECTION), fred = failure(DataFetchError.RATE_LIMITED))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.RATE_LIMITED), useCase()())
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun `R-45 연결 안 됨이 그 밖의 실패보다 먼저다`() = runTest {
        answer(firstBatch = failure(DataFetchError.OTHER), fred = failure(DataFetchError.NO_CONNECTION))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), useCase()())
        assertTrue(repository.saveCalls.isEmpty())
    }

    // R-42 10초 제한은 요청마다

    @Test
    fun `R-42 둘째 묶음이 11초 걸리면 NO_CONNECTION이고 첫 묶음과 FRED는 저장된다`() = runTest {
        plan = { source, ids ->
            if (source == TWELVE_DATA && "SMH" in ids) delay(11_000)
            null
        }
        val outcome = useCase(twoBatches)()
        assertEquals(75_000L, currentTime)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), outcome)
        assertEquals((listOf("SPY", "QQQ") + fredIds).toSet(), savedValues().keys)
    }

    @Test
    fun `R-42 둘째 묶음이 9_5초면 Success, 기다린 65초는 제한에 들지 않는다`() = runTest {
        plan = { source, ids ->
            if (source == TWELVE_DATA && "SMH" in ids) delay(9_500)
            null
        }
        assertEquals(RefreshOutcome.Success, useCase(twoBatches)())
    }

    @Test
    fun `R-42 FRED가 11초면 FRED만 NO_CONNECTION이고 묶음 둘은 저장된다`() = runTest {
        plan = { source, _ ->
            if (source == FRED) delay(11_000)
            null
        }
        val outcome = useCase(twoBatches)()
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), outcome)
        assertEquals(STOCKS.toSet(), savedValues().keys)
    }

    @Test
    fun `R-42 10초를 넘긴 출처는 연결 안 됨이고 다른 출처의 값은 저장된다`() = runTest {
        plan = { source, _ ->
            if (source == TWELVE_DATA) delay(11_000)
            null
        }

        val outcome = useCase()()

        assertEquals(10_000L, currentTime)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), outcome)
        assertEquals(fredIds.toSet(), repository.saveCalls.single().values.keys)
    }

    @Test
    fun `R-42 9_5초 안에 오면 Success`() = runTest {
        plan = { source, _ ->
            if (source == TWELVE_DATA) delay(9_500)
            null
        }
        assertEquals(RefreshOutcome.Success, useCase()())
    }

    // R-42 넷으로 가르기

    @Test
    fun `R-42 저장이 IOException을 던지면 OTHER`() = runTest {
        repository.failOnSave = true
        assertEquals(RefreshOutcome.Failure(RefreshFailure.OTHER), useCase()())
    }

    @Test
    fun `R-42 모두 성공했는데 SPY 종가가 모자라 판정을 낼 수 없으면 OTHER이고 성공한 때 없이 저장한다`() = runTest {
        val keep = MoodTestData.weekdaysEndingAt(MoodTestData.END, 69).toSet()
        repository.remote = dataA().keepOnly("SPY") { it in keep }

        val outcome = useCase()()

        assertEquals(RefreshOutcome.Failure(RefreshFailure.OTHER), outcome)
        assertEquals(repository.remote.build(), savedValues())
        assertTrue(repository.saveCalls.all { it.succeededAt == null })
    }

    @Test
    fun `R-42 출처가 DFII10 없이 돌려주면 OTHER이고 받은 값은 저장되며 성공한 때는 적지 않는다`() = runTest {
        repository.remote = dataA().removeSeries(MoodTestData.REAL_YIELD)

        val outcome = useCase()()

        assertEquals(RefreshOutcome.Failure(RefreshFailure.OTHER), outcome)
        assertEquals(repository.remote.build(), savedValues())
        assertTrue(repository.saveCalls.all { it.succeededAt == null })
    }

    @Test
    fun `R-42 한 출처가 한도 넘음이면 RATE_LIMITED`() = runTest {
        answer(firstBatch = failure(DataFetchError.RATE_LIMITED))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.RATE_LIMITED), useCase()())
    }

    @Test
    fun `R-42 키 거절은 OTHER`() = runTest {
        answer(firstBatch = failure(DataFetchError.KEY_REJECTED))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.OTHER), useCase()())
    }

    // R-37 장이 끝나지 않은 날의 값 거르기

    @Test
    fun `R-37 묶음마다 받은 때로 거른다`() = runTest {
        start = Instant.parse("2026-11-23T20:59:00Z")

        useCase(twoBatches)()

        val saved = savedValues()
        listOf("SPY", "QQQ").forEach { assertEquals(it, date("2026-11-20"), saved.getValue(it).last().date) }
        // 둘째 묶음은 65초 뒤(미국 동부 오후 4시 0분 5초)에 받는다.
        listOf("SMH", "XLK").forEach { assertEquals(it, date("2026-11-23"), saved.getValue(it).last().date) }
        listOf(YIELD_CURVE, HIGH_YIELD, "DFII10", "DGS10", "DFF").forEach {
            assertEquals(it, date("2026-11-23"), saved.getValue(it).last().date)
        }
        assertEquals(date("2026-10-01"), saved.getValue("CPIAUCSL").last().date)
    }

    @Test
    fun `R-37 미국 동부 오후 3시 59분에는 그날의 종가를 뺀다`() = runTest {
        start = Instant.parse("2026-11-23T20:59:00Z")

        useCase()()

        val saved = savedValues()
        STOCKS.forEach { id -> assertEquals(id, date("2026-11-20"), saved.getValue(id).last().date) }
        assertEquals(date("2026-11-23"), saved.getValue(YIELD_CURVE).last().date)
    }

    @Test
    fun `R-37 R-72 미국 동부 오후 3시 59분에도 매매 판단의 날마다의 값과 달마다의 값은 거르지 않는다`() = runTest {
        start = Instant.parse("2026-11-23T20:59:00Z")

        useCase()()

        val saved = savedValues()
        STOCKS.forEach { id -> assertEquals(id, date("2026-11-20"), saved.getValue(id).last().date) }
        listOf("DFII10", "DGS10", "DFF").forEach { id -> assertEquals(id, date("2026-11-23"), saved.getValue(id).last().date) }
        assertEquals(date("2026-10-01"), saved.getValue("CPIAUCSL").last().date)
        assertEquals(24, saved.getValue("CPIAUCSL").size)
    }

    @Test
    fun `R-37 미국 동부 오후 4시 정각에는 그날의 종가를 빼지 않는다`() = runTest {
        start = Instant.parse("2026-11-23T21:00:00Z")

        useCase()()

        val saved = savedValues()
        STOCKS.forEach { id -> assertEquals(id, date("2026-11-23"), saved.getValue(id).last().date) }
    }

    // R-62 시작한 때

    @Test
    fun `R-62 받기를 시작하면 첫 fetch보다 먼저 받는 때를 한 번 적는다`() = runTest {
        val outcome = useCase()()

        assertEquals(RefreshOutcome.Success, outcome)
        assertEquals(listOf(startedAt), repository.savedStamps(RefreshStamp.STARTED))
        assertEquals("saveStamp(STARTED)", repository.callOrder.first())
        assertEquals(1, repository.callOrder.count { it == "saveStamp(STARTED)" })
    }

    @Test
    fun `R-62 받기가 실패해도 적힌 시작한 때가 남는다`() = runTest {
        answer(firstBatch = failure(DataFetchError.NO_CONNECTION), fred = failure(DataFetchError.NO_CONNECTION))

        val outcome = useCase()()

        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), outcome)
        assertEquals(startedAt, repository.getStamp(RefreshStamp.STARTED))
    }

    @Test
    fun `R-62 시작한 때를 적지 못해도 받기를 이어 가고 Success다`() = runTest {
        repository.failOnSaveStamp += RefreshStamp.STARTED

        val outcome = useCase()()

        assertEquals(listOf(TWELVE_DATA, FRED), repository.fetchCalls.map { it.source }.sortedBy { it.ordinal })
        assertEquals(RefreshOutcome.Success, outcome)
        assertEquals((STOCKS + fredIds).toSet(), savedValues().keys)
        assertEquals(startedAt, repository.saveCalls.last().succeededAt)
    }

    // R-62, P-44 끝난 때

    @Test
    fun `R-62 끝난 때는 받기가 끝난 때로 한 번 적고 마지막으로 불린 것이다`() = runTest {
        plan = { source, _ ->
            if (source == FRED) delay(3_000)
            null
        }
        val outcome = useCase()()

        assertEquals(RefreshOutcome.Success, outcome)
        assertEquals(listOf(startedAt.plusSeconds(3)), repository.savedStamps(RefreshStamp.FINISHED))
        assertEquals("saveStamp(FINISHED)", repository.callOrder.last())
        assertEquals(listOf(startedAt), repository.savedStamps(RefreshStamp.STARTED))
    }

    @Test
    fun `R-62 모두 한도 넘음이어도 끝난 때를 적는다`() = runTest {
        answer(firstBatch = failure(DataFetchError.RATE_LIMITED), fred = failure(DataFetchError.RATE_LIMITED))
        useCase()()
        assertEquals(1, repository.savedStamps(RefreshStamp.FINISHED).size)
    }

    @Test
    fun `R-62 fetch가 예외를 던져도 끝난 때를 적는다`() = runTest {
        plan = { _, _ -> throw IllegalStateException("받기 실패") }
        useCase()()
        assertEquals(1, repository.savedStamps(RefreshStamp.FINISHED).size)
    }

    @Test
    fun `R-62 끝난 때를 적지 못해도 결과는 Success`() = runTest {
        repository.failOnSaveStamp += RefreshStamp.FINISHED
        assertEquals(RefreshOutcome.Success, useCase()())
    }

    @Test
    fun `R-62 묶음 둘이면 끝난 때는 65초의 둘째 묶음이 끝난 뒤다`() = runTest {
        useCase(twoBatches)()
        assertEquals(listOf(startedAt.plusSeconds(65)), repository.savedStamps(RefreshStamp.FINISHED))
        assertEquals("saveStamp(FINISHED)", repository.callOrder.last())
    }
}
