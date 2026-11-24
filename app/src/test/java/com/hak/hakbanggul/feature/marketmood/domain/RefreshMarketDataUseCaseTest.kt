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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshMarketDataUseCaseTest {

    private val repository = FakeMarketDataRepository()
    private val rulesRepository = FakeMoodRulesRepository()
    private val clock = FakeClock(Instant.parse("2026-11-24T01:00:00Z"))
    private val useCase = RefreshMarketDataUseCase(repository, rulesRepository, MoodCalculator(), clock)

    private val fredIds = listOf(YIELD_CURVE, HIGH_YIELD)

    private fun answer(twelveData: FetchResult? = null, fred: FetchResult? = null) {
        val remote = repository.remote
        repository.fetchAnswer = { source, series, _ ->
            val fromRemote = FetchResult.Success(remote.build(series.map { it.seriesId }))
            when (source) {
                TWELVE_DATA -> twelveData ?: fromRemote
                FRED -> fred ?: fromRemote
            }
        }
    }

    private fun failure(error: DataFetchError) = FetchResult.Failure(error)

    // R-23

    @Test
    fun `R-23 받아 둔 값이 바뀌면 판정을 다시 계산해 내보낸다`() = runTest {
        repository.setStored(dataA(), null)
        val observe = ObserveMoodUseCase(rulesRepository, repository, MoodCalculator())
        val received = mutableListOf<MoodAssessment?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe().toList(received) }

        repository.setStored(dataB(), null)

        assertEquals(listOf(1.0, 0.4125), received.map { it!!.compositeScore })
    }

    // R-41

    @Test
    fun `R-41 출처마다 한 번씩 미국 동부 날짜의 2년 전부터 받는다`() = runTest {
        useCase()

        assertEquals(
            listOf(
                FakeMarketDataRepository.FetchCall(TWELVE_DATA, STOCKS, date("2024-11-23")),
                FakeMarketDataRepository.FetchCall(FRED, fredIds, date("2024-11-23")),
            ),
            repository.fetchCalls.sortedBy { it.source.ordinal },
        )
    }

    // R-37 장이 끝나지 않은 날의 값 거르기

    @Test
    fun `R-37 미국 동부 오후 3시 59분에는 그날의 종가를 뺀다`() = runTest {
        clock.now = Instant.parse("2026-11-23T20:59:00Z")

        useCase()

        val saved = repository.saveCalls.single().values
        STOCKS.forEach { id ->
            assertEquals(id, date("2026-11-20"), saved.getValue(id).last().date)
        }
        assertEquals(date("2026-11-23"), saved.getValue(YIELD_CURVE).last().date)
    }

    @Test
    fun `R-37 미국 동부 오후 4시 정각에는 그날의 종가를 빼지 않는다`() = runTest {
        clock.now = Instant.parse("2026-11-23T21:00:00Z")

        useCase()

        val saved = repository.saveCalls.single().values
        STOCKS.forEach { id ->
            assertEquals(id, date("2026-11-23"), saved.getValue(id).last().date)
        }
    }

    // R-42 10초 제한

    @Test
    fun `R-42 10초를 넘긴 출처는 연결 안 됨이고 다른 출처의 값은 저장된다`() = runTest {
        val remote = repository.remote
        repository.fetchAnswer = { source, series, _ ->
            if (source == TWELVE_DATA) delay(11_000)
            FetchResult.Success(remote.build(series.map { it.seriesId }))
        }

        val outcome = useCase()

        assertEquals(10_000L, currentTime)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), outcome)
        assertEquals(fredIds.toSet(), repository.saveCalls.single().values.keys)
    }

    @Test
    fun `R-42 9_5초 안에 오면 Success`() = runTest {
        val remote = repository.remote
        repository.fetchAnswer = { source, series, _ ->
            if (source == TWELVE_DATA) delay(9_500)
            FetchResult.Success(remote.build(series.map { it.seriesId }))
        }

        assertEquals(RefreshOutcome.Success, useCase())
    }

    // R-42 넷으로 가르기

    @Test
    fun `R-42 저장이 IOException을 던지면 OTHER`() = runTest {
        repository.failOnSave = true
        assertEquals(RefreshOutcome.Failure(RefreshFailure.OTHER), useCase())
    }

    @Test
    fun `R-42 모두 성공했는데 판정을 낼 수 없으면 OTHER이고 성공한 때 없이 저장한다`() = runTest {
        repository.remote = dataA().keepOnly("XLK") { it == date("2026-11-23") }

        val outcome = useCase()

        assertEquals(RefreshOutcome.Failure(RefreshFailure.OTHER), outcome)
        val save = repository.saveCalls.single()
        assertEquals(repository.remote.build(), save.values)
        assertNull(save.succeededAt)
    }

    @Test
    fun `R-42 한 출처가 한도 넘음이면 RATE_LIMITED`() = runTest {
        answer(twelveData = failure(DataFetchError.RATE_LIMITED))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.RATE_LIMITED), useCase())
    }

    @Test
    fun `R-42 키 거절은 OTHER`() = runTest {
        answer(twelveData = failure(DataFetchError.KEY_REJECTED))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.OTHER), useCase())
    }

    @Test
    fun `R-42 모두 성공하면 Success이고 시리즈 여섯과 받는 때로 한 번 저장한다`() = runTest {
        val outcome = useCase()

        assertEquals(RefreshOutcome.Success, outcome)
        val save = repository.saveCalls.single()
        assertEquals((STOCKS + fredIds).toSet(), save.values.keys)
        assertEquals(Instant.parse("2026-11-24T01:00:00Z"), save.succeededAt)
    }

    // R-43

    @Test
    fun `R-43 성공한 출처의 시리즈만 성공한 때 없이 저장한다`() = runTest {
        repository.setStored(dataA(), null)
        repository.remote = dataB()
        answer(twelveData = failure(DataFetchError.NO_CONNECTION))

        val outcome = useCase()

        val save = repository.saveCalls.single()
        assertEquals(dataB().build(fredIds), save.values)
        assertNull(save.succeededAt)
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), outcome)
    }

    // R-45 원인의 우선순위

    @Test
    fun `R-45 한도 넘음이 연결 안 됨보다 먼저다`() = runTest {
        answer(twelveData = failure(DataFetchError.NO_CONNECTION), fred = failure(DataFetchError.RATE_LIMITED))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.RATE_LIMITED), useCase())
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun `R-45 연결 안 됨이 그 밖의 실패보다 먼저다`() = runTest {
        answer(twelveData = failure(DataFetchError.OTHER), fred = failure(DataFetchError.NO_CONNECTION))
        assertEquals(RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION), useCase())
        assertTrue(repository.saveCalls.isEmpty())
    }
}
