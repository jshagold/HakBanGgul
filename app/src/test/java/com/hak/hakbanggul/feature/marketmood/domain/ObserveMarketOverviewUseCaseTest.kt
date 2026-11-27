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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun TestScope.collect(useCase: ObserveMarketOverviewUseCase): MutableList<MarketOverviewState> {
        val received = mutableListOf<MarketOverviewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase().toList(received) }
        return received
    }

    // 받은 값 가운데 Ready의 한 벌만
    private fun List<MarketOverviewState>.readies(): List<MarketOverview?> =
        filterIsInstance<MarketOverviewState.Ready>().map { it.overview }

    // R-04, P-60 계산 중

    @Test
    fun `R-04 디스패처를 돌리기 전에는 시험용 규칙의 Calculating 하나이고 돌리면 Ready가 이어 나온다`() = runTest {
        repository.setStored(dataA(), null)
        val received = collect(useCase(StandardTestDispatcher(testScheduler)))

        assertEquals(listOf<MarketOverviewState>(MarketOverviewState.Calculating(MoodTestData.RULES)), received)

        runCurrent()

        assertEquals(2, received.size)
        assertEquals(0.766667, (received[1] as MarketOverviewState.Ready).overview!!.mood.compositeScore, 0.0)
    }

    @Test
    fun `R-112 Ready를 받은 뒤 값이 바뀌어 다시 계산하는 동안 새 값이 나오지 않는다`() = runTest {
        repository.setStored(dataA(), null)
        val received = collect(useCase(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertEquals(2, received.size)

        repository.setStored(dataB(), null)

        assertEquals(2, received.size)

        runCurrent()

        assertEquals(3, received.size)
        assertEquals(0.15, (received.last() as MarketOverviewState.Ready).overview!!.mood.compositeScore, 0.0)
    }

    @Test
    fun `R-04 Ready를 받은 뒤 멈췄다가 다시 모으면 첫 값은 들고 있던 Ready이고 Calculating은 나오지 않는다`() = runTest {
        repository.setStored(dataA(), null)
        val useCase = useCase(StandardTestDispatcher(testScheduler))
        val firstJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase().toList(mutableListOf()) }
        runCurrent()
        firstJob.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, repository.stored.subscriptionCount.value)

        val received = collect(useCase)

        assertEquals(1, received.size)
        assertTrue(received.single() is MarketOverviewState.Ready)
        assertEquals(0.766667, received.readies().single()!!.mood.compositeScore, 0.0)
    }

    @Test
    fun `R-04 받아 둔 값이 없으면 Calculating 다음 Ready(null)`() = runTest {
        val received = collect(useCase(StandardTestDispatcher(testScheduler)))
        runCurrent()

        assertEquals(2, received.size)
        assertTrue(received[0] is MarketOverviewState.Calculating)
        assertNull((received[1] as MarketOverviewState.Ready).overview)
    }

    // R-23 계산용 디스패처에서, 마지막 값만

    @Test
    fun `R-23 디스패처를 멈춘 채 B1, B2로 바꾸면 B2의 한 벌만 내보낸다`() = runTest {
        repository.setStored(dataA(), null)
        val received = collect(useCase(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertEquals(1, received.readies().size)

        repository.setStored(dataA().set("SPY", "2026-11-23", 99.0), null)
        repository.setStored(dataB(), null)
        runCurrent()

        // B1의 현재 상태는 0.3, 데이터 B는 0.15다.
        assertEquals(listOf(0.766667, 0.15), received.readies().map { it!!.mood.compositeScore })
    }

    @Test
    fun `R-23 같은 값을 다시 저장하면(받은 때만 바뀜) 새 한 벌을 내보내지 않는다`() = runTest {
        repository.setStored(dataA(), null)
        val received = collect(useCase(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(1, received.readies().size)

        repository.save(dataA().build(), Instant.parse("2026-11-24T01:00:00Z"))
        runCurrent()

        assertEquals(1, received.readies().size)
    }

    // R-99, P-40 한 번만 계산

    @Test
    fun `R-99 받는 쪽 둘이 같은 한 벌을 받고 받아 둔 값은 한 번만 구독한다`() = runTest {
        repository.setStored(dataA(), null)
        val useCase = useCase(UnconfinedTestDispatcher(testScheduler))
        val first = collect(useCase)
        val second = collect(useCase)
        runCurrent()

        assertEquals(1, first.readies().size)
        assertSame(first.readies().single(), second.readies().single())
        assertEquals(1, repository.stored.subscriptionCount.value)

        repository.setStored(dataB(), null)
        runCurrent()

        assertEquals(0.15, first.readies().last()!!.mood.compositeScore, 0.0)
        assertSame(first.readies().last(), second.readies().last())
        assertEquals(2, second.readies().size)
    }
}
