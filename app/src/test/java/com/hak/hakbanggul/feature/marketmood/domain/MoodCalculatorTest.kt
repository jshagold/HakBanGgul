package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.HIGH_YIELD
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.YIELD_CURVE
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataB
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.date
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.withRegimeWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MoodCalculatorTest {

    private val calculator = MoodCalculator(RegimeCalculator(), ValuationCalculator())
    private val rules = MoodTestData.RULES

    // 시험용 규칙에서 비중만 주식 0.75, 채권 신호 0.25로 바꾼 규칙
    private val weighted = rules.withRegimeWeights(0.75, 0.25)

    private fun assess(builder: MoodTestData.Builder, rules: MoodRules = this.rules): MoodAssessment =
        requireNotNull(calculator.assess(rules, builder.build())) { "판정을 낼 수 있어야 한다" }

    private fun MoodAssessment.indicator(id: String): IndicatorAssessment =
        groups.flatMap { it.indicators }.single { it.id == id }

    private fun MoodAssessment.group(id: String): GroupAssessment = groups.single { it.id == id }

    private fun MoodAssessment.ratio(id: String) = (indicator(id).evidence as IndicatorEvidence.RatioEvidence).ratio

    private fun MoodAssessment.value(id: String) = indicator(id).evidence as IndicatorEvidence.ValueEvidence

    private fun score(builder: MoodTestData.Builder, id: String): Double = assess(builder).indicator(id).score

    // R-38, R-37

    @Test
    fun `R-38 데이터 A의 기준 날짜는 2026-11-23`() {
        assertEquals(date("2026-11-23"), assess(dataA()).asOfDate)
    }

    @Test
    fun `R-37 SPY의 2026-11-23이 없으면 기준 날짜는 2026-11-20`() {
        val result = assess(dataA().remove("SPY", "2026-11-23"))
        assertEquals(date("2026-11-20"), result.asOfDate)
        assertEquals(0.766667, result.compositeScore, 0.0)
        assertEquals(MarketStage.BULL, result.displayedStage)
    }

    @Test
    fun `R-37 QQQ의 2026-11-23이 없어도 기준 날짜는 2026-11-23`() {
        assertEquals(date("2026-11-23"), assess(dataA().remove("QQQ", "2026-11-23")).asOfDate)
    }

    // R-101 평균 대비

    @Test
    fun `R-101 데이터 A면 평균 대비 0점, 근거 0`() {
        val result = assess(dataA())
        assertEquals(0.0, result.indicator("above_average").score, 0.0)
        assertEquals(0.0, result.ratio("above_average"), 1e-12)
    }

    @Test
    fun `R-101 SPY 2026-11-23이 104_1이면 평균 대비 +1`() {
        val result = assess(dataA().set("SPY", "2026-11-23", 104.1))
        assertEquals(1.0, result.indicator("above_average").score, 0.0)
        assertEquals(0.040147, result.ratio("above_average"), 1e-6)
    }

    @Test
    fun `R-101 SPY 2026-11-23이 99면 평균 대비 0`() {
        val result = assess(dataA().set("SPY", "2026-11-23", 99.0))
        assertEquals(0.0, result.indicator("above_average").score, 0.0)
        assertEquals(-0.009802, result.ratio("above_average"), 1e-6)
    }

    // R-101 고점 대비

    @Test
    fun `R-101 SPY 2026-11-23이 95면 고점 대비 +1`() {
        assertEquals(1.0, score(dataA().set("SPY", "2026-11-23", 95.0), "from_high"), 0.0)
    }

    @Test
    fun `R-101 93이면 고점 대비 0, 근거 -0_07`() {
        val result = assess(dataA().set("SPY", "2026-11-23", 93.0))
        assertEquals(0.0, result.indicator("from_high").score, 0.0)
        assertEquals(-0.07, result.ratio("from_high"), 1e-12)
    }

    @Test
    fun `R-101 90이면 고점 대비 -1`() {
        assertEquals(-1.0, score(dataA().set("SPY", "2026-11-23", 90.0), "from_high"), 0.0)
    }

    @Test
    fun `R-101 80이면 고점 대비 -1, 근거 -0_2`() {
        val result = assess(dataA().set("SPY", "2026-11-23", 80.0))
        assertEquals(-1.0, result.indicator("from_high").score, 0.0)
        assertEquals(-0.2, result.ratio("from_high"), 1e-12)
    }

    @Test
    fun `R-101 고점은 그날을 포함한 최근 63거래일`() {
        // 2026-08-27은 2026-11-23을 포함해 63번째 평일이다.
        assertEquals(date("2026-08-27"), MoodTestData.weekdaysEndingAt(date("2026-11-23"), 63).first())
        val inside = assess(dataA().set("SPY", "2026-08-27", 120.0))
        assertEquals(-0.166667, inside.ratio("from_high"), 1e-6)
        val outside = assess(dataA().set("SPY", "2026-08-17", 120.0))
        assertEquals(0.0, outside.ratio("from_high"), 1e-12)
    }

    // R-33

    @Test
    fun `R-33 yield_curve 점수 경계`() {
        val cases = listOf(0.5 to 1.0, 0.49 to 0.0, 0.0 to 0.0, -0.01 to -1.0)
        cases.forEach { (value, expected) ->
            assertEquals("값 $value", expected, score(dataA().set(YIELD_CURVE, "2026-11-23", value), "yield_curve"), 0.0)
        }
    }

    @Test
    fun `R-33 high_yield_spread 점수 경계`() {
        val cases = listOf(3.99 to 1.0, 4.0 to 0.0, 6.0 to 0.0, 6.01 to -1.0)
        cases.forEach { (value, expected) ->
            assertEquals("값 $value", expected, score(dataA().set(HIGH_YIELD, "2026-11-23", value), "high_yield_spread"), 0.0)
        }
    }

    // R-34

    @Test
    fun `R-34 데이터 B의 묶음 점수와 현재 상태`() {
        val result = assess(dataB())
        assertEquals(0.0, result.group("stocks").score, 1e-12)
        assertEquals(0.5, result.group("bond_signals").score, 1e-12)
        assertEquals(0.15, result.compositeScore, 0.0)
    }

    // R-35

    @Test
    fun `R-35 경계에 꼭 걸린 0_5는 불장`() {
        val result = assess(dataA().set(YIELD_CURVE, "2026-11-23", 0.29).set(HIGH_YIELD, "2026-11-23", 5.00), weighted)
        assertEquals(0.5, result.compositeScore, 0.0)
        assertEquals(MarketStage.BULL, result.computedStage)
    }

    @Test
    fun `R-35 데이터 B는 중립`() {
        val result = assess(dataB())
        assertEquals(0.15, result.compositeScore, 0.0)
        assertEquals(MarketStage.NEUTRAL, result.computedStage)
    }

    @Test
    fun `R-35 현재 상태 0은 중립`() {
        val result = assess(
            dataA().set("SPY", "2026-11-23", 99.0).set(YIELD_CURVE, "2026-11-23", 0.29).set(HIGH_YIELD, "2026-11-23", 5.00),
        )
        assertEquals(0.0, result.compositeScore, 0.0)
        assertEquals(MarketStage.NEUTRAL, result.computedStage)
    }

    @Test
    fun `R-35 현재 상태 -0_5는 약세`() {
        val result = assess(
            dataA().set("SPY", "2026-11-23", 93.0).set(YIELD_CURVE, "2026-11-23", 0.29).set(HIGH_YIELD, "2026-11-23", 5.00),
            weighted,
        )
        assertEquals(-0.666667, result.group("stocks").score, 1e-6)
        assertEquals(-0.5, result.compositeScore, 0.0)
        assertEquals(MarketStage.WEAK, result.computedStage)
    }

    @Test
    fun `R-35 현재 상태 -0_85는 물장`() {
        val result = assess(
            dataA().set("SPY", "2026-11-23", 80.0).set(YIELD_CURVE, "2026-11-23", -0.01).set(HIGH_YIELD, "2026-11-23", 5.00),
        )
        assertEquals(-0.85, result.compositeScore, 0.0)
        assertEquals(MarketStage.BEAR, result.computedStage)
    }

    // R-36

    @Test
    fun `R-36 나쁜 날이 이틀이면 표시 단계는 그대로 불장`() {
        val result = assess(dataA().badDay("2026-11-20", "2026-11-23"))
        assertEquals(MarketStage.BULL, result.displayedStage)
        assertEquals(MarketStage.NEUTRAL, result.computedStage)
        assertEquals(0.166667, result.compositeScore, 0.0)
    }

    @Test
    fun `R-36 나쁜 날이 사흘 이어지면 3거래일째부터 중립`() {
        val result = assess(dataA().badDay("2026-11-19", "2026-11-20", "2026-11-23"))
        assertEquals(MarketStage.NEUTRAL, result.displayedStage)
    }

    @Test
    fun `R-36 표시 단계와 같은 날이 끼면 연속이 끊긴다`() {
        val result = assess(dataA().badDay("2026-11-17", "2026-11-18", "2026-11-20", "2026-11-23"))
        assertEquals(MarketStage.BULL, result.displayedStage)
    }

    @Test
    fun `R-36 다른 단계가 끼면 연속이 끊긴다`() {
        val builder = dataA().badDay("2026-11-18", "2026-11-19", "2026-11-20", "2026-11-23").set("SPY", "2026-11-20", 80.0)
        assertEquals(
            MarketStage.BEAR,
            assess(dataA().badDay("2026-11-18", "2026-11-19", "2026-11-20").set("SPY", "2026-11-20", 80.0).truncateAllAfter("2026-11-20"))
                .computedStage,
        )
        val result = assess(builder)
        assertEquals(-0.3, result.compositeScore, 0.0)
        assertEquals(MarketStage.WEAK, result.computedStage)
        assertEquals(MarketStage.BULL, result.displayedStage)
    }

    @Test
    fun `R-36 첫 거래일의 계산 단계가 표시 단계가 된다`() {
        val keep = MoodTestData.weekdaysEndingAt(MoodTestData.END, 72).toSet()
        assertEquals(date("2026-08-14"), keep.min())
        val result = assess(dataA().keepOnly("SPY") { it in keep }.badDay("2026-11-19"))
        assertEquals(MarketStage.NEUTRAL, result.displayedStage)
        assertEquals(MarketStage.BULL, result.computedStage)
        assertEquals(2, result.computedStageStreak)
    }

    @Test
    fun `R-36 계산할 수 있는 날이 하루뿐이면 표시 단계와 계산 단계가 같다`() {
        val result = assess(dataA().keepOnly(YIELD_CURVE) { it == date("2026-11-23") })
        assertEquals(MarketStage.BULL, result.displayedStage)
        assertEquals(MarketStage.BULL, result.computedStage)
        assertEquals(1, result.computedStageStreak)
    }

    // R-08

    @Test
    fun `R-08 나쁜 날이 이틀이면 이어진 거래일 수 2, 유지 일수 3`() {
        val result = assess(dataA().badDay("2026-11-20", "2026-11-23"))
        assertEquals(2, result.computedStageStreak)
        assertEquals(3, result.holdDays)
    }

    @Test
    fun `R-08 데이터 B는 불장 표시, 중립 계산, 1일째`() {
        val result = assess(dataB())
        assertEquals(MarketStage.BULL, result.displayedStage)
        assertEquals(MarketStage.NEUTRAL, result.computedStage)
        assertEquals(1, result.computedStageStreak)
    }

    @Test
    fun `R-08 다른 단계가 끼면 이어진 거래일 수가 다시 1`() {
        val result = assess(
            dataA().badDay("2026-11-18", "2026-11-19", "2026-11-20", "2026-11-23").set("SPY", "2026-11-20", 80.0),
        )
        assertEquals(1, result.computedStageStreak)
    }

    // R-11

    @Test
    fun `R-11 SPY 2026-11-23이 104_1일 때의 세 값`() {
        val result = assess(dataA().set("SPY", "2026-11-23", 104.1))
        assertEquals(0.040147, result.ratio("above_average"), 1e-6)
        assertEquals(0.0, result.ratio("from_high"), 1e-12)
        assertEquals(0.00082, result.ratio("average_slope"), 1e-9)
    }

    @Test
    fun `R-11 데이터 B의 SPY의 세 값`() {
        val result = assess(dataB())
        assertEquals(99.0 / 99.98 - 1, result.ratio("above_average"), 1e-12)
        assertEquals(-0.009802, result.ratio("above_average"), 1e-6)
        assertEquals(-0.01, result.ratio("from_high"), 1e-12)
        assertEquals(-0.0002, result.ratio("average_slope"), 1e-12)
    }

    // R-39, R-12

    @Test
    fun `R-39 값이 없는 날은 가장 가까운 앞날의 값을 쓴다`() {
        val result = assess(dataB())
        assertEquals(3.76, result.value("high_yield_spread").value, 0.0)
        assertEquals(1.0, result.indicator("high_yield_spread").score, 0.0)
        assertEquals(0.29, result.value("yield_curve").value, 0.0)
    }

    @Test
    fun `R-12 근거에 값의 날짜가 담긴다`() {
        val result = assess(dataB())
        assertEquals(date("2026-11-20"), result.value("high_yield_spread").valueDate)
        assertEquals(date("2026-11-23"), result.value("yield_curve").valueDate)
    }

    // R-40

    @Test
    fun `R-40 XLK가 없어도 판정이 있다`() {
        assertNotNull(calculator.assess(rules, dataA().removeSeries("XLK").build()))
    }

    @Test
    fun `R-40 SPY가 없으면 null`() {
        assertNull(calculator.assess(rules, dataA().removeSeries("SPY").build()))
    }

    @Test
    fun `R-40 빈 값이면 null`() {
        assertNull(calculator.assess(rules, emptyMap()))
    }

    @Test
    fun `R-40 SPY 마지막 70평일이면 판정이 있고 69평일이면 null`() {
        val keep70 = MoodTestData.weekdaysEndingAt(MoodTestData.END, 70).toSet()
        val keep69 = MoodTestData.weekdaysEndingAt(MoodTestData.END, 69).toSet()
        assertNotNull(calculator.assess(rules, dataA().keepOnly("SPY") { it in keep70 }.build()))
        assertNull(calculator.assess(rules, dataA().keepOnly("SPY") { it in keep69 }.build()))
    }

    // R-40 매매 판단이 없으면 판정도 없음

    @Test
    fun `R-40 데이터 A면 판정이 있고 매매 판단이 실린다`() {
        val result = assess(dataA())
        assertEquals(0.766667, result.compositeScore, 0.0)
        assertEquals(MarketStage.BULL, result.displayedStage)
        assertEquals(MarketStage.BULL, result.computedStage)
        assertEquals(0.666667, result.group("stocks").score, 1e-6)
        assertEquals(1.0, result.group("bond_signals").score, 0.0)
        assertEquals(ValuationCalculatorTest.dataAValuation(), result.valuation)
        assertEquals(50, result.valuation.pricePosition.score)
        assertEquals(50, result.valuation.rateBurden.score)
    }

    @Test
    fun `R-40 DFII10이 없으면 장세를 낼 수 있어도 null`() {
        assertNull(calculator.assess(rules, dataA().removeSeries(MoodTestData.REAL_YIELD).build()))
    }

    @Test
    fun `R-40 CPIAUCSL이 2025-11-01부터만 있으면 null`() {
        val builder = dataA().keepOnly(MoodTestData.CONSUMER_PRICE) { !it.isBefore(date("2025-11-01")) }
        assertNull(calculator.assess(rules, builder.build()))
    }

    // R-37 거래일에 매매 판단의 시리즈는 넣지 않음

    @Test
    fun `R-37 매매 판단의 시리즈에만 있는 날은 기준 날짜가 되지 않는다`() {
        val builder = dataA()
            .set(MoodTestData.TREASURY_10Y, "2026-11-24", 4.0)
            .set(MoodTestData.POLICY_RATE, "2026-11-24", 3.5)
            .set(MoodTestData.REAL_YIELD, "2026-11-24", 2.0)
        assertEquals(date("2026-11-23"), assess(builder).asOfDate)
    }

    @Test
    fun `R-37 실질금리가 기준 날짜에 없어도 기준 날짜는 그대로이고 실질금리의 날은 그 전 날`() {
        val result = assess(dataA().remove(MoodTestData.REAL_YIELD, "2026-11-23"))
        assertEquals(date("2026-11-23"), result.asOfDate)
        assertEquals(date("2026-11-20"), result.valuation.rateBurden.realYieldDate)
    }
}
