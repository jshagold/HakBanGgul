package com.hak.hakbanggul.feature.marketmood.presentation

import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.MarketOverview
import com.hak.hakbanggul.feature.marketmood.domain.MarketOverviewCalculator
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.MoodCalculator
import com.hak.hakbanggul.feature.marketmood.domain.MoodRules
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.HIGH_YIELD
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.YIELD_CURVE
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.closesForScores
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataB
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.valuesForScores
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.withRegimeWeights
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.withSingleWindow
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.withWindow
import com.hak.hakbanggul.feature.marketmood.domain.PeriodLength
import com.hak.hakbanggul.feature.marketmood.domain.PeriodUnit
import com.hak.hakbanggul.feature.marketmood.domain.RegimeCalculator
import com.hak.hakbanggul.feature.marketmood.domain.ValuationCalculator
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 한 벌은 시험용 규칙과 받아 둔 값으로 MarketOverviewCalculator가 낸 것이다. 기준 날짜는 2026-11-23이다.
class MoodUiMapperTest {

    private val mapper = MoodUiMapper()
    private val calculator = MarketOverviewCalculator(
        MoodCalculator(RegimeCalculator(), ValuationCalculator()),
        RegimeCalculator(),
        ValuationCalculator(),
    )
    private val rules = MoodTestData.RULES

    private fun overview(builder: MoodTestData.Builder, rules: MoodRules = this.rules): MarketOverview =
        requireNotNull(calculator.assess(rules, builder.build())) { "한 벌을 낼 수 있어야 한다" }

    private fun main(builder: MoodTestData.Builder, rules: MoodRules = this.rules, refreshing: Boolean = false) =
        mapper.mainScreen(overview(builder, rules), refreshing)

    private fun regime(builder: MoodTestData.Builder, id: String = "broad_market", rules: MoodRules = this.rules) =
        requireNotNull(mapper.regimeDetail(overview(builder, rules), id))

    private fun price(builder: MoodTestData.Builder, id: String = "broad_market", rules: MoodRules = this.rules) =
        requireNotNull(mapper.pricePositionDetail(overview(builder, rules), id))

    private fun rate(builder: MoodTestData.Builder, rules: MoodRules = this.rules) =
        requireNotNull(mapper.rateBurdenDetail(overview(builder, rules)))

    private fun months(n: Int) = UiText(R.string.period_months, listOf(n))
    private fun years(n: Int) = UiText(R.string.period_years, listOf(n))
    private fun stage(res: Int) = UiText(res)
    private val broadRegime = UiText(R.string.regime_broad_market)
    private fun sectorRegime(nameRes: Int) = UiText(R.string.regime_sector, listOf(UiText(nameRes)))
    private fun withSymbol(nameRes: Int, symbol: String) = UiText(R.string.target_with_symbol, listOf(UiText(nameRes), symbol))
    private fun scoreText(score: Int, nameRes: Int) = UiText(R.string.valuation_score, listOf(score, UiText(nameRes)))
    private fun withDate(value: String, month: Int, day: Int) = UiText(R.string.evidence_value_with_date, listOf(value, month, day))
    private fun pending(computed: Int, displayed: Int, streak: Int) =
        UiText(R.string.mood_pending_notice, listOf(UiText(computed), UiText(displayed), 3, streak))

