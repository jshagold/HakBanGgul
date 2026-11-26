package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataSource

// 용어: 기본 규칙
// 항목을 더할 때는 여기에 한 줄, 화면의 IndicatorLabels와 strings.xml에 이름을 더한다.
object DefaultMoodRules {

    private val priceTrend = ScoringRule.PriceTrend(averageDays = 200, shallowDrawdown = 0.10, deepDrawdown = 0.20)

    private fun stock(id: String, ticker: String) = IndicatorRule(
        id = id,
        series = IndicatorSeries(DataSource.TWELVE_DATA, ticker, SeriesKind.DAILY_CLOSE),
        scoring = priceTrend,
    )

    val rules = MoodRules(
        groups = listOf(
            IndicatorGroupRule(
                id = "stocks",
                weight = 0.7,
                indicators = listOf(
                    stock("broad_market", "SPY"),
                    stock("nasdaq_100", "QQQ"),
                    stock("technology", "XLK"),
                    stock("semiconductors", "SMH"),
                ),
            ),
            IndicatorGroupRule(
                id = "bond_signals",
                weight = 0.3,
                indicators = listOf(
                    IndicatorRule(
                        id = "yield_curve",
                        series = IndicatorSeries(DataSource.FRED, "T10Y2Y", SeriesKind.DAILY_VALUE),
                        scoring = ScoringRule.HighIsGood(zeroFrom = 0.0, plusFrom = 0.5),
                    ),
                    IndicatorRule(
                        id = "high_yield_spread",
                        series = IndicatorSeries(DataSource.FRED, "BAMLH0A0HYM2", SeriesKind.DAILY_VALUE),
                        scoring = ScoringRule.LowIsGood(plusBelow = 4.0, minusAbove = 6.0),
                    ),
                ),
            ),
        ),
        upperBoundary = 0.5,
        lowerBoundary = -0.5,
        holdDays = 3,
        valuation = ValuationRules(
            priceSeries = IndicatorSeries(DataSource.TWELVE_DATA, "SPY", SeriesKind.DAILY_CLOSE),
            shortAverageDays = 200,
            longAverageDays = 756,
            shortWeight = 2.0,
            longWeight = 1.0,
            positionWindow = 1260,
            smoothingDays = 20,
            lowBoundary = 30,
            highBoundary = 70,
            buffer = 5,
            threeMonthDays = 63,
            priceFetchYears = 9,
            realYieldSeries = IndicatorSeries(DataSource.FRED, "DFII10", SeriesKind.DAILY_VALUE),
            realYieldFetchYears = 6,
            treasury10ySeries = IndicatorSeries(DataSource.FRED, "DGS10", SeriesKind.DAILY_VALUE),
            policyRateSeries = IndicatorSeries(DataSource.FRED, "DFF", SeriesKind.DAILY_VALUE),
            consumerPriceSeries = IndicatorSeries(DataSource.FRED, "CPIAUCSL", SeriesKind.MONTHLY_VALUE),
            yearAgoDays = 365,
            inflationTarget = 0.02,
        ),
        fetchYears = 2,
    )
}
