package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataB
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMarketOverviewUseCaseTest {

    private val repository = FakeMarketDataRepository()
    private val rulesRepository = FakeMoodRulesRepository()
    private val calculator = MarketOverviewCalculator(
        MoodCalculator(RegimeCalculator(), ValuationCalculator()),
        RegimeCalculator(),
        ValuationCalculator(),
    )

    // 계산용 디스패처를 넘겨 만든다. 앱 범위는 이 테스트가 끝날 때 함께 끝나는 범위다.
    private fun TestScope.useCase(dispatcher: CoroutineDispatcher): ObserveMarketOverviewUseCase {
        val appScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        return ObserveMarketOverviewUseCase(rulesRepository, repository, calculator, dispatcher, appScope)
    }

    private fun TestScope.collect(useCase: ObserveMarketOverviewUseCase): MutableList<MarketOverview?> {
        val received = mutableListOf<MarketOverview?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase().toList(received) }
        return received
    }

    // R-23 계산용 디스패처에서, 마지막 값만

    @Test
    fun `R-23 넘긴 디스패처를 돌리기 전에는 값이 없고 돌리면 한 벌이 나온다`() = runTest {
        repository.setStored(dataA(), null)
        val received = collect(useCase(StandardTestDispatcher(testScheduler)))

        assertTrue(received.isEmpty())

        runCurrent()

        assertEquals(1, received.size)
        assertEquals(0.766667, received.single()!!.mood.compositeScore, 0.0)
    }

    @Test
    fun `R-23 디스패처를 멈춘 채 B1, B2로 바꾸면 B2의 한 벌만 내보낸다`() = runTest {
        repository.setStored(dataA(), null)
        val received = collect(useCase(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertEquals(1, received.size)

        repository.setStored(dataA().set("SPY", "2026-11-23", 99.0), null)
        repository.setStored(dataB(), null)
        runCurrent()

        // B1의 현재 상태는 0.3, 데이터 B는 0.15다.
        assertEquals(listOf(0.766667, 0.15), received.map { it!!.mood.compositeScore })
    }

    @Test
    fun `R-23 성공한 때만 바뀌면 새 한 벌을 내보내지 않는다`() = runTest {
        repository.setStored(dataA(), null)
        val received = collect(useCase(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(1, received.size)

        repository.setStored(dataA(), Instant.parse("2026-11-24T01:00:00Z"))
        runCurrent()

        assertEquals(1, received.size)
    }

    // R-99, P-40 한 번만 계산

    @Test
    fun `R-99 받는 쪽 둘이 같은 한 벌을 받고 받아 둔 값은 한 번만 구독한다`() = runTest {
        repository.setStored(dataA(), null)
        val useCase = useCase(UnconfinedTestDispatcher(testScheduler))
        val first = collect(useCase)
        val second = collect(useCase)
        runCurrent()

        assertEquals(1, first.size)
        assertSame(first.single(), second.single())
        assertEquals(1, repository.stored.subscriptionCount.value)

        repository.setStored(dataB(), null)
        runCurrent()

        assertEquals(0.15, first.last()!!.mood.compositeScore, 0.0)
        assertSame(first.last(), second.last())
        assertEquals(2, second.size)
    }
}
