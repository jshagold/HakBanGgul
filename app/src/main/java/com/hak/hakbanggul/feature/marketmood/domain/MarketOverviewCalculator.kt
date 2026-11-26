package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import javax.inject.Inject

// 받아 둔 값과 규칙으로 한 벌(모든 대상과 기간의 콘텐츠)을 내는 순수 계산기.
// 판정(전체 시장의 메인)은 MoodCalculator로 내고, 나머지 칸도 같은 계산기로 내어 화면과 위젯의 값이 어긋나지 않는다.
class MarketOverviewCalculator @Inject constructor(
    private val moodCalculator: MoodCalculator,
    private val regimeCalculator: RegimeCalculator,
    private val valuationCalculator: ValuationCalculator,
) {

    // 판정을 낼 수 없으면 null이다. 섹터 하나나 기간 하나를 낼 수 없으면 그 칸만 null이다.
    fun assess(rules: MoodRules, values: Map<String, List<DailyValue>>): MarketOverview? {
        val mood = moodCalculator.assess(rules, values) ?: return null
        val rates = valuationCalculator.assessRateBurdens(rules.valuation, values, mood.asOfDate, rules.valuation.periods)
        return MarketOverview(
            rules = rules,
            mood = mood,
            broadMarket = targetOverview(rules, rules.broadMarket, values),
            sectors = rules.sectors.map { targetOverview(rules, it, values) },
            rateBurdens = rules.valuation.periods.map {
                PeriodContent(it.id, it.length, it.id == rules.valuation.mainPeriodId, rates[it.id])
            },
        )
    }

    private fun targetOverview(rules: MoodRules, target: MarketTarget, values: Map<String, List<DailyValue>>): TargetOverview {
        val closes = values[target.closeSeries.seriesId]
        val asOfDate = closes?.maxOfOrNull { it.date }
        val regimes = rules.regime.periods.map { period ->
            PeriodContent(
                periodId = period.id,
                length = period.length,
                isMain = period.id == rules.regime.mainPeriodId,
                value = regimeCalculator.assess(rules.regime, period, closes, values),
            )
        }
        val prices = if (asOfDate == null) {
            emptyMap()
        } else {
            valuationCalculator.assessPricePositions(rules.valuation, closes, asOfDate, rules.valuation.periods)
        }
        return TargetOverview(
            target = target,
            asOfDate = asOfDate,
            regimes = regimes,
            pricePositions = rules.valuation.periods.map {
                PeriodContent(it.id, it.length, it.id == rules.valuation.mainPeriodId, prices[it.id])
            },
        )
    }
}
