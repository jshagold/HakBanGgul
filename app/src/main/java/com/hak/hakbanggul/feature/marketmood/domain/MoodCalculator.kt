package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import javax.inject.Inject

// 받아 둔 일별 값과 규칙으로 판정(전체 시장의 메인 장세와 메인 매매 판단)을 내는 순수 계산기.
// 지금 시각을 쓰지 않아 같은 값이면 늘 같은 판정이 난다. 섹터와 다른 기간은 계산하지 않는다.
class MoodCalculator @Inject constructor(
    private val regimeCalculator: RegimeCalculator,
    private val valuationCalculator: ValuationCalculator,
) {

    // values의 키는 시리즈 ID다. 목록의 순서에 기대지 않는다.
    // 전체 시장의 메인 장세나 그 기준 날짜의 메인 매매 판단을 낼 수 없으면 null이다.
    fun assess(rules: MoodRules, values: Map<String, List<DailyValue>>): MoodAssessment? {
        val closes = values[rules.broadMarket.closeSeries.seriesId]
        val regime = regimeCalculator.assess(rules.regime, rules.regime.mainPeriod(), closes, values) ?: return null
        val valuation = valuationCalculator.assess(rules.valuation, closes, values, regime.asOfDate) ?: return null
        return MoodAssessment(
            asOfDate = regime.asOfDate,
            displayedStage = regime.displayedStage,
            computedStage = regime.computedStage,
            compositeScore = regime.compositeScore,
            computedStageStreak = regime.computedStageStreak,
            holdDays = regime.holdDays,
            upperBoundary = regime.upperBoundary,
            lowerBoundary = regime.lowerBoundary,
            groups = regime.groups,
            valuation = valuation,
        )
    }
}
