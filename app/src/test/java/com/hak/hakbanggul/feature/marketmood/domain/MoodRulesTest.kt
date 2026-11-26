package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodRulesTest {

    private val rules = DefaultMoodRules.rules

    // R-71

    @Test
    fun `R-71 기본 규칙의 매매 판단 값`() {
        val v = rules.valuation
        assertEquals(IndicatorSeries(TWELVE_DATA, "SPY", SeriesKind.DAILY_CLOSE), v.priceSeries)
        assertEquals(200, v.shortAverageDays)
        assertEquals(756, v.longAverageDays)
        assertEquals(2.0, v.shortWeight, 0.0)
        assertEquals(1.0, v.longWeight, 0.0)
        assertEquals(1260, v.positionWindow)
        assertEquals(20, v.smoothingDays)
        assertEquals(30, v.lowBoundary)
        assertEquals(70, v.highBoundary)
        assertEquals(5, v.buffer)
        assertEquals(63, v.threeMonthDays)
        assertEquals(9, v.priceFetchYears)
        assertEquals(IndicatorSeries(FRED, "DFII10", SeriesKind.DAILY_VALUE), v.realYieldSeries)
        assertEquals(6, v.realYieldFetchYears)
        assertEquals(IndicatorSeries(FRED, "DGS10", SeriesKind.DAILY_VALUE), v.treasury10ySeries)
        assertEquals(IndicatorSeries(FRED, "DFF", SeriesKind.DAILY_VALUE), v.policyRateSeries)
        assertEquals(IndicatorSeries(FRED, "CPIAUCSL", SeriesKind.MONTHLY_VALUE), v.consumerPriceSeries)
        assertEquals(365, v.yearAgoDays)
        assertEquals(0.02, v.inflationTarget, 0.0)
    }

    // R-41 시리즈마다의 받는 기간

    @Test
    fun `R-41 기본 규칙의 시리즈마다 받는 기간은 열 개이고 SPY는 한 번만 나온다`() {
        val result = rules.fetchYearsBySeries().entries.map { it.key.seriesId to it.value }
        assertEquals(
            listOf(
                "SPY" to 9,
                "QQQ" to 2,
                "XLK" to 2,
                "SMH" to 2,
                "T10Y2Y" to 2,
                "BAMLH0A0HYM2" to 2,
                "DFII10" to 6,
                "DGS10" to 2,
                "DFF" to 2,
                "CPIAUCSL" to 2,
            ),
            result,
        )
    }

    @Test
    fun `R-41 대상의 받는 기간이 장세 항목보다 짧으면 긴 쪽을 쓴다`() {
        val changed = rules.copy(valuation = rules.valuation.copy(priceFetchYears = 1))
        val result = changed.fetchYearsBySeries().entries.associate { it.key.seriesId to it.value }
        assertEquals(2, result["SPY"])
        assertEquals(10, result.size)
    }
}
