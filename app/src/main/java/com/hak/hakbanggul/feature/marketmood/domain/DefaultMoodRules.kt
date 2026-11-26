package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataSource

// 용어: 기본 규칙
// 항목을 더할 때는 여기에 한 줄, 화면의 IndicatorLabels와 strings.xml에 이름을 더한다.
// 대상을 더할 때는 여기에 한 줄, 화면의 TargetLabels와 strings.xml에 이름을 더한다.
object DefaultMoodRules {

    private fun target(id: String, ticker: String) =
        MarketTarget(id, IndicatorSeries(DataSource.TWELVE_DATA, ticker, SeriesKind.DAILY_CLOSE))

    val rules = MoodRules(
        broadMarket = target("broad_market", "SPY"),
        sectors = listOf(
            target("nasdaq_100", "QQQ"),
            target("semiconductors", "SMH"),
            target("technology", "XLK"),
            target("financials", "XLF"),
            target("energy", "XLE"),
            target("health_care", "XLV"),
            target("consumer_discretionary", "XLY"),
            target("consumer_staples", "XLP"),
            target("industrials", "XLI"),
            target("materials", "XLB"),
            target("utilities", "XLU"),
            target("real_estate", "XLRE"),
            target("communication_services", "XLC"),
        ),
        regime = RegimeRules(
            groups = listOf(
                IndicatorGroupRule(
                    id = "stocks",
                    weight = 0.7,
                    indicators = listOf(
                        IndicatorRule("above_average", IndicatorInput.TargetClose, ScoringRule.AboveAverage(averageBand = 0.02)),
                        IndicatorRule("from_high", IndicatorInput.TargetClose, ScoringRule.FromHigh),
                        IndicatorRule("average_slope", IndicatorInput.TargetClose, ScoringRule.AverageSlope),
                    ),
                ),
                IndicatorGroupRule(
                    id = "bond_signals",
                    weight = 0.3,
                    indicators = listOf(
                        IndicatorRule(
                            id = "yield_curve",
                            input = IndicatorInput.Series(IndicatorSeries(DataSource.FRED, "T10Y2Y", SeriesKind.DAILY_VALUE)),
                            scoring = ScoringRule.HighIsGood(zeroFrom = 0.0, plusFrom = 0.5),
                        ),
                        IndicatorRule(
                            id = "high_yield_spread",
                            input = IndicatorInput.Series(IndicatorSeries(DataSource.FRED, "BAMLH0A0HYM2", SeriesKind.DAILY_VALUE)),
                            scoring = ScoringRule.LowIsGood(plusBelow = 4.0, minusAbove = 6.0),
                        ),
                    ),
                ),
            ),
            upperBoundary = 0.5,
            lowerBoundary = -0.5,
            holdDays = 3,
            periods = listOf(
                RegimePeriod("3m", PeriodLength(3, PeriodUnit.MONTHS), 50, 63, 0.05, 0.10, 20),
                RegimePeriod("6m", PeriodLength(6, PeriodUnit.MONTHS), 100, 126, 0.07, 0.15, 40),
                RegimePeriod("1y", PeriodLength(1, PeriodUnit.YEARS), 200, 252, 0.10, 0.20, 80),
            ),
            mainPeriodId = "3m",
        ),
        valuation = ValuationRules(
            shortAverageDays = 200,
            longAverageDays = 756,
            longAverageLength = PeriodLength(3, PeriodUnit.YEARS),
            shortWeight = 2.0,
            longWeight = 1.0,
            smoothingDays = 20,
            lowBoundary = 30,
            highBoundary = 70,
            buffer = 5,
            threeMonthDays = 63,
            threeMonthLength = PeriodLength(3, PeriodUnit.MONTHS),
            periods = listOf(
                ValuationPeriod("1y", PeriodLength(1, PeriodUnit.YEARS), 252),
                ValuationPeriod("2y", PeriodLength(2, PeriodUnit.YEARS), 504),
                ValuationPeriod("3y", PeriodLength(3, PeriodUnit.YEARS), 756),
                ValuationPeriod("5y", PeriodLength(5, PeriodUnit.YEARS), 1260),
            ),
            mainPeriodId = "2y",
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
