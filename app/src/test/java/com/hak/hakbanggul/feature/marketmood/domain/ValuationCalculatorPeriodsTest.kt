package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.date
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.valuesForScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

// 기간마다 내는 주가 위치와 금리 부담 (단위 2)
class ValuationCalculatorPeriodsTest {

    private val calculator = ValuationCalculator()
    private val end: LocalDate = date("2026-11-23")

    private fun period(id: String, window: Int) = ValuationPeriod(id, PeriodLength(1, PeriodUnit.YEARS), window)

    // 작은 규칙: 짧은 평균 2, 긴 평균 3, 비중 2 : 1, 평균 일수 1, 3개월 2, 1년 전 1일
    private val small: ValuationRules = DefaultMoodRules.rules.valuation.copy(
        shortAverageDays = 2,
        longAverageDays = 3,
        shortWeight = 2.0,
        longWeight = 1.0,
        smoothingDays = 1,
        threeMonthDays = 2,
        yearAgoDays = 1,
    )

    private fun series(values: List<Double>, endDay: LocalDate = end): List<DailyValue> =
        MoodTestData.weekdaysEndingAt(endDay, values.size).zip(values) { day, value -> DailyValue(day, value) }

    // 금리 부담의 기간과 상관없는 값을 모두 낼 수 있는 기본 값
    private fun base(count: Int = 300): Map<String, List<DailyValue>> {
        val lastMonth = YearMonth.from(end)
        return mapOf(
            "DFII10" to series(List(count) { 2.0 }),
            "DGS10" to series(List(count) { 4.0 }),
            "DFF" to series(List(count) { 3.5 }),
            "CPIAUCSL" to (24 downTo 0).map { DailyValue(lastMonth.minusMonths(it.toLong()).atDay(1), 300.0) },
        )
    }

    // R-72, R-73, R-74

    @Test
    fun `R-72 종가 다섯이면 a는 42이고 b는 맵에 없다`() {
        val closes = series(listOf(10.0, 12.0, 11.0, 13.0, 12.0))
        val result = calculator.assessPricePositions(small, closes, end, listOf(period("a", 3), period("b", 4)))
        assertEquals(42, result.getValue("a").score)
        assertFalse("b" in result)
    }

    @Test
    fun `R-72 종가 일곱이면 a 50, b 33이고 기간과 상관없는 값은 같다`() {
        val closes = series(listOf(10.0, 12.0, 11.0, 13.0, 12.0, 15.0, 14.0))
        val result = calculator.assessPricePositions(small, closes, end, listOf(period("a", 3), period("b", 4)))
        val a = result.getValue("a")
        val b = result.getValue("b")
        assertEquals(50, a.score)
        assertEquals(33, b.score)
        assertEquals(-0.0345, a.shortDeviation, 1e-4)
        assertEquals(14.0 / 14.5 - 1, a.shortDeviation, 1e-12)
        assertEquals(0.0244, a.longDeviation, 1e-4)
        assertEquals(14.0 / 12.0 - 1, a.threeMonthReturn, 1e-12)
        assertEquals(a.shortDeviation, b.shortDeviation, 0.0)
        assertEquals(a.longDeviation, b.longDeviation, 0.0)
        assertEquals(a.drawdown, b.drawdown, 0.0)
        assertEquals(a.threeMonthReturn, b.threeMonthReturn, 0.0)
    }

    // R-75

    @Test
    fun `R-75 평균 일수 2면 a 13이고 b는 없다, 기간과 상관없는 값은 기준 날짜의 것`() {
        val rules = small.copy(smoothingDays = 2)
        val values = base() + ("DFII10" to series(listOf(5.0, 4.0, 3.0, 2.0, 1.0, 1.5)))
        val result = calculator.assessRateBurdens(rules, values, end, listOf(period("a", 5), period("b", 6)))
        val a = result.getValue("a")
        assertEquals(13, a.score)
        assertFalse("b" in result)
        assertEquals(end, a.realYieldDate)
        // 1년 전(1일 앞 = 일요일)이나 그 전의 가장 가까운 날(금요일)의 값 1.0과의 차이
        assertEquals(0.5, a.yearChange, 1e-12)
        assertEquals(DatedValue(4.0, end), a.treasury10y)
        assertEquals(DatedValue(3.5, end), a.policyRate)
        assertEquals(YearMonth.from(end), a.inflationMonth)
        assertEquals(0.0, a.inflationRate, 1e-12)
    }

    // R-77 콘텐츠마다 따로

    @Test
    fun `R-77 a는 표시 LOW, 점수로 정한 단계 MIDDLE이고 b의 단계는 b의 점수로 따로 정한다`() {
        val rules = small
        val scores = valuesForScores(listOf(30, 35))
        val values = base() + ("DFII10" to series(List(200) { 2.0 } + scores))
        val a = period("a", 101)
        val b = period("b", 20)

        val result = calculator.assessRateBurdens(rules, values, end, listOf(a, b))

        assertEquals(35, result.getValue("a").score)
        assertEquals(ValuationStage.LOW, result.getValue("a").displayedStage)
        assertEquals(ValuationStage.MIDDLE, result.getValue("a").scoredStage)
        // b만 넘겨 낸 것과 같다(a의 표시 단계를 따르지 않는다).
        assertEquals(calculator.assessRateBurdens(rules, values, end, listOf(b)).getValue("b"), result.getValue("b"))
    }

    // R-104 낼 수 없음

    @Test
    fun `R-104 종가 넷이면 빈 맵`() {
        val closes = series(listOf(10.0, 12.0, 11.0, 13.0))
        assertTrue(calculator.assessPricePositions(small, closes, end, listOf(period("a", 3))).isEmpty())
    }

    @Test
    fun `R-104 기준 날짜에 종가가 없으면 빈 맵`() {
        val closes = series(listOf(10.0, 12.0, 11.0, 13.0, 12.0, 15.0, 14.0), date("2026-11-20"))
        assertTrue(calculator.assessPricePositions(small, closes, end, listOf(period("a", 3))).isEmpty())
    }

    @Test
    fun `R-104 DGS10이 없으면 금리 부담은 빈 맵`() {
        val values = base() - "DGS10"
        assertTrue(calculator.assessRateBurdens(small, values, end, listOf(period("a", 3))).isEmpty())
    }
}