    private fun assertSegments(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-6f) }
    }

    // R-06 장세 카드

    @Test
    fun `R-06 데이터 A의 전체 장세 카드`() {
        val card = main(dataA()).regime
        assertEquals(UiText(R.string.regime_card_title, listOf(broadRegime, months(3))), card.title)
        assertEquals(UiText(R.string.mood_as_of, listOf(11, 23)), card.asOfText)
        assertEquals(MarketStage.BULL, card.stage)
        assertEquals(stage(R.string.stage_bull), card.stageName)
        assertEquals("+0.77", card.compositeScoreText)
        assertEquals(ScoreTone.POSITIVE, card.compositeTone)
        assertEquals(stage(R.string.stage_bull), card.computedStageName)
        assertEquals(0.8833335f, card.gaugePosition, 1e-6f)
        assertNull(card.pendingNotice)
        assertTrue(card.showLink)
    }

    @Test
    fun `R-06 섹터 화면의 장세 카드는 섹터의 장세 이름이고 링크가 있다, 장세 화면의 메인 카드는 링크가 없다`() {
        val sector = requireNotNull(mapper.sector(overview(dataA()), "nasdaq_100"))
        assertEquals(UiText(R.string.regime_card_title, listOf(sectorRegime(R.string.target_nasdaq_100), months(3))), sector.regime.title)
        assertTrue(sector.regime.showLink)
        assertFalse(regime(dataA()).main.showLink)
    }

    @Test
    fun `R-06 위 경계가 0_6이면 눈금 위치도 따라 바뀐다`() {
        val changed = rules.copy(regime = rules.regime.copy(upperBoundary = 0.6))
        assertEquals(0.5625f, main(dataB(), changed).regime.gaugePosition, 1e-6f)
    }

    @Test
    fun `R-06 현재 상태 -0_5의 눈금 위치`() {
        val builder = dataA().set("SPY", "2026-11-23", 93.0).set(YIELD_CURVE, "2026-11-23", 0.29).set(HIGH_YIELD, "2026-11-23", 5.00)
        assertEquals(0.25f, main(builder, rules.withRegimeWeights(0.75, 0.25)).regime.gaugePosition, 1e-6f)
    }

    // R-08

    @Test
    fun `R-08 데이터 B의 메인 카드`() {
        val card = main(dataB()).regime
        assertEquals("+0.15", card.compositeScoreText)
        assertEquals(stage(R.string.stage_neutral), card.computedStageName)
        assertEquals(0.575f, card.gaugePosition, 1e-6f)
        assertEquals(pending(R.string.stage_neutral, R.string.stage_bull, 1), card.pendingNotice)
    }

    @Test
    fun `R-08 데이터 B의 XLK 장세 화면의 다른 기간 카드`() {
        val others = regime(dataB(), "technology").otherPeriods
        val year = others[1] as OtherPeriodCardUiState.Regime
        assertEquals(stage(R.string.stage_neutral), year.stageName)
        assertEquals("-0.08", year.compositeScoreText)
        assertEquals(ScoreTone.NEGATIVE, year.compositeTone)
        assertEquals(pending(R.string.stage_weak, R.string.stage_neutral, 1), year.pendingNotice)
        val half = others[0] as OtherPeriodCardUiState.Regime
        assertEquals(stage(R.string.stage_bull), half.stageName)
        assertNull(half.pendingNotice)
    }

    // R-09, R-10 묶음과 항목 줄

    @Test
    fun `R-09 R-10 데이터 A의 전체 시장 장세 화면의 묶음과 항목`() {
        val groups = regime(dataA()).groups
        assertEquals(2, groups.size)
        val stocks = groups[0]
        assertEquals(UiText(R.string.group_stocks), stocks.name)
        assertEquals(UiText(R.string.stock_group_suffix, listOf(withSymbol(R.string.target_broad_market, "SPY"), 70)), stocks.weightText)
        assertEquals("+0.67", stocks.scoreText)
        assertEquals(ScoreTone.POSITIVE, stocks.scoreTone)
        assertEquals(
            listOf(
                IndicatorUiState(UiText(R.string.indicator_above_average), null, UiText(R.string.evidence_above_average, listOf(50, "0.0%")), "0.0", ScoreTone.ZERO),
                IndicatorUiState(UiText(R.string.indicator_from_high), null, UiText(R.string.evidence_from_high, listOf(months(3), "0.0%")), "+1.0", ScoreTone.POSITIVE),
                IndicatorUiState(UiText(R.string.indicator_average_slope), null, UiText(R.string.evidence_average_slope, listOf(50, 20, "0.0%")), "+1.0", ScoreTone.POSITIVE),
            ),
            stocks.indicators,
        )
        val bonds = groups[1]
        assertEquals(UiText(R.string.group_bond_signals), bonds.name)
        assertEquals(UiText(R.string.group_weight, listOf(30)), bonds.weightText)
        assertEquals("+1.00", bonds.scoreText)
        assertEquals(UiText(R.string.indicator_yield_curve), bonds.indicators[0].name)
        assertEquals(UiText(R.string.indicator_yield_curve_suffix), bonds.indicators[0].suffix)
        assertEquals(UiText(R.string.evidence_value, listOf("+0.60%p")), bonds.indicators[0].evidence)
        assertEquals(UiText(R.string.indicator_high_yield_spread), bonds.indicators[1].name)
        assertEquals(UiText(R.string.evidence_value, listOf("3.00%")), bonds.indicators[1].evidence)
    }

    @Test
    fun `R-10 R-12 데이터 B의 전체 시장 장세 화면`() {
        val groups = regime(dataB()).groups
        val stocks = groups[0]
        assertEquals("0.00", stocks.scoreText)
        assertEquals(ScoreTone.ZERO, stocks.scoreTone)
        assertEquals(
            listOf(
                UiText(R.string.evidence_above_average, listOf(50, "-1.0%")),
                UiText(R.string.evidence_from_high, listOf(months(3), "-1.0%")),
                UiText(R.string.evidence_average_slope, listOf(50, 20, "0.0%")),
            ),
            stocks.indicators.map { it.evidence },
        )
        assertEquals(listOf("0.0", "+1.0", "-1.0"), stocks.indicators.map { it.scoreText })
        val bonds = groups[1]
        assertEquals("+0.50", bonds.scoreText)
        assertEquals(UiText(R.string.evidence_value, listOf("+0.29%p")), bonds.indicators[0].evidence)
        assertEquals("0.0", bonds.indicators[0].scoreText)
        assertEquals(withDate("3.76%", 11, 20), bonds.indicators[1].evidence)
        assertEquals("+1.0", bonds.indicators[1].scoreText)
    }

    @Test
    fun `R-10 데이터 B의 SMH 장세 화면`() {
        val stocks = regime(dataB(), "semiconductors").groups[0]
        assertEquals(UiText(R.string.stock_group_suffix, listOf(withSymbol(R.string.target_semiconductors, "SMH"), 70)), stocks.weightText)
        assertEquals(
            listOf(
                UiText(R.string.evidence_above_average, listOf(50, "-19.7%")),
                UiText(R.string.evidence_from_high, listOf(months(3), "-20.0%")),
                UiText(R.string.evidence_average_slope, listOf(50, 20, "-0.4%")),
            ),
            stocks.indicators.map { it.evidence },
        )
        assertEquals("-1.00", stocks.scoreText)
        assertEquals(ScoreTone.NEGATIVE, stocks.scoreTone)
    }

    // R-88 장세의 다른 기간 카드

    @Test
    fun `R-88 데이터 A의 전체 시장 장세 화면의 다른 기간 카드 둘`() {
        val others = regime(dataA()).otherPeriods.map { it as OtherPeriodCardUiState.Regime }
        assertEquals(2, others.size)
        val half = others[0]
        assertEquals(broadRegime, half.name)
        assertEquals(UiText(R.string.regime_period_label, listOf(months(6))), half.periodText)
        assertEquals("+0.77", half.compositeScoreText)
        assertEquals(ScoreTone.POSITIVE, half.compositeTone)
        assertEquals(stage(R.string.stage_bull), half.stageName)
        assertEquals(0.8833335f, half.gaugePosition, 1e-6f)
        assertEquals(
            listOf(
                UiText(R.string.regime_period_evidence, listOf(100, "0.0%", months(6), "0.0%")),
                UiText(R.string.evidence_average_slope, listOf(100, 40, "0.0%")),
            ),
            half.evidence,
        )
        val year = others[1]
        assertEquals(UiText(R.string.regime_period_label, listOf(years(1))), year.periodText)
        assertEquals(
            listOf(
                UiText(R.string.regime_period_evidence, listOf(200, "0.0%", years(1), "0.0%")),
                UiText(R.string.evidence_average_slope, listOf(200, 80, "0.0%")),
            ),
            year.evidence,
        )
    }

    @Test
    fun `R-88 데이터 B의 XLK 1년 카드 근거, QQQ의 카드 이름`() {
        val year = regime(dataB(), "technology").otherPeriods[1] as OtherPeriodCardUiState.Regime
        assertEquals(
            listOf(
                UiText(R.string.regime_period_evidence, listOf(200, "0.0%", years(1), "-16.7%")),
                UiText(R.string.evidence_average_slope, listOf(200, 80, "-0.1%")),
            ),
            year.evidence,
        )
        val qqq = regime(dataA(), "nasdaq_100").otherPeriods
        assertTrue(qqq.all { it.name == sectorRegime(R.string.target_nasdaq_100) })
    }

    // R-63, R-64 매매 판단 카드

    @Test
    fun `R-63 R-64 데이터 A의 첫 화면 두 카드와 섹터 화면의 주가 위치 카드`() {
        val mood = main(dataA())
        assertEquals(UiText(R.string.price_position_name), mood.pricePosition.name)
        assertEquals(withSymbol(R.string.target_broad_market, "SPY"), mood.pricePosition.subtitle)
        assertEquals(scoreText(50, R.string.valuation_middle), mood.pricePosition.scoreText)
        assertEquals(emptyList<UiText>(), mood.pricePosition.evidence)
        assertTrue(mood.pricePosition.showLink)
        assertEquals(UiText(R.string.rate_burden_name), mood.rateBurden.name)
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf("2.00%")), mood.rateBurden.subtitle)
        assertEquals(scoreText(50, R.string.valuation_middle), mood.rateBurden.scoreText)
        assertEquals(emptyList<UiText>(), mood.rateBurden.evidence)
        assertTrue(mood.rateBurden.showLink)

        val sector = requireNotNull(mapper.sector(overview(dataA()), "semiconductors"))
        assertEquals(withSymbol(R.string.target_semiconductors, "SMH"), sector.pricePosition.subtitle)
        assertEquals(emptyList<UiText>(), sector.pricePosition.evidence)
        assertTrue(sector.pricePosition.showLink)
    }

    // 점수를 만들기 쉬운 규칙: 위치 기간 101(메인 기간 하나), 평균 일수 1. 주가 위치는 SPY의 종가로 낸다.
    private fun stageRules(low: Int = 30, high: Int = 70, buffer: Int = 5) = rules.copy(
        valuation = rules.valuation.withSingleWindow(101).copy(
            shortAverageDays = 2,
            longAverageDays = 2,
            smoothingDays = 1,
            threeMonthDays = 2,
            yearAgoDays = 1,
            lowBoundary = low,
            highBoundary = high,
            buffer = buffer,
        ),
    )

    private fun valuationMain(
        priceScores: List<Int> = listOf(50),
        rateScores: List<Int>? = null,
        low: Int = 30,
        high: Int = 70,
        buffer: Int = 5,
    ): MoodUiState {
        val builder = dataA().replaceSeries("SPY", closesForScores(priceScores))
        if (rateScores != null) builder.replaceSeries(MoodTestData.REAL_YIELD, valuesForScores(rateScores))
        return main(builder, stageRules(low, high, buffer))
    }

    // R-66 단계의 이름

    @Test
    fun `R-66 주가 위치 LOW 6, 금리 부담 HIGH 98`() {
        val mood = valuationMain(priceScores = listOf(6), rateScores = listOf(98))
        assertEquals(ValuationStage.LOW, mood.pricePosition.stage)
        assertEquals(scoreText(6, R.string.valuation_price_low), mood.pricePosition.scoreText)
        assertEquals(ValuationStage.HIGH, mood.rateBurden.stage)
        assertEquals(scoreText(98, R.string.valuation_rate_high), mood.rateBurden.scoreText)
    }

    @Test
    fun `R-66 주가 위치 HIGH 74, 금리 부담 LOW 22`() {
        val mood = valuationMain(priceScores = listOf(74), rateScores = listOf(22))
        assertEquals(ValuationStage.HIGH, mood.pricePosition.stage)
        assertEquals(scoreText(74, R.string.valuation_price_high), mood.pricePosition.scoreText)
        assertEquals(ValuationStage.LOW, mood.rateBurden.stage)
        assertEquals(scoreText(22, R.string.valuation_rate_low), mood.rateBurden.scoreText)
    }

    // R-65 칸 폭, 칸 이름, 표시 위치

    @Test
    fun `R-65 데이터 A면 칸 폭 31, 38, 31과 칸 이름, 표시 0_5`() {
        val mood = main(dataA())
        listOf(mood.pricePosition, mood.rateBurden).forEach {
            assertSegments(listOf(0.31f, 0.38f, 0.31f), it.gauge.segments)
            assertEquals(0.5f, it.gauge.position, 1e-6f)
        }
        assertEquals(
            listOf(UiText(R.string.valuation_price_low), UiText(R.string.valuation_middle), UiText(R.string.valuation_price_high)),
            mood.pricePosition.gauge.labels,
        )
        assertEquals(
            listOf(UiText(R.string.valuation_rate_low), UiText(R.string.valuation_middle), UiText(R.string.valuation_rate_high)),
            mood.rateBurden.gauge.labels,
        )
    }

    @Test
    fun `R-65 경계 25 80이면 칸 폭 26, 53, 21`() {
        val changed = rules.copy(valuation = rules.valuation.copy(lowBoundary = 25, highBoundary = 80))
        assertSegments(listOf(0.26f, 0.53f, 0.21f), main(dataA(), changed).pricePosition.gauge.segments)
    }

    @Test
    fun `R-65 점수 0이면 표시 0_0, 100이면 1_0`() {
        assertEquals(0f, valuationMain(priceScores = listOf(0)).pricePosition.gauge.position, 1e-6f)
        assertEquals(1f, valuationMain(priceScores = listOf(100)).pricePosition.gauge.position, 1e-6f)
    }

    // R-67 숫자와 단계가 다른 날 안내

    @Test
    fun `R-67 주가 위치 33, 점수로 정한 단계 MIDDLE, 표시 LOW`() {
        assertEquals(
            UiText(R.string.valuation_notice_low, listOf(33, UiText(R.string.valuation_price_low), 36)),
            valuationMain(priceScores = listOf(30, 33)).pricePosition.notice,
        )
    }

    @Test
    fun `R-67 금리 부담 67, 점수로 정한 단계 MIDDLE, 표시 HIGH`() {
        assertEquals(
            UiText(R.string.valuation_notice_high, listOf(67, UiText(R.string.valuation_rate_high), 64)),
            valuationMain(rateScores = listOf(70, 67)).rateBurden.notice,
        )
    }

    @Test
    fun `R-67 금리 부담 35, 표시 LOW`() {
        assertEquals(
            UiText(R.string.valuation_notice_low, listOf(35, UiText(R.string.valuation_rate_low), 36)),
            valuationMain(rateScores = listOf(30, 35)).rateBurden.notice,
        )
    }

    @Test
    fun `R-67 경계 25 75, 완충 3이면 숫자를 규칙의 값에서 낸다`() {
        assertEquals(
            UiText(R.string.valuation_notice_low, listOf(28, UiText(R.string.valuation_price_low), 29)),
            valuationMain(priceScores = listOf(25, 28), low = 25, high = 75, buffer = 3).pricePosition.notice,
        )
        assertEquals(
            UiText(R.string.valuation_notice_high, listOf(73, UiText(R.string.valuation_price_high), 71)),
            valuationMain(priceScores = listOf(75, 73), low = 25, high = 75, buffer = 3).pricePosition.notice,
        )
    }

    @Test
    fun `R-67 점수로 정한 단계와 표시 단계가 같으면 안내가 없다`() {
        val mood = main(dataA())
        assertNull(mood.pricePosition.notice)
        assertNull(mood.rateBurden.notice)
    }

    @Test
    fun `R-67 다른 기간 카드의 안내 줄은 그 콘텐츠의 점수와 단계로 낸다`() {
        val changed = rules.copy(valuation = rules.valuation.withWindow("3y", 101).copy(smoothingDays = 1))
        val values = valuesForScores(listOf(30, 35))
        val builder = dataA()
        MoodTestData.weekdaysEndingAt(MoodTestData.END, values.size).zip(values).forEach { (day, value) ->
            builder.set(MoodTestData.REAL_YIELD, day, value)
        }
        val card = rate(builder, changed).otherPeriods[1] as OtherPeriodCardUiState.Valuation
        assertEquals(UiText(R.string.valuation_period_label, listOf(years(3))), card.periodText)
        assertEquals(scoreText(35, R.string.valuation_rate_low), card.scoreText)
        assertEquals(UiText(R.string.valuation_notice_low, listOf(35, UiText(R.string.valuation_rate_low), 36)), card.notice)
    }

    // R-68 주가 위치의 근거

    @Test
    fun `R-68 데이터 A의 주가 위치 화면 메인 카드의 근거`() {
        assertEquals(
            listOf(
                UiText(R.string.price_evidence_average, listOf(5, "0.0%", years(3), "0.0%")),
                UiText(R.string.price_evidence_drawdown, listOf("0.0%", months(3), "0.0%")),
            ),
            price(dataA()).main.evidence,
        )
    }

    @Test
    fun `R-68 두 괴리, 낙폭, 3개월 수익률의 글`() {
        // 짧은 평균 2, 긴 평균 3, 위치 기간 3, 3개월 3인 규칙에서 마지막 종가 100의 괴리가 +0.041, +0.214, 낙폭 -0.032, 3개월 +0.058이 되게 한다.
        val p = 200 / 1.041 - 100
        val q = 300 / 1.214 - 100 - p
        val closes = List(70) { 100.0 } + listOf(100 / 0.968, 100 / 1.058, q, p, 100.0)
        val changed = rules.copy(
            valuation = rules.valuation.withSingleWindow(3).copy(
                shortAverageDays = 2,
                longAverageDays = 3,
                smoothingDays = 1,
                threeMonthDays = 3,
            ),
        )
        assertEquals(
            listOf(
                UiText(R.string.price_evidence_average, listOf(2, "+4.1%", years(3), "+21.4%")),
                UiText(R.string.price_evidence_drawdown, listOf("-3.2%", months(3), "+5.8%")),
            ),
            price(dataA().replaceSeries("SPY", closes), rules = changed).main.evidence,
        )
    }

    // R-69 금리 부담의 근거

    private fun rateData(): MoodTestData.Builder = dataA()
        .set(MoodTestData.REAL_YIELD, "2026-11-23", 2.79)
        .set(MoodTestData.REAL_YIELD, "2025-11-21", 1.82)
        .set(MoodTestData.TREASURY_10Y, "2026-11-23", 4.92)
        .set(MoodTestData.POLICY_RATE, "2026-11-23", 3.88)
        .set(MoodTestData.CONSUMER_PRICE, "2026-10-01", 312.3)

    private fun rising(change: String) = UiText(R.string.rate_evidence_year_change, listOf(change, UiText(R.string.rate_evidence_rising)))

    @Test
    fun `R-69 데이터 A의 금리 부담 화면 메인 카드의 근거`() {
        assertEquals(
            listOf(
                UiText(R.string.rate_evidence_year_same),
                UiText(R.string.rate_evidence_rates, listOf("4.00%", "3.50%")),
                UiText(R.string.rate_evidence_inflation, listOf("3.0%", "2", "+1.0%p", 10)),
            ),
            rate(dataA()).main.evidence,
        )
    }

    @Test
    fun `R-69 실질금리, 1년 전과의 차이, 금리, 물가의 글`() {
        val card = rate(rateData()).main
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf("2.79%")), card.subtitle)
        assertEquals(
            listOf(
                rising("+0.97%p"),
                UiText(R.string.rate_evidence_rates, listOf("4.92%", "3.88%")),
                UiText(R.string.rate_evidence_inflation, listOf("4.1%", "2", "+2.1%p", 10)),
            ),
            card.evidence,
        )
    }

    @Test
    fun `R-69 차이가 음수면 내리는 중`() {
        assertEquals(
            UiText(R.string.rate_evidence_year_change, listOf("-0.18%p", UiText(R.string.rate_evidence_falling))),
            rate(rateData().set(MoodTestData.REAL_YIELD, "2025-11-21", 2.97)).main.evidence[0],
        )
    }

    @Test
    fun `R-69 적는 값이 0이면 1년 전과 같음`() {
        val same = UiText(R.string.rate_evidence_year_same)
        assertEquals(same, rate(rateData().set(MoodTestData.REAL_YIELD, "2025-11-21", 2.79)).main.evidence[0])
        assertEquals(same, rate(rateData().set(MoodTestData.REAL_YIELD, "2025-11-21", 2.786)).main.evidence[0])
    }

    @Test
    fun `R-69 차이 +0_005는 +0_01%p로 오르는 중`() {
        val builder = rateData()
            .set(MoodTestData.REAL_YIELD, "2026-11-23", 1.0)
            .set(MoodTestData.REAL_YIELD, "2025-11-21", 0.995)
        assertEquals(rising("+0.01%p"), rate(builder).main.evidence[0])
    }

    @Test
    fun `R-69 물가 1_8%, 9월 값`() {
        val builder = rateData()
            .remove(MoodTestData.CONSUMER_PRICE, "2026-10-01")
            .set(MoodTestData.CONSUMER_PRICE, "2026-09-01", 305.4)
        assertEquals(
            UiText(R.string.rate_evidence_inflation, listOf("1.8%", "2", "-0.2%p", 9)),
            rate(builder).main.evidence[2],
        )
    }

    // R-70 날짜를 붙일지와 그 글

    @Test
    fun `R-70 실질금리, 10년물, 기준금리의 날짜가 기준 날짜와 다르면 값 뒤에 날짜를 붙인다`() {
        val builder = rateData()
            .remove(MoodTestData.REAL_YIELD, "2026-11-23")
            .set(MoodTestData.REAL_YIELD, "2026-11-21", 2.79)
            .remove(MoodTestData.TREASURY_10Y, "2026-11-23")
            .set(MoodTestData.TREASURY_10Y, "2026-11-21", 4.92)
            .remove(MoodTestData.POLICY_RATE, "2026-11-23")
            .set(MoodTestData.POLICY_RATE, "2026-11-21", 3.88)
        val card = rate(builder).main
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf(withDate("2.79%", 11, 21))), card.subtitle)
        assertEquals(
            UiText(R.string.rate_evidence_rates, listOf(withDate("4.92%", 11, 21), withDate("3.88%", 11, 21))),
            card.evidence[1],
        )
        // 첫 줄과 점수에는 날짜가 없다.
        assertEquals(rising("+0.97%p"), card.evidence[0])
        // 점수: 실질금리의 날(11월 21일)까지 세 날의 위치 0.5, 0.5, 1.0의 평균
        assertEquals(scoreText(67, R.string.valuation_middle), card.scoreText)
        assertEquals(card.subtitle, main(builder).rateBurden.subtitle)
    }

    @Test
    fun `R-70 기준금리만 날짜가 다르면 기준금리에만 붙인다`() {
        val builder = rateData()
            .remove(MoodTestData.POLICY_RATE, "2026-11-23")
            .set(MoodTestData.POLICY_RATE, "2026-11-20", 3.88)
        val card = rate(builder).main
        assertEquals(UiText(R.string.rate_burden_subtitle, listOf("2.79%")), card.subtitle)
        assertEquals(UiText(R.string.rate_evidence_rates, listOf("4.92%", withDate("3.88%", 11, 20))), card.evidence[1])
    }

    // R-91 매매 판단의 다른 기간 카드

    @Test
    fun `R-91 주가 위치 화면과 금리 부담 화면의 다른 기간 카드 셋`() {
        listOf(
            R.string.price_position_name to price(dataA()).otherPeriods,
            R.string.rate_burden_name to rate(dataA()).otherPeriods,
        ).forEach { (nameRes, others) ->
            val cards = others.map { it as OtherPeriodCardUiState.Valuation }
            assertEquals(
                listOf(years(1), years(3), years(5)).map { UiText(R.string.valuation_period_label, listOf(it)) },
                cards.map { it.periodText },
            )
            cards.forEach {
                assertEquals(UiText(nameRes), it.name)
                assertEquals(scoreText(50, R.string.valuation_middle), it.scoreText)
                assertEquals(ValuationStage.MIDDLE, it.stage)
                assertSegments(listOf(0.31f, 0.38f, 0.31f), it.gauge.segments)
                assertEquals(0.5f, it.gauge.position, 1e-6f)
                assertNull(it.notice)
            }
        }
    }

    // R-92, R-104 낼 수 없는 기간

    @Test
    fun `R-92 5y 위치 기간이 600이면 셋째 카드는 낼 수 없음`() {
        val changed = rules.copy(valuation = rules.valuation.withWindow("5y", 600))
        listOf(
            R.string.price_position_name to price(dataA(), rules = changed).otherPeriods,
            R.string.rate_burden_name to rate(dataA(), changed).otherPeriods,
        ).forEach { (nameRes, others) ->
            assertEquals(
                OtherPeriodCardUiState.Unavailable(
                    UiText(nameRes),
                    UiText(R.string.valuation_period_label, listOf(years(5))),
                    UiText(R.string.valuation_period_unavailable, listOf(years(5))),
                ),
                others[2],
            )
        }
    }

    @Test
    fun `R-104 QQQ를 마지막 200평일만 남기면 장세 화면의 1년 카드는 낼 수 없음`() {
        val keep = MoodTestData.weekdaysEndingAt(MoodTestData.END, 200).toSet()
        val others = regime(dataA().keepOnly("QQQ") { it in keep }, "nasdaq_100").otherPeriods
        assertTrue(others[0] is OtherPeriodCardUiState.Regime)
        assertEquals(
            OtherPeriodCardUiState.Unavailable(
                sectorRegime(R.string.target_nasdaq_100),
                UiText(R.string.regime_period_label, listOf(years(1))),
                UiText(R.string.regime_period_unavailable, listOf(years(1))),
            ),
            others[1],
        )
    }

    // R-86 다른 기간 카드의 순서

    @Test
    fun `R-86 다른 기간 카드는 메인을 뺀 짧은 기간부터`() {
        assertEquals(
            listOf(months(6), years(1)).map { UiText(R.string.regime_period_label, listOf(it)) },
            regime(dataA()).otherPeriods.map { it.periodText },
        )
        val valuation = listOf(years(1), years(3), years(5)).map { UiText(R.string.valuation_period_label, listOf(it)) }
        assertEquals(valuation, price(dataA()).otherPeriods.map { it.periodText })
        assertEquals(valuation, rate(dataA()).otherPeriods.map { it.periodText })
    }

    // R-05 매매 판단 제목

    @Test
    fun `R-05 매매 판단 제목과 그 아래 한 줄`() {
        val mood = main(dataA())
        assertEquals(UiText(R.string.valuation_title, listOf(years(2))), mood.valuationTitle)
        assertEquals(UiText(R.string.valuation_description, listOf(years(2))), mood.valuationDescription)
    }

    // R-82, R-96, R-105 섹터 목록

    @Test
    fun `R-82 데이터 A의 섹터 머리와 줄 셋`() {
        val mood = main(dataA())
        assertEquals(
            SectorHeaderUiState(
                UiText(R.string.sectors_header_name),
                UiText(R.string.sectors_header_regime, listOf(months(3))),
                UiText(R.string.sectors_header_price, listOf(years(2))),
            ),
            mood.sectorHeader,
        )
        assertEquals(
            listOf(
                Triple("nasdaq_100", R.string.target_nasdaq_100, "QQQ"),
                Triple("semiconductors", R.string.target_semiconductors, "SMH"),
                Triple("technology", R.string.target_technology, "XLK"),
            ).map { (id, nameRes, symbol) ->
                SectorRowUiState(
                    targetId = id,
                    name = UiText(nameRes),
                    symbol = symbol,
                    asOfText = null,
                    content = SectorRowContent.Values(
                        MarketStage.BULL,
                        stage(R.string.stage_bull),
                        ValuationStage.MIDDLE,
                        scoreText(50, R.string.valuation_middle),
                    ),
                    clickable = true,
                )
            },
            mood.sectors,
        )
    }

    @Test
    fun `R-83 QQQ의 2026-11-23을 빼면 QQQ 줄에만 기준 날짜 줄이 있다`() {
        val rows = main(dataA().remove("QQQ", "2026-11-23")).sectors
        assertEquals(UiText(R.string.sector_as_of, listOf(11, 20)), rows[0].asOfText)
        assertNull(rows[1].asOfText)
        assertNull(rows[2].asOfText)
    }

    @Test
    fun `R-84 SMH를 빼면 SMH 줄은 받지 못함, 받는 중이면 받는 중`() {
        val failed = main(dataA().removeSeries("SMH")).sectors[1]
        assertEquals(SectorRowContent.Status(UiText(R.string.sector_failed)), failed.content)
        assertFalse(failed.clickable)
        assertNull(failed.asOfText)
        val loading = main(dataA().removeSeries("SMH"), refreshing = true).sectors[1]
        assertEquals(SectorRowContent.Status(UiText(R.string.sector_loading)), loading.content)
        assertFalse(loading.clickable)
    }

    @Test
    fun `R-84 QQQ를 마지막 60평일만 남기면 QQQ 줄은 받지 못함`() {
        val keep = MoodTestData.weekdaysEndingAt(MoodTestData.END, 60).toSet()
        val row = main(dataA().keepOnly("QQQ") { it in keep }).sectors[0]
        assertEquals(SectorRowContent.Status(UiText(R.string.sector_failed)), row.content)
        assertFalse(row.clickable)
    }

    // R-107 문구의 숫자가 규칙을 따름

    @Test
    fun `R-107 일수와 기간의 이름은 규칙에서 낸다`() {
        val changed = rules.copy(
            regime = rules.regime.copy(
                periods = rules.regime.periods.map {
                    if (it.id == "3m") it.copy(averageDays = 40, slopeDays = 10, length = PeriodLength(2, PeriodUnit.MONTHS)) else it
                },
            ),
            valuation = rules.valuation.copy(
                shortAverageDays = 150,
                longAverageLength = PeriodLength(4, PeriodUnit.YEARS),
                threeMonthLength = PeriodLength(2, PeriodUnit.MONTHS),
                periods = rules.valuation.periods.map { if (it.id == "2y") it.copy(length = PeriodLength(18, PeriodUnit.MONTHS)) else it },
            ),
        )
        val mood = main(dataA(), changed)
        assertEquals(UiText(R.string.regime_card_title, listOf(broadRegime, months(2))), mood.regime.title)
        assertEquals(UiText(R.string.valuation_title, listOf(months(18))), mood.valuationTitle)
        assertEquals(UiText(R.string.valuation_description, listOf(months(18))), mood.valuationDescription)
        assertEquals(UiText(R.string.sectors_header_regime, listOf(months(2))), mood.sectorHeader.regimeText)
        assertEquals(UiText(R.string.sectors_header_price, listOf(months(18))), mood.sectorHeader.pricePositionText)

        val detail = regime(dataA(), rules = changed)
        assertEquals(UiText(R.string.evidence_basis, listOf(months(2))), detail.evidenceBasis)
        assertEquals(
            listOf(
                UiText(R.string.evidence_above_average, listOf(40, "0.0%")),
                UiText(R.string.evidence_from_high, listOf(months(2), "0.0%")),
                UiText(R.string.evidence_average_slope, listOf(40, 10, "0.0%")),
            ),
            detail.groups[0].indicators.map { it.evidence },
        )
        assertEquals(
            listOf(
                UiText(R.string.price_evidence_average, listOf(150, "0.0%", years(4), "0.0%")),
                UiText(R.string.price_evidence_drawdown, listOf("0.0%", months(2), "0.0%")),
            ),
            price(dataA(), rules = changed).main.evidence,
        )
    }
}
