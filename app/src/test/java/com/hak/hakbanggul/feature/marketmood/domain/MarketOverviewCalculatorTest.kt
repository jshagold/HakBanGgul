package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.date
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.withWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketOverviewCalculatorTest {

    private val moodCalculator = MoodCalculator(RegimeCalculator(), ValuationCalculator())
    private val calculator = MarketOverviewCalculator(moodCalculator, RegimeCalculator(), ValuationCalculator())
    private val rules = MoodTestData.RULES
    private val day = date("2026-11-23")

    private fun assess(builder: MoodTestData.Builder, rules: MoodRules = this.rules): MarketOverview =
        requireNotNull(calculator.assess(rules, builder.build())) { "한 벌을 낼 수 있어야 한다" }

    private fun MarketOverview.all() = listOf(broadMarket) + sectors

    private fun assertFullTarget(target: TargetOverview) {
        val id = target.target.id
        assertEquals(id, day, target.asOfDate)
        assertEquals(id, listOf("3m", "6m", "1y"), target.regimes.map { it.periodId })
        assertEquals(id, listOf(true, false, false), target.regimes.map { it.isMain })
        target.regimes.forEach {
            val value = requireNotNull(it.value) { "$id ${it.periodId}" }
            assertEquals(0.766667, value.compositeScore, 0.0)
            assertEquals(MarketStage.BULL, value.displayedStage)
        }
        assertEquals(id, listOf("1y", "2y", "3y", "5y"), target.pricePositions.map { it.periodId })
        assertEquals(id, listOf(false, true, false, false), target.pricePositions.map { it.isMain })
        target.pricePositions.forEach {
            val value = requireNotNull(it.value) { "$id ${it.periodId}" }
            assertEquals(50, value.score)
            assertEquals(ValuationStage.MIDDLE, value.displayedStage)
        }
        assertTrue(target.isMainAvailable())
    }

    // R-102, R-40

    @Test
    fun `R-102 데이터 A면 모든 대상과 기간을 낸다`() {
        val values = dataA().build()
        val result = requireNotNull(calculator.assess(rules, values))

        assertEquals(moodCalculator.assess(rules, values), result.mood)
        assertEquals(rules, result.rules)
        assertEquals(listOf("broad_market", "nasdaq_100", "semiconductors", "technology"), result.all().map { it.target.id })
        assertEquals(listOf("SPY", "QQQ", "SMH", "XLK"), result.all().map { it.target.closeSeries.seriesId })
        result.all().forEach { assertFullTarget(it) }

        assertEquals(listOf("1y", "2y", "3y", "5y"), result.rateBurdens.map { it.periodId })
        assertEquals(listOf(false, true, false, false), result.rateBurdens.map { it.isMain })
        result.rateBurdens.forEach {
            val value = requireNotNull(it.value)
            assertEquals(50, value.score)
            assertEquals(ValuationStage.MIDDLE, value.displayedStage)
            assertEquals(day, value.realYieldDate)
        }
    }

    @Test
    fun `R-102 전체 시장의 메인 장세와 주가 위치, 메인 금리 부담은 판정과 같은 값이다`() {
        val result = assess(MoodTestData.dataB())
        val regime = requireNotNull(result.broadMarket.mainRegime())
        val mood = result.mood
        assertEquals(
            RegimeAssessment(
                mood.asOfDate, mood.displayedStage, mood.computedStage, mood.compositeScore,
                mood.computedStageStreak, mood.holdDays, mood.upperBoundary, mood.lowerBoundary, mood.groups,
            ),
            regime,
        )
        assertEquals(mood.valuation.pricePosition, result.broadMarket.mainPricePosition())
        assertEquals(mood.valuation.rateBurden, result.rateBurdens.single { it.isMain }.value)
    }

    @Test
    fun `R-102 target으로 대상을 찾는다`() {
        val result = assess(dataA())
        assertEquals("SMH", result.target("semiconductors")?.target?.closeSeries?.seriesId)
        assertEquals(result.broadMarket, result.target("broad_market"))
        assertNull(result.target("unknown"))
    }

    // R-104, R-105 일부를 낼 수 없음

    @Test
    fun `R-104 SMH를 빼면 SMH 칸은 모두 null이고 나머지는 그대로`() {
        val result = assess(dataA().removeSeries("SMH"))
        val smh = requireNotNull(result.target("semiconductors"))
        assertNull(smh.asOfDate)
        assertTrue(smh.regimes.all { it.value == null })
        assertTrue(smh.pricePositions.all { it.value == null })
        assertFalse(smh.isMainAvailable())
        result.all().filter { it.target.id != "semiconductors" }.forEach { assertFullTarget(it) }
    }

    @Test
    fun `R-104 QQQ를 마지막 200평일만 남기면 1y 장세만 null`() {
        val keep = MoodTestData.weekdaysEndingAt(MoodTestData.END, 200).toSet()
        val qqq = requireNotNull(assess(dataA().keepOnly("QQQ") { it in keep }).target("nasdaq_100"))
        assertEquals(listOf(true, true, false), qqq.regimes.map { it.value != null })
        assertTrue(qqq.pricePositions.all { it.value != null })
        assertTrue(qqq.isMainAvailable())
    }

    @Test
    fun `R-105 QQQ를 마지막 60평일만 남기면 메인 장세가 없어 isMainAvailable 거짓`() {
        val keep = MoodTestData.weekdaysEndingAt(MoodTestData.END, 60).toSet()
        val qqq = requireNotNull(assess(dataA().keepOnly("QQQ") { it in keep }).target("nasdaq_100"))
        assertNull(qqq.mainRegime())
        assertFalse(qqq.isMainAvailable())
    }

    @Test
    fun `R-104 5y 위치 기간이 600이면 모든 대상의 5y 주가 위치와 5y 금리 부담만 null`() {
        val changed = rules.copy(valuation = rules.valuation.withWindow("5y", 600))
        val result = assess(dataA(), changed)
        val base = assess(dataA())
        assertEquals(base.mood, result.mood)
        result.all().forEach { target ->
            assertEquals(listOf(true, true, true, false), target.pricePositions.map { it.value != null })
            assertEquals(base.target(target.target.id)?.regimes, target.regimes)
            assertEquals(base.target(target.target.id)?.pricePositions?.take(3), target.pricePositions.take(3))
        }
        assertEquals(listOf(true, true, true, false), result.rateBurdens.map { it.value != null })
        assertEquals(base.rateBurdens.take(3), result.rateBurdens.take(3))
    }

    @Test
    fun `R-40 SPY를 빼면 null`() {
        assertNull(calculator.assess(rules, dataA().removeSeries("SPY").build()))
    }

    // R-38 대상마다의 기준 날짜

    @Test
    fun `R-38 QQQ의 2026-11-23을 빼면 QQQ만 2026-11-20`() {
        val result = assess(dataA().remove("QQQ", "2026-11-23"))
        val qqq = requireNotNull(result.target("nasdaq_100"))
        assertEquals(date("2026-11-20"), qqq.asOfDate)
        assertEquals(date("2026-11-20"), qqq.mainRegime()?.asOfDate)
        assertNotNull(qqq.mainPricePosition())
        assertEquals(day, result.broadMarket.asOfDate)
        assertEquals(day, result.mood.asOfDate)
    }

    @Test
    fun `R-38 SPY의 2026-11-23을 빼면 판정, 전체 시장, 금리 부담은 2026-11-20이고 QQQ는 2026-11-23`() {
        val result = assess(dataA().remove("SPY", "2026-11-23"))
        val friday = date("2026-11-20")
        assertEquals(friday, result.mood.asOfDate)
        assertEquals(friday, result.broadMarket.asOfDate)
        result.rateBurdens.forEach { assertEquals(friday, it.value?.realYieldDate) }
        assertEquals(day, result.target("nasdaq_100")?.asOfDate)
    }
}
