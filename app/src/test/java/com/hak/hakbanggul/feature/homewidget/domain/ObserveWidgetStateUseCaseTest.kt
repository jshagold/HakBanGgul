package com.hak.hakbanggul.feature.homewidget.domain

import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.core.network.ApiKeyAvailability.AVAILABLE
import com.hak.hakbanggul.core.network.ApiKeyAvailability.INVALID
import com.hak.hakbanggul.core.network.ApiKeyAvailability.MISSING
import com.hak.hakbanggul.feature.homewidget.domain.WidgetContent.KeyGuide
import com.hak.hakbanggul.feature.homewidget.domain.WidgetContent.NotReceived
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshButton.DISABLED
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshButton.ENABLED
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshButton.IN_PROGRESS
import com.hak.hakbanggul.feature.homewidget.domain.WidgetTestFixture.Companion.ASSESSMENT_A
import com.hak.hakbanggul.feature.homewidget.domain.WidgetTestFixture.Companion.BUFFER_RULES
import com.hak.hakbanggul.feature.homewidget.domain.WidgetTestFixture.Companion.MIDDLE_50
import com.hak.hakbanggul.feature.homewidget.domain.WidgetTestFixture.Companion.bufferData
import com.hak.hakbanggul.feature.marketmood.domain.MoodCalculator
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMoodUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RegimeCalculator
import com.hak.hakbanggul.feature.marketmood.domain.ValuationCalculator
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
import kotlinx.coroutines.flow.first
import com.hak.hakbanggul.feature.marketmood.domain.FetchResult
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.RefreshCondition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveWidgetStateUseCaseTest {

    private fun TestScope.fixture() = WidgetTestFixture(this)

    private fun TestScope.collect(f: WidgetTestFixture): List<WidgetState> {
        val states = mutableListOf<WidgetState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.observeWidgetState()().toList(states) }
        return states
    }

    private fun TestScope.firstWith(f: WidgetTestFixture): WidgetState {
        f.start()
        val states = collect(f)
        runCurrent()
        return states.first()
    }

    private fun List<WidgetState>.contents() = map { it.content }

    // R-05, R-07, R-31, R-33, R-37 표에서 고르기와 버튼

    @Test
    fun `R-05 기본 상황이면 판정과 누를 수 있는 버튼`() = runTest {
        assertEquals(WidgetState(ASSESSMENT_A, ENABLED), firstWith(fixture()))
    }

    @Test
    fun `R-07 FRED의 키가 없으면 키 안내와 비활성 버튼`() = runTest {
        val f = fixture().apply { setKeys(AVAILABLE, MISSING) }
        assertEquals(WidgetState(KeyGuide, DISABLED), firstWith(f))
    }

    @Test
    fun `R-33 Twelve Data의 키가 틀림이면 키 안내`() = runTest {
        val f = fixture().apply { setKeys(INVALID, AVAILABLE) }
        assertEquals(WidgetState(KeyGuide, DISABLED), firstWith(f))
    }

    @Test
    fun `R-07 두 키가 없고 받아 둔 값도 없으면 키 안내`() = runTest {
        val f = fixture().apply { setKeys(MISSING, MISSING); setStoredNone() }
        assertEquals(WidgetState(KeyGuide, DISABLED), firstWith(f))
    }

    @Test
    fun `R-31 받아 둔 값이 없으면 받지 못함`() = runTest {
        val f = fixture().apply { setStoredNone() }
        assertEquals(WidgetState(NotReceived, ENABLED), firstWith(f))
    }

    @Test
    fun `R-31 SPY가 빠져 판정을 낼 수 없으면 받지 못함`() = runTest {
        val f = fixture()
        f.repository.setStored(dataA().removeSeries("SPY"), f.now.minusSeconds(3600))
        assertEquals(WidgetState(NotReceived, ENABLED), firstWith(f))
    }

    // R-16 처음 값은 기다리지 않음

    @Test
    fun `R-16 처음 값이 받지 못함이면 기다리지 않고 나온다`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        val states = collect(f)
        runCurrent()
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(listOf(NotReceived), states.contents())
    }

    @Test
    fun `R-16 받는 중이어도 처음 값은 표대로다`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        val wait = f.holdFetch()
        f.refresher.refresh()
        runCurrent()

        val states = collect(f)
        runCurrent()

        assertEquals(NotReceived, states.first().content)
        wait.complete(Unit)
        advanceUntilIdle()
    }

    // R-25 표시 단계와 기준 날짜

    @Test
    fun `R-25 하루만 나쁜 날이면 표시 단계 BULL`() = runTest {
        val f = fixture()
        f.repository.setStored(dataA().badDay("2026-11-23"), f.now.minusSeconds(3600))
        assertEquals(ASSESSMENT_A, firstWith(f).content)
    }

    // R-17, R-30, R-36 판정이 바뀌면 상태가 바뀜

    @Test
    fun `R-17 받아 둔 값이 생기면 판정이 이어서 나온다`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        val states = collect(f)
        runCurrent()

        f.repository.setStored(dataA(), f.now.minusSeconds(3600))
        runCurrent()

        assertEquals(listOf(NotReceived, ASSESSMENT_A), states.contents())
    }

    @Test
    fun `R-30 받아 둔 값의 표시 단계가 바뀌면 이어서 나온다`() = runTest {
        val f = fixture().apply { start() }
        val states = collect(f)
        runCurrent()

        f.repository.setStored(dataA().badDay("2026-11-19", "2026-11-20", "2026-11-23"), f.now.minusSeconds(3600))
        runCurrent()

        assertEquals(
            listOf(ASSESSMENT_A, WidgetContent.Assessment(MarketStage.NEUTRAL, LocalDate.parse("2026-11-23"), MIDDLE_50, MIDDLE_50)),
            states.contents(),
        )
    }

    // R-18, R-28, R-36 키의 상태가 바뀌면 상태가 바뀜

    @Test
    fun `R-18 FRED의 키를 지웠다가 다시 넣으면 키 안내였다가 판정이다`() = runTest {
        val f = fixture().apply { start() }
        val states = collect(f)
        runCurrent()

        f.keys.set(FRED, MISSING)
        advanceUntilIdle()
        f.keys.set(FRED, AVAILABLE)
        advanceUntilIdle()

        assertEquals(
            listOf(WidgetState(ASSESSMENT_A, ENABLED), WidgetState(KeyGuide, DISABLED), WidgetState(ASSESSMENT_A, ENABLED)),
            states,
        )
        // 키가 돌아온 뒤 받기 관리자가 받기를 시작했다.
        assertEquals(2, f.fetchCount)
    }

    @Test
    fun `R-28 Twelve Data의 키가 틀림이 되면 키 안내`() = runTest {
        val f = fixture().apply { start() }
        val states = collect(f)
        runCurrent()

        f.keys.set(TWELVE_DATA, INVALID)
        runCurrent()

        assertEquals(WidgetState(KeyGuide, DISABLED), states.last())
    }

    // R-19 값이 없는데 받는 중이면 그대로 두기

    private fun TestScope.keyGuideThenKeysSaved(answer: FetchResult?): Triple<WidgetTestFixture, List<WidgetState>, kotlinx.coroutines.CompletableDeferred<Unit>> {
        val f = fixture().apply { setStoredNone(); setKeys(AVAILABLE, MISSING); start() }
        val states = collect(f)
        runCurrent()
        assertEquals(listOf(KeyGuide), states.contents())
        val wait = f.holdFetch(answer)

        f.keys.set(FRED, AVAILABLE)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        return Triple(f, states, wait)
    }

    @Test
    fun `R-19 키를 넣고 받는 동안 키 안내 그대로이고 받지 못함이 스치지 않는다`() = runTest {
        val (f, states, wait) = keyGuideThenKeysSaved(null)
        assertTrue(f.refresher.state.value.isRefreshing)
        assertEquals(listOf(KeyGuide), states.contents().distinct())

        wait.complete(Unit)
        advanceUntilIdle()

        assertEquals(ASSESSMENT_A, states.last().content)
        assertFalse(NotReceived in states.contents())
    }

    @Test
    fun `R-19 키를 넣고 받기가 실패하면 0_5초 뒤에 받지 못함`() = runTest {
        val (_, states, wait) = keyGuideThenKeysSaved(FetchResult.Failure(DataFetchError.NO_CONNECTION))

        wait.complete(Unit)
        runCurrent()
        advanceTimeBy(499)
        runCurrent()
        assertEquals(KeyGuide, states.last().content)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(NotReceived, states.last().content)
    }

    @Test
    fun `R-19 받는 중에 키를 지우면 받지 못함 그대로였다가 받기가 끝나면 키 안내`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        val states = collect(f)
        runCurrent()
        val wait = f.holdFetch()
        val request = async { f.requestRefresh(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        runCurrent()

        f.keys.set(FRED, MISSING)
        runCurrent()
        assertEquals(listOf(NotReceived), states.contents().distinct())

        wait.complete(Unit)
        advanceUntilIdle()
        request.await()
        assertEquals(KeyGuide, states.last().content)
    }

    // R-19 받지 못함으로 바뀌는 것을 미루기

    @Test
    fun `R-19 받아 둔 값이 없어지면 0_5초 뒤에 받지 못함`() = runTest {
        val f = fixture().apply { start() }
        val states = collect(f)
        runCurrent()

        f.setStoredNone()
        runCurrent()
        advanceTimeBy(499)
        runCurrent()
        assertEquals(ASSESSMENT_A, states.last().content)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(NotReceived, states.last().content)
    }

    @Test
    fun `R-19 0_5초 안에 판정이 돌아오면 받지 못함이 나오지 않는다`() = runTest {
        val f = fixture().apply { start() }
        val states = collect(f)
        runCurrent()

        f.setStoredNone()
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        f.repository.setStored(dataA(), f.now.minusSeconds(3600))
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        assertFalse(NotReceived in states.contents())
        assertEquals(ASSESSMENT_A, states.last().content)
    }

    // R-27 실패해도 상태가 바뀌지 않음

    @Test
    fun `R-27 받기가 실패해도 판정 그대로다`() = runTest {
        val f = fixture().apply { answerAlways(FetchResult.Failure(DataFetchError.NO_CONNECTION)); start() }
        val states = collect(f)
        runCurrent()

        val request = async { f.requestRefresh(RefreshCondition.WHEN_KEYS_AVAILABLE) }
        advanceUntilIdle()
        request.await()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(ASSESSMENT_A), states.contents().distinct())
    }

    // R-29 판정을 읽다 예외가 나면 값이 없는 것으로

    @Test
    fun `R-29 판정의 Flow가 예외를 던지면 받지 못함`() = runTest {
        val f = fixture().apply { widgetMoodRules = ThrowingRulesRepository() }
        assertEquals(WidgetState(NotReceived, ENABLED), firstWith(f))
    }

    @Test
    fun `R-29 판정이 예외로 끝난 뒤에도 키의 변화에 반응한다`() = runTest {
        val f = fixture().apply { widgetMoodRules = ThrowingRulesRepository(); start() }
        val states = collect(f)
        runCurrent()

        f.keys.set(FRED, MISSING)
        runCurrent()

        assertEquals(listOf(NotReceived, KeyGuide), states.contents())
    }

    // R-39, R-40 버튼의 상태

    private fun TestScope.launchManual(f: WidgetTestFixture): Job {
        val caller = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        return caller.launch { f.coordinator.request(WidgetRefreshTrigger.MANUAL) }
    }

    @Test
    fun `R-39 눌러 받는 동안 진행 표시이고 끝나면 누를 수 있다`() = runTest {
        val f = fixture().apply { start() }
        val states = collect(f)
        runCurrent()
        val wait = f.holdFetch()

        launchManual(f)
        runCurrent()
        assertEquals(WidgetState(ASSESSMENT_A, IN_PROGRESS), states.last())

        wait.complete(Unit)
        advanceUntilIdle()
        assertEquals(WidgetState(ASSESSMENT_A, ENABLED), states.last())
    }

    @Test
    fun `R-39 받지 못함에서 눌러 받으면 진행 표시였다가 판정이다`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        val states = collect(f)
        runCurrent()
        val wait = f.holdFetch()

        launchManual(f)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(WidgetState(NotReceived, IN_PROGRESS), states.last())

        wait.complete(Unit)
        advanceUntilIdle()
        assertEquals(WidgetState(ASSESSMENT_A, ENABLED), states.last())
    }

    @Test
    fun `R-40 받지 않으면 진행 표시가 1초 뒤 버튼으로 돌아오고 판정 그대로다`() = runTest {
        val f = fixture()
        f.repository.lastStartedAt = f.now.minusSeconds(30)
        f.start()
        val states = collect(f)
        runCurrent()

        launchManual(f)
        runCurrent()
        assertEquals(IN_PROGRESS, states.last().refreshButton)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(
            listOf(ENABLED, IN_PROGRESS, ENABLED),
            states.map { it.refreshButton },
        )
        assertEquals(listOf(ASSESSMENT_A), states.contents().distinct())
        assertEquals(0, f.fetchCount)
    }

    // 매매 판단 (위젯 버전업-001)

    private val bufferAssessment = WidgetContent.Assessment(
        MarketStage.BULL,
        LocalDate.parse("2026-11-23"),
        MIDDLE_50,
        WidgetValuation(35, ValuationStage.LOW),
    )

    private fun WidgetTestFixture.useBuffer() {
        rulesRepository.rules.value = BUFFER_RULES
        repository.setStored(bufferData(), now.minusSeconds(3600))
    }

    @Test
    fun `R-31 기본 상황이면 장세, 기준 날짜, 매매 판단 둘의 점수와 표시 단계를 옮긴다`() = runTest {
        assertEquals(
            WidgetState(
                WidgetContent.Assessment(MarketStage.BULL, LocalDate.parse("2026-11-23"), MIDDLE_50, MIDDLE_50),
                ENABLED,
            ),
            firstWith(fixture()),
        )
    }

    @Test
    fun `R-31 완충 데이터면 금리 부담만 35 LOW이고 주가 위치와 바뀌어 실리지 않는다`() = runTest {
        val f = fixture().apply { useBuffer() }
        assertEquals(bufferAssessment, firstWith(f).content)
    }

    @Test
    fun `R-46 점수로 정한 단계가 아니라 표시 단계를 싣는다`() = runTest {
        val f = fixture().apply { useBuffer() }
        assertEquals(WidgetValuation(35, ValuationStage.LOW), (firstWith(f).content as WidgetContent.Assessment).rateBurden)

        // 같은 판정의 점수로 정한 단계는 MIDDLE이다.
        val mood = ObserveMoodUseCase(
            f.rulesRepository,
            f.repository,
            MoodCalculator(RegimeCalculator(), ValuationCalculator()),
            UnconfinedTestDispatcher(testScheduler),
        )().first()!!
        assertEquals(ValuationStage.MIDDLE, mood.valuation.rateBurden.scoredStage)
        assertEquals(ValuationStage.LOW, mood.valuation.rateBurden.displayedStage)
    }

    @Test
    fun `R-17 R-30 매매 판단만 바뀌어도 새 상태가 이어서 나온다`() = runTest {
        val f = fixture().apply {
            rulesRepository.rules.value = BUFFER_RULES
            start()
        }
        val states = collect(f)
        runCurrent()

        f.repository.setStored(bufferData(), f.now.minusSeconds(3600))
        runCurrent()

        assertEquals(listOf(ASSESSMENT_A, bufferAssessment), states.contents())
    }

    @Test
    fun `R-31 실질금리가 통째로 빠지면 장세의 값이 있어도 받지 못함`() = runTest {
        val f = fixture()
        f.repository.setStored(dataA().removeSeries(MoodTestData.REAL_YIELD), f.now.minusSeconds(3600))
        assertEquals(WidgetState(NotReceived, ENABLED), firstWith(f))
    }
}
