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
    )
}
