package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.HIGH_YIELD
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.STOCKS
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.YIELD_CURVE
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataB
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class MoodCalculatorTest {

    private val calculator = MoodCalculator()
    private val rules = DefaultMoodRules.rules

    private fun assess(builder: MoodTestData.Builder): MoodAssessment =
        requireNotNull(calculator.assess(rules, builder.build())) { "판정을 낼 수 있어야 한다" }

    private fun MoodAssessment.indicator(id: String): IndicatorAssessment =
        groups.flatMap { it.indicators }.single { it.id == id }

    private fun MoodAssessment.group(id: String): GroupAssessment = groups.single { it.id == id }

    private fun MoodAssessment.price(id: String) = indicator(id).evidence as IndicatorEvidence.PriceEvidence

    private fun MoodAssessment.value(id: String) = indicator(id).evidence as IndicatorEvidence.ValueEvidence

    private fun score(builder: MoodTestData.Builder, id: String): Double = assess(builder).indicator(id).score

    // R-30

    @Test
    fun `R-30 기본 규칙의 묶음, 항목, 시리즈, 점수 기준, 비중, 경계, 유지 일수`() {
        val trend = ScoringRule.PriceTrend(200, 0.10, 0.20)
        fun stock(id: String, ticker: String) =
            IndicatorRule(id, IndicatorSeries(DataSource.TWELVE_DATA, ticker, SeriesKind.DAILY_CLOSE), trend)

        val expected = listOf(
            IndicatorGroupRule(
                "stocks",
                0.7,
                listOf(
                    stock("broad_market", "SPY"),
                    stock("nasdaq_100", "QQQ"),
                    stock("technology", "XLK"),
                    stock("semiconductors", "SMH"),
                ),
            ),
            IndicatorGroupRule(
                "bond_signals",
                0.3,
                listOf(
                    IndicatorRule(
                        "yield_curve",
                        IndicatorSeries(DataSource.FRED, "T10Y2Y", SeriesKind.DAILY_VALUE),
                        ScoringRule.HighIsGood(0.0, 0.5),
                    ),
                    IndicatorRule(
                        "high_yield_spread",
                        IndicatorSeries(DataSource.FRED, "BAMLH0A0HYM2", SeriesKind.DAILY_VALUE),
                        ScoringRule.LowIsGood(4.0, 6.0),
                    ),
                ),
            ),
        )
        assertEquals(expected, rules.groups)
        assertEquals(1.0, rules.groups.sumOf { it.weight }, 1e-9)
        assertEquals(0.5, rules.upperBoundary, 0.0)
        assertEquals(-0.5, rules.lowerBoundary, 0.0)
        assertEquals(3, rules.holdDays)
    }

    // R-38, R-37

    @Test
    fun `R-38 데이터 A의 기준 날짜는 2026-11-23`() {
        assertEquals(date("2026-11-23"), assess(dataA()).asOfDate)
    }

    @Test
    fun `R-37 QQQ의 2026-11-23이 없으면 기준 날짜는 2026-11-20`() {
        assertEquals(date("2026-11-20"), assess(dataA().remove("QQQ", "2026-11-23")).asOfDate)
    }

    // R-31

    @Test
    fun `R-31 종가가 평균과 같으면 평균 이상이라 broad_market은 +1`() {
        assertEquals(1.0, score(dataA(), "broad_market"), 0.0)
    }

    @Test
    fun `R-31 SPY 2026-11-23이 99_9면 추세 -1 낙폭 +1로 0`() {
        assertEquals(0.0, score(dataA().set("SPY", "2026-11-23", 99.9), "broad_market"), 0.0)
    }

    @Test
    fun `R-31 평균은 그날을 포함한 최근 200개 종가`() {
        val builder = dataA()
        MoodTestData.weekdays(MoodTestData.START, date("2026-02-16")).forEach { builder.set("SPY", it, 50.0) }

        val result = assess(builder)

        assertEquals(0.0, result.price("broad_market").aboveAverageRatio, 1e-12)
        assertEquals(1.0, result.indicator("broad_market").score, 0.0)
    }

    // R-32

    @Test
    fun `R-32 하락률 10%는 얕은 기준 이하라 낙폭 +1, 추세 -1로 0`() {
        assertEquals(0.0, score(dataA().set("SPY", "2026-11-23", 90.0), "broad_market"), 0.0)
    }

    @Test
    fun `R-32 89_9면 -0_5`() {
        assertEquals(-0.5, score(dataA().set("SPY", "2026-11-23", 89.9), "broad_market"), 0.0)
    }

    @Test
    fun `R-32 하락률 20%는 깊은 기준 이상이라 -1`() {
        assertEquals(-1.0, score(dataA().set("SPY", "2026-11-23", 80.0), "broad_market"), 0.0)
    }

    @Test
    fun `R-32 80_1이면 -0_5`() {
        assertEquals(-0.5, score(dataA().set("SPY", "2026-11-23", 80.1), "broad_market"), 0.0)
    }

    @Test
    fun `R-32 1년 전 같은 날은 최고 종가에 들어간다`() {
        val builder = dataA().truncateAllAfter("2026-11-20").set("SPY", "2025-11-20", 200.0)
        assertEquals(-0.5, assess(builder).price("broad_market").drawdown, 1e-12)
    }

    @Test
    fun `R-32 1년보다 앞의 날은 최고 종가에 들어가지 않는다`() {
        val builder = dataA().truncateAllAfter("2026-11-20").set("SPY", "2025-11-19", 200.0)
        assertEquals(0.0, assess(builder).price("broad_market").drawdown, 1e-12)
    }

    // R-33

    @Test
    fun `R-33 데이터 B의 technology는 추세 +1 낙폭 0으로 +0_5`() {
        assertEquals(0.5, score(dataB(), "technology"), 0.0)
    }

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
        assertEquals(0.375, result.group("stocks").score, 1e-12)
        assertEquals(0.5, result.group("bond_signals").score, 1e-12)
        assertEquals(0.4125, result.compositeScore, 1e-12)
    }

    // R-35

    @Test
    fun `R-35 경계에 꼭 걸린 0_5는 불장`() {
        val result = assess(dataA().setStocks("2026-01-05", 120.0).set(YIELD_CURVE, "2026-11-23", 0.29))
        assertEquals(0.5, result.compositeScore, 0.0)
        assertEquals(MarketStage.BULL, result.computedStage)
    }

    @Test
    fun `R-35 데이터 B는 중립`() {
        assertEquals(MarketStage.NEUTRAL, assess(dataB()).computedStage)
    }

    @Test
    fun `R-35 현재 상태 0은 중립`() {
        val result = assess(
            dataA().setStocks("2026-11-23", 99.9).set(YIELD_CURVE, "2026-11-23", 0.29).set(HIGH_YIELD, "2026-11-23", 5.00),
        )
        assertEquals(0.0, result.compositeScore, 0.0)
        assertEquals(MarketStage.NEUTRAL, result.computedStage)
    }

    @Test
    fun `R-35 현재 상태 -0_5는 약세`() {
        val result = assess(
            dataA().setStocks("2026-11-23", 89.9).set(YIELD_CURVE, "2026-11-23", -0.01).set(HIGH_YIELD, "2026-11-23", 5.00),
        )
        assertEquals(-0.5, result.compositeScore, 0.0)
        assertEquals(MarketStage.WEAK, result.computedStage)
    }

    @Test
    fun `R-35 현재 상태 -0_85는 물장`() {
        val result = assess(
            dataA().setStocks("2026-11-23", 80.0).set(YIELD_CURVE, "2026-11-23", -0.01).set(HIGH_YIELD, "2026-11-23", 5.00),
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
        val result = assess(
            dataA().badDay("2026-11-18", "2026-11-19", "2026-11-20", "2026-11-23").setStocks("2026-11-20", 80.0),
        )
        assertEquals(MarketStage.BULL, result.displayedStage)
        assertEquals(MarketStage.NEUTRAL, result.computedStage)
    }

    @Test
    fun `R-36 첫 거래일의 계산 단계가 표시 단계가 된다`() {
        val result = assess(dataA().truncateAllAfter("2025-11-26").badDay("2025-11-25"))
        assertEquals(MarketStage.NEUTRAL, result.displayedStage)
        assertEquals(MarketStage.BULL, result.computedStage)
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
            dataA().badDay("2026-11-18", "2026-11-19", "2026-11-20", "2026-11-23").setStocks("2026-11-20", 80.0),
        )
        assertEquals(1, result.computedStageStreak)
    }

    // R-11

    @Test
    fun `R-11 SPY 2026-11-23이 104_1일 때의 두 값`() {
        val evidence = assess(dataA().set("SPY", "2026-11-23", 104.1)).price("broad_market")
        assertEquals((104.1 - 100.0205) / 100.0205, evidence.aboveAverageRatio, 1e-12)
        assertEquals(0.0, evidence.drawdown, 1e-12)
    }

    @Test
    fun `R-11 데이터 B의 technology와 semiconductors의 두 값`() {
        val result = assess(dataB())
        assertEquals(0.0, result.price("technology").aboveAverageRatio, 1e-12)
        assertEquals((100.0 - 120.0) / 120.0, result.price("technology").drawdown, 1e-12)
        assertEquals((80.0 - 99.9) / 99.9, result.price("semiconductors").aboveAverageRatio, 1e-12)
        assertEquals(-0.2, result.price("semiconductors").drawdown, 1e-12)
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
    fun `R-40 XLK 시리즈가 없으면 null`() {
        assertNull(calculator.assess(rules, dataA().removeSeries("XLK").build()))
    }

    @Test
    fun `R-40 빈 값이면 null`() {
        assertNull(calculator.assess(rules, emptyMap()))
    }

    @Test
    fun `R-40 종가가 200개에 못 미치면 null`() {
        val builder = dataA()
        STOCKS.forEach { id -> builder.keepOnly(id) { it.dayOfWeek == DayOfWeek.MONDAY } }
        assertNull(calculator.assess(rules, builder.build()))
    }
}
