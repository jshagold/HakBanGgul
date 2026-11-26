package com.hak.hakbanggul.feature.homewidget.presentation

import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.network.ApiKeyAvailability.AVAILABLE
import com.hak.hakbanggul.core.network.ApiKeyAvailability.MISSING
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.homewidget.domain.CountingApiKeyProvider
import com.hak.hakbanggul.feature.homewidget.domain.WidgetContent
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshButton.DISABLED
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshButton.ENABLED
import com.hak.hakbanggul.feature.homewidget.domain.WidgetState
import com.hak.hakbanggul.feature.homewidget.domain.WidgetTestFixture
import com.hak.hakbanggul.feature.homewidget.domain.WidgetTestFixture.Companion.BUFFER_RULES
import com.hak.hakbanggul.feature.homewidget.domain.WidgetTestFixture.Companion.MIDDLE_50
import com.hak.hakbanggul.feature.homewidget.domain.WidgetTestFixture.Companion.bufferData
import com.hak.hakbanggul.feature.homewidget.domain.WidgetValuation
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeWidgetStateHolderTest {

    private fun TestScope.fixture() = WidgetTestFixture(this)

    // 받기 관리자를 만든 뒤 진짜 ObserveWidgetStateUseCase로 HomeWidgetStateHolder를 만든다. 범위는 테스트의 것이다.
    private fun TestScope.holder(f: WidgetTestFixture): HomeWidgetStateHolder {
        f.start()
        val scope: CoroutineScope = backgroundScope
        val holder = HomeWidgetStateHolder(f.observeWidgetState(), CoroutineScope(scope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        runCurrent()
        return holder
    }

    private fun TestScope.collect(holder: HomeWidgetStateHolder): List<HomeWidgetUiState> {
        val states = mutableListOf<HomeWidgetUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.filterNotNull().toList(states) }
        return states
    }

    private fun asOf(month: Int, day: Int) = UiText(R.string.widget_as_of, listOf(month, day))

    private fun line(stage: ValuationStage, nameRes: Int, shortRes: Int, score: Int, stageNameRes: Int) = WidgetValuationLine(
        stage = stage,
        name = UiText(nameRes),
        shortName = UiText(shortRes),
        scoreText = UiText(R.string.valuation_score, listOf(score, UiText(stageNameRes))),
        stageName = UiText(stageNameRes),
    )

    private fun priceLine(stage: ValuationStage, score: Int, stageNameRes: Int) =
        line(stage, R.string.price_position_name, R.string.widget_price_position_short, score, stageNameRes)

    private fun rateLine(stage: ValuationStage, score: Int, stageNameRes: Int) =
        line(stage, R.string.rate_burden_name, R.string.widget_rate_burden_short, score, stageNameRes)

    // 데이터 A의 두 줄: 둘 다 50 · 보통
    private val priceA = priceLine(ValuationStage.MIDDLE, 50, R.string.valuation_middle)
    private val rateA = rateLine(ValuationStage.MIDDLE, 50, R.string.valuation_middle)

    private fun stage(stage: MarketStage, nameRes: Int, month: Int = 11, day: Int = 23) =
        WidgetMain.Stage(stage, UiText(nameRes), asOf(month, day), priceA, rateA)

    private val keyGuide = HomeWidgetUiState(
        UiText(R.string.app_name),
        WidgetMain.Message(UiText(R.string.key_guide_title), UiText(R.string.widget_open_app)),
        DISABLED,
    )

    private fun assessment(stage: MarketStage, date: String) =
        WidgetState(WidgetContent.Assessment(stage, LocalDate.parse(date), MIDDLE_50, MIDDLE_50), ENABLED)

    private fun valuations(stage: MarketStage, price: WidgetValuation, rate: WidgetValuation) =
        WidgetState(WidgetContent.Assessment(stage, LocalDate.parse("2026-11-23"), price, rate), ENABLED).toUiState().main as WidgetMain.Stage

    // R-06 첫 줄, 단계 이름, 날짜 줄의 글 / R-11 마지막 줄

    @Test
    fun `R-06 기본 상황이면 지금 장은, 불장, 11월 23일 종가`() = runTest {
        val h = holder(fixture())
        assertEquals(
            HomeWidgetUiState(UiText(R.string.mood_now), stage(MarketStage.BULL, R.string.stage_bull), ENABLED),
            h.state.value,
        )
    }

    @Test
    fun `R-06 단계마다 단계 이름과 단계`() {
        assertEquals(stage(MarketStage.NEUTRAL, R.string.stage_neutral), assessment(MarketStage.NEUTRAL, "2026-11-23").toUiState().main)
        assertEquals(stage(MarketStage.WEAK, R.string.stage_weak), assessment(MarketStage.WEAK, "2026-11-23").toUiState().main)
        assertEquals(stage(MarketStage.BEAR, R.string.stage_bear), assessment(MarketStage.BEAR, "2026-11-23").toUiState().main)
    }

    // R-07 / R-11

    @Test
    fun `R-07 FRED의 키가 없으면 학반꿀, 키를 넣어 주세요, 눌러서 앱 열기, 비활성`() = runTest {
        val f = fixture().apply { setKeys(AVAILABLE, MISSING) }
        assertEquals(keyGuide, holder(f).state.value)
    }

    // R-08 / R-11

    @Test
    fun `R-08 받아 둔 값이 없으면 학반꿀, 데이터를 받지 못했습니다, 눌러서 앱 열기`() = runTest {
        val f = fixture().apply { setStoredNone() }
        assertEquals(
            HomeWidgetUiState(
                UiText(R.string.app_name),
                WidgetMain.Message(UiText(R.string.mood_failed_title), UiText(R.string.widget_open_app)),
                ENABLED,
            ),
            holder(f).state.value,
        )
    }

    // R-26

    @Test
    fun `R-26 1월 5일은 해 없이 1, 5`() {
        val main = assessment(MarketStage.BULL, "2026-01-05").toUiState().main as WidgetMain.Stage
        assertEquals(listOf<Any>(1, 5), main.asOfText.args)
    }

    @Test
    fun `R-26 12월 31일은 12, 31`() {
        val main = assessment(MarketStage.BULL, "2026-12-31").toUiState().main as WidgetMain.Stage
        assertEquals(listOf<Any>(12, 31), main.asOfText.args)
    }

    // R-03 모두 같은 값

    @Test
    fun `R-03 두 곳이 같은 값을 받고 UseCase는 한 번만 모아진다`() = runTest {
        val f = fixture()
        val counting = CountingApiKeyProvider(f.keys)
        f.widgetKeyProvider = counting
        val h = holder(f)
        val first = collect(h)
        val second = collect(h)

        f.repository.setStored(dataA().badDay("2026-11-19", "2026-11-20", "2026-11-23"), f.now.minusSeconds(3600))
        runCurrent()

        assertEquals(first, second)
        assertEquals(stage(MarketStage.NEUTRAL, R.string.stage_neutral), first.last().main)
        // 한 번 모을 때 출처마다 한 번씩 키의 상태를 모은다.
        assertEquals(2, counting.observeCalls)
    }

    // R-16 그대로 두는 동안 새로 모으는 곳도 같은 것을 받음

    @Test
    fun `R-16 그대로 두는 동안 새로 모은 곳도 키 안내를 받고 받기가 끝나면 둘 다 판정`() = runTest {
        val f = fixture().apply { setStoredNone(); setKeys(AVAILABLE, MISSING) }
        val h = holder(f)
        val first = collect(h)
        assertEquals(keyGuide, first.last())
        val wait = f.holdFetch()

        f.keys.set(FRED, AVAILABLE)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        val second = collect(h)

        assertEquals(UiText(R.string.app_name), second.first().title)
        assertEquals(UiText(R.string.key_guide_title), (second.first().main as WidgetMain.Message).text)

        wait.complete(Unit)
        advanceUntilIdle()
        assertEquals(stage(MarketStage.BULL, R.string.stage_bull), first.last().main)
        assertEquals(stage(MarketStage.BULL, R.string.stage_bull), second.last().main)
    }

    // R-30

    @Test
    fun `R-30 받아 둔 값을 데이터 A로 바꾸면 불장으로 바뀐다`() = runTest {
        val f = fixture().apply { setStoredNone() }
        val h = holder(f)

        f.repository.setStored(dataA(), f.now.minusSeconds(3600))
        runCurrent()

        assertEquals(stage(MarketStage.BULL, R.string.stage_bull), h.state.value!!.main)
    }

    // 매매 판단 두 줄 (위젯 버전업-001)

    // R-06, R-44, R-45 두 줄의 순서와 글

    @Test
    fun `R-06 R-44 R-45 데이터 A면 두 줄이 주가 위치, 금리 부담 순이고 둘 다 50 보통`() = runTest {
        val main = holder(fixture()).state.value!!.main
        assertEquals(
            WidgetMain.Stage(MarketStage.BULL, UiText(R.string.stage_bull), asOf(11, 23), priceA, rateA),
            main,
        )
    }

    @Test
    fun `R-44 R-45 HIGH 74, 98`() {
        val main = valuations(MarketStage.BULL, WidgetValuation(74, ValuationStage.HIGH), WidgetValuation(98, ValuationStage.HIGH))
        assertEquals(priceLine(ValuationStage.HIGH, 74, R.string.valuation_price_high), main.pricePosition)
        assertEquals(rateLine(ValuationStage.HIGH, 98, R.string.valuation_rate_high), main.rateBurden)
    }

    @Test
    fun `R-44 R-45 LOW 12, 20`() {
        val main = valuations(MarketStage.BEAR, WidgetValuation(12, ValuationStage.LOW), WidgetValuation(20, ValuationStage.LOW))
        assertEquals(priceLine(ValuationStage.LOW, 12, R.string.valuation_price_low), main.pricePosition)
        assertEquals(rateLine(ValuationStage.LOW, 20, R.string.valuation_rate_low), main.rateBurden)
    }

    @Test
    fun `R-44 R-45 MIDDLE 58, 52`() {
        val main = valuations(MarketStage.NEUTRAL, WidgetValuation(58, ValuationStage.MIDDLE), WidgetValuation(52, ValuationStage.MIDDLE))
        assertEquals(priceLine(ValuationStage.MIDDLE, 58, R.string.valuation_middle), main.pricePosition)
        assertEquals(rateLine(ValuationStage.MIDDLE, 52, R.string.valuation_middle), main.rateBurden)
    }

    // R-46 점수와 표시 단계가 다른 날의 글

    private fun UiText.resIds(): List<Int> = listOf(resId) + args.filterIsInstance<UiText>().flatMap { it.resIds() }

    @Test
    fun `R-46 점수와 표시 단계가 달라도 그대로 적고 안내 줄은 없다`() {
        val state = WidgetState(
            WidgetContent.Assessment(
                MarketStage.BULL,
                LocalDate.parse("2026-11-23"),
                WidgetValuation(33, ValuationStage.LOW),
                WidgetValuation(65, ValuationStage.HIGH),
            ),
            ENABLED,
        ).toUiState()
        val main = state.main as WidgetMain.Stage
        assertEquals(priceLine(ValuationStage.LOW, 33, R.string.valuation_price_low), main.pricePosition)
        assertEquals(rateLine(ValuationStage.HIGH, 65, R.string.valuation_rate_high), main.rateBurden)

        val texts = listOf(state.title, main.name, main.asOfText) +
            listOf(main.pricePosition, main.rateBurden).flatMap { listOf(it.name, it.shortName, it.scoreText, it.stageName) }
        val ids = texts.flatMap { it.resIds() }
        assertFalse(R.string.valuation_notice_low in ids)
        assertFalse(R.string.valuation_notice_high in ids)
    }

    @Test
    fun `R-46 완충 데이터면 금리 부담은 35 낮음, LOW`() = runTest {
        val f = fixture().apply {
            rulesRepository.rules.value = BUFFER_RULES
            repository.setStored(bufferData(), now.minusSeconds(3600))
        }
        val main = holder(f).state.value!!.main as WidgetMain.Stage
        assertEquals(rateLine(ValuationStage.LOW, 35, R.string.valuation_rate_low), main.rateBurden)
    }

    // R-30 받아 둔 값을 바꾸면 매매 판단도 따라 바뀜

    @Test
    fun `R-30 받아 둔 값을 완충 데이터로 바꾸면 금리 부담이 35 낮음으로 바뀐다`() = runTest {
        val f = fixture().apply { rulesRepository.rules.value = BUFFER_RULES }
        val h = holder(f)
        assertEquals(rateA, (h.state.value!!.main as WidgetMain.Stage).rateBurden)

        f.repository.setStored(bufferData(), f.now.minusSeconds(3600))
        runCurrent()

        val main = h.state.value!!.main as WidgetMain.Stage
        assertEquals(rateLine(ValuationStage.LOW, 35, R.string.valuation_rate_low), main.rateBurden)
        assertEquals(UiText(R.string.stage_bull), main.name)
        assertEquals(asOf(11, 23), main.asOfText)
    }
}
