package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.feature.marketmood.domain.PeriodUnit.MONTHS
import com.hak.hakbanggul.feature.marketmood.domain.PeriodUnit.YEARS
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodRulesTest {

    private val rules = DefaultMoodRules.rules

    private fun close(ticker: String) = IndicatorSeries(TWELVE_DATA, ticker, SeriesKind.DAILY_CLOSE)

    // R-30

    @Test
    fun `R-30 전체 시장과 섹터 열셋이 그 순서로 있고 모두 TWELVE_DATA, DAILY_CLOSE`() {
        assertEquals(MarketTarget("broad_market", close("SPY")), rules.broadMarket)
        assertEquals(
            listOf(
                "nasdaq_100" to "QQQ",
                "semiconductors" to "SMH",
                "technology" to "XLK",
                "financials" to "XLF",
                "energy" to "XLE",
                "health_care" to "XLV",
                "consumer_discretionary" to "XLY",
                "consumer_staples" to "XLP",
                "industrials" to "XLI",
                "materials" to "XLB",
                "utilities" to "XLU",
                "real_estate" to "XLRE",
                "communication_services" to "XLC",
            ).map { (id, ticker) -> MarketTarget(id, close(ticker)) },
            rules.sectors,
        )
        assertEquals(listOf(rules.broadMarket) + rules.sectors, rules.targets())
        assertEquals(rules.sectors[1], rules.target("semiconductors"))
        assertEquals(null, rules.target("unknown"))
    }

    // R-100

    @Test
    fun `R-100 장세의 묶음, 항목, 입력, 점수 기준, 비중, 경계, 유지 일수`() {
        val regime = rules.regime
        val expected = listOf(
            IndicatorGroupRule(
                "stocks",
                0.7,
                listOf(
                    IndicatorRule("above_average", IndicatorInput.TargetClose, ScoringRule.AboveAverage(0.02)),
                    IndicatorRule("from_high", IndicatorInput.TargetClose, ScoringRule.FromHigh),
                    IndicatorRule("average_slope", IndicatorInput.TargetClose, ScoringRule.AverageSlope),
                ),
            ),
            IndicatorGroupRule(
                "bond_signals",
                0.3,
                listOf(
                    IndicatorRule(
                        "yield_curve",
                        IndicatorInput.Series(IndicatorSeries(FRED, "T10Y2Y", SeriesKind.DAILY_VALUE)),
                        ScoringRule.HighIsGood(0.0, 0.5),
                    ),
                    IndicatorRule(
                        "high_yield_spread",
                        IndicatorInput.Series(IndicatorSeries(FRED, "BAMLH0A0HYM2", SeriesKind.DAILY_VALUE)),
                        ScoringRule.LowIsGood(4.0, 6.0),
                    ),
                ),
            ),
        )
        assertEquals(expected, regime.groups)
        assertEquals(1.0, regime.groups.sumOf { it.weight }, 1e-9)
        assertEquals(0.5, regime.upperBoundary, 0.0)
        assertEquals(-0.5, regime.lowerBoundary, 0.0)
        assertEquals(3, regime.holdDays)
    }

    // R-102

    @Test
    fun `R-102 장세와 매매 판단의 기간과 메인`() {
        assertEquals(
            listOf(
                RegimePeriod("3m", PeriodLength(3, MONTHS), 50, 63, 0.05, 0.10, 20),
                RegimePeriod("6m", PeriodLength(6, MONTHS), 100, 126, 0.07, 0.15, 40),
                RegimePeriod("1y", PeriodLength(1, YEARS), 200, 252, 0.10, 0.20, 80),
            ),
            rules.regime.periods,
        )
        assertEquals("3m", rules.regime.mainPeriod().id)
        assertEquals(
            listOf(
                ValuationPeriod("1y", PeriodLength(1, YEARS), 252),
                ValuationPeriod("2y", PeriodLength(2, YEARS), 504),
                ValuationPeriod("3y", PeriodLength(3, YEARS), 756),
                ValuationPeriod("5y", PeriodLength(5, YEARS), 1260),
            ),
            rules.valuation.periods,
        )
        assertEquals("2y", rules.valuation.mainPeriod().id)
    }

    // R-71

    @Test
    fun `R-71 기본 규칙의 매매 판단 값`() {
        val v = rules.valuation
        assertEquals(200, v.shortAverageDays)
        assertEquals(756, v.longAverageDays)
        assertEquals(PeriodLength(3, YEARS), v.longAverageLength)
        assertEquals(2.0, v.shortWeight, 0.0)
        assertEquals(1.0, v.longWeight, 0.0)
        assertEquals(20, v.smoothingDays)
        assertEquals(30, v.lowBoundary)
        assertEquals(70, v.highBoundary)
        assertEquals(5, v.buffer)
        assertEquals(63, v.threeMonthDays)
        assertEquals(PeriodLength(3, MONTHS), v.threeMonthLength)
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
    fun `R-41 기본 규칙의 시리즈마다 받는 기간은 스무 개이고 이 순서다`() {
        val result = rules.fetchYearsBySeries().entries.map { it.key.seriesId to it.value }
        val closes = listOf("SPY", "QQQ", "SMH", "XLK", "XLF", "XLE", "XLV", "XLY", "XLP", "XLI", "XLB", "XLU", "XLRE", "XLC")
        assertEquals(
            closes.map { it to 9 } + listOf(
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
    fun `R-41 대상의 받는 기간이 1이면 종가 열넷은 모두 1이다`() {
        val changed = rules.copy(valuation = rules.valuation.copy(priceFetchYears = 1))
        val result = changed.fetchYearsBySeries().entries.associate { it.key.seriesId to it.value }
        assertEquals(20, result.size)
        changed.targets().forEach { assertEquals(it.id, 1, result[it.closeSeries.seriesId]) }
    }
}
