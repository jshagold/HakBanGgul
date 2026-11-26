package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.HIGH_YIELD
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.YIELD_CURVE
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataB
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RegimeCalculatorTest {

    private val calculator = RegimeCalculator()
    private val rules = DefaultMoodRules.rules.regime
    private val main = rules.mainPeriod()

    // 작은 장세 기간
    private val small = RegimePeriod("t", PeriodLength(1, PeriodUnit.MONTHS), 3, 4, 0.05, 0.10, 2)

    // 2026-11-02(월)부터 이어진 평일
    private val days: List<LocalDate> = MoodTestData.weekdays(date("2026-11-02"), date("2026-12-31"))
    private fun d(n: Int) = days[n - 1]

    private fun closes(vararg values: Double): List<DailyValue> = values.mapIndexed { i, v -> DailyValue(days[i], v) }

    // 종가와 같은 날들에 T10Y2Y 0.60, BAMLH0A0HYM2 3.00(채권 신호 +1, +1)
    private fun bonds(count: Int): Map<String, List<DailyValue>> = mapOf(
        YIELD_CURVE to days.take(count).map { DailyValue(it, 0.60) },
        HIGH_YIELD to days.take(count).map { DailyValue(it, 3.00) },
    )

    private fun assessSmall(vararg values: Double, period: RegimePeriod = small): RegimeAssessment? =
        calculator.assess(rules, period, closes(*values), bonds(values.size))

    private fun RegimeAssessment.indicator(id: String) = groups.flatMap { it.indicators }.single { it.id == id }
    private fun RegimeAssessment.ratio(id: String) = (indicator(id).evidence as IndicatorEvidence.RatioEvidence).ratio
    private fun RegimeAssessment.score(id: String) = indicator(id).score
    private fun RegimeAssessment.group(id: String) = groups.single { it.id == id }

    // R-101 세 값과 세 점수

    @Test
    fun `R-101 종가 100, 100, 100, 100, 104, 95`() {
        val result = requireNotNull(assessSmall(100.0, 100.0, 100.0, 100.0, 104.0, 95.0))
        assertEquals(d(6), result.asOfDate)
        assertEquals(-0.046823, result.ratio("above_average"), 1e-6)
        assertEquals(-0.086538, result.ratio("from_high"), 1e-6)
        assertEquals(-0.003333, result.ratio("average_slope"), 1e-6)
        assertEquals(-1.0, result.score("above_average"), 0.0)
        assertEquals(0.0, result.score("from_high"), 0.0)
        assertEquals(-1.0, result.score("average_slope"), 0.0)
        assertEquals(-0.666667, result.group("stocks").score, 1e-6)
        assertEquals(-0.166667, result.compositeScore, 0.0)
        assertEquals(MarketStage.WEAK, result.computedStage)
        assertEquals(MarketStage.BULL, result.displayedStage)
        assertEquals(1, result.computedStageStreak)
    }

    @Test
    fun `R-101 평균 괴리 +0_02는 평균 대비 +1`() {
        val result = requireNotNull(assessSmall(100.0, 100.0, 100.0, 99.0, 99.0, 102.0))
        assertEquals(0.02, result.ratio("above_average"), 1e-9)
        assertEquals(1.0, result.score("above_average"), 0.0)
    }

    @Test
    fun `R-101 평균 괴리 -0_02는 평균 대비 0`() {
        val result = requireNotNull(assessSmall(100.0, 100.0, 100.0, 101.0, 101.0, 98.0))
        assertEquals(-0.02, result.ratio("above_average"), 1e-9)
        assertEquals(0.0, result.score("above_average"), 0.0)
    }

    @Test
    fun `R-101 하락률 0_05는 +1, 0_10은 -1, 0_101은 -1`() {
        assertEquals(1.0, requireNotNull(assessSmall(100.0, 100.0, 100.0, 100.0, 100.0, 95.0)).score("from_high"), 0.0)
        assertEquals(-1.0, requireNotNull(assessSmall(100.0, 100.0, 100.0, 100.0, 100.0, 90.0)).score("from_high"), 0.0)
        assertEquals(-1.0, requireNotNull(assessSmall(100.0, 100.0, 100.0, 100.0, 100.0, 89.9)).score("from_high"), 0.0)
    }

    @Test
    fun `R-101 종가 100 다섯 날이면 기울기 0이라 +1, 평균 괴리 0이라 0`() {
        val result = requireNotNull(assessSmall(100.0, 100.0, 100.0, 100.0, 100.0))
        assertEquals(1.0, result.score("average_slope"), 0.0)
        assertEquals(0.0, result.score("above_average"), 0.0)
    }

    @Test
    fun `R-101 기본 규칙의 3m, 데이터 A에서 SPY 2026-11-23만 104_1`() {
        val values = dataA().set("SPY", "2026-11-23", 104.1).build()
        val result = requireNotNull(calculator.assess(rules, main, values["SPY"], values))
        assertEquals(104.1 / 100.082 - 1, result.ratio("above_average"), 1e-9)
        assertEquals(0.040147, result.ratio("above_average"), 1e-6)
        assertEquals(0.0, result.ratio("from_high"), 1e-12)
        assertEquals(0.00082, result.ratio("average_slope"), 1e-9)
        assertEquals(1.0, result.score("above_average"), 0.0)
        assertEquals(1.0, result.score("from_high"), 0.0)
        assertEquals(1.0, result.score("average_slope"), 0.0)
    }

    // R-36 계산할 수 있는 날

    @Test
    fun `R-36 종가 넷이면 null`() {
        assertNull(assessSmall(100.0, 100.0, 100.0, 100.0))
    }

    @Test
    fun `R-36 종가 다섯이면 d5, 불장, 0_766667, 이어진 거래일 1`() {
        val result = requireNotNull(assessSmall(100.0, 100.0, 100.0, 100.0, 100.0))
        assertEquals(d(5), result.asOfDate)
        assertEquals(MarketStage.BULL, result.displayedStage)
        assertEquals(MarketStage.BULL, result.computedStage)
        assertEquals(0.766667, result.compositeScore, 0.0)
        assertEquals(1, result.computedStageStreak)
    }

    @Test
    fun `R-36 고점 일수 6이면 종가 다섯은 null, 여섯이면 첫 계산할 수 있는 날 d6`() {
        val period = small.copy(highDays = 6)
        assertNull(assessSmall(100.0, 100.0, 100.0, 100.0, 100.0, period = period))
        // 첫 날이 d6뿐이라 이어진 거래일은 1이다.
        val result = requireNotNull(assessSmall(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, period = period))
        assertEquals(d(6), result.asOfDate)
        assertEquals(1, result.computedStageStreak)
    }

    @Test
    fun `R-36 T10Y2Y가 d5에만 있으면 d5만 계산할 수 있다`() {
        val values = bonds(5) + (YIELD_CURVE to listOf(DailyValue(d(5), 0.60)))
        val result = requireNotNull(calculator.assess(rules, small, closes(100.0, 100.0, 100.0, 100.0, 100.0), values))
        assertEquals(d(5), result.asOfDate)
        assertEquals(1, result.computedStageStreak)
    }

    @Test
    fun `R-36 T10Y2Y가 d6에만 있으면 null`() {
        val values = bonds(5) + (YIELD_CURVE to listOf(DailyValue(d(6), 0.60)))
        assertNull(calculator.assess(rules, small, closes(100.0, 100.0, 100.0, 100.0, 100.0), values))
    }

    // R-37, R-38 대상마다의 거래일

    @Test
    fun `R-37 QQQ의 2026-11-23을 빼면 QQQ의 기준 날짜는 2026-11-20, SPY는 2026-11-23`() {
        val values = dataA().remove("QQQ", "2026-11-23").build()
        assertEquals(date("2026-11-20"), calculator.assess(rules, main, values["QQQ"], values)?.asOfDate)
        assertEquals(date("2026-11-23"), calculator.assess(rules, main, values["SPY"], values)?.asOfDate)
    }

    // R-39, R-12

    @Test
    fun `R-39 데이터 B의 채권 신호 근거와 점수`() {
        val values = dataB().build()
        val result = requireNotNull(calculator.assess(rules, main, values["SPY"], values))
        val spread = result.indicator("high_yield_spread")
        assertEquals(IndicatorEvidence.ValueEvidence(3.76, date("2026-11-20")), spread.evidence)
        assertEquals(1.0, spread.score, 0.0)
        val curve = result.indicator("yield_curve")
        assertEquals(IndicatorEvidence.ValueEvidence(0.29, date("2026-11-23")), curve.evidence)
        assertEquals(0.0, curve.score, 0.0)
    }

    // R-100 주식 항목은 그 대상의 종가

    @Test
    fun `R-100 데이터 B에서 SMH의 종가로 내면 물장 계산, 불장 표시`() {
        val values = dataB().build()
        val result = requireNotNull(calculator.assess(rules, main, values["SMH"], values))
        assertEquals(listOf(-1.0, -1.0, -1.0), result.group("stocks").indicators.map { it.score })
        assertEquals(0.5, result.group("bond_signals").score, 1e-12)
        assertEquals(-0.55, result.compositeScore, 0.0)
        assertEquals(MarketStage.BEAR, result.computedStage)
        assertEquals(MarketStage.BULL, result.displayedStage)
    }

    @Test
    fun `R-100 데이터 B에서 SPY의 종가로 내면 0, +1, -1과 0_15`() {
        val values = dataB().build()
        val result = requireNotNull(calculator.assess(rules, main, values["SPY"], values))
        assertEquals(listOf(0.0, 1.0, -1.0), result.group("stocks").indicators.map { it.score })
        assertEquals(0.5, result.group("bond_signals").score, 1e-12)
        assertEquals(0.15, result.compositeScore, 0.0)
    }
}
