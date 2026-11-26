package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

// 한 대상, 한 기간의 장세를 내는 순수 계산기.
// 항목이 무엇인지 모르고 규칙의 목록만 돈다. 지금 시각을 쓰지 않아 같은 값이면 늘 같은 장세가 난다.
class RegimeCalculator @Inject constructor() {

    // closes: 그 대상의 종가. values: 시리즈 ID마다의 받아 둔 값(Series 항목이 쓴다).
    // 거래일은 그 대상의 종가가 있는 날이고, 기준 날짜는 그 가장 최근 거래일이다. 기준 날짜를 계산할 수 없으면 null이다.
    fun assess(
        rules: RegimeRules,
        period: RegimePeriod,
        closes: List<DailyValue>?,
        values: Map<String, List<DailyValue>>,
    ): RegimeAssessment? {
        if (closes.isNullOrEmpty()) return null
        if (rules.groups.isEmpty() || rules.groups.any { it.indicators.isEmpty() }) return null
        val close = DailySeries.of(closes)

        val series = HashMap<String, DailySeries>()
        for (indicator in rules.groups.flatMap { it.indicators }) {
            val input = indicator.input as? IndicatorInput.Series ?: continue
            val id = input.series.seriesId
            if (id in series) continue
            val list = values[id]
            if (list.isNullOrEmpty()) return null
            series[id] = DailySeries.of(list)
        }

        // 용어: 거래일
        val tradingDays = close.dates.indices
        val asOfIndex = tradingDays.last
        val scorable = tradingDays.filter { canScore(rules, period, close, series, it) }
        if (scorable.isEmpty() || scorable.last() != asOfIndex) return null

        // 첫 계산할 수 있는 날부터 기준 날짜까지 날마다 계산 단계를 내어 표시 단계를 구한다.
        val computedStages = scorable.map { index -> stageOf(rules, compositeOf(assessGroups(rules, period, close, series, index))) }
        val displayedStage = displayedStageOf(computedStages, rules.holdDays)
        val computedStage = computedStages.last()
        val streak = computedStages.asReversed().takeWhile { it == computedStage }.size

        val groups = assessGroups(rules, period, close, series, asOfIndex)
        return RegimeAssessment(
            asOfDate = close.dates[asOfIndex],
            displayedStage = displayedStage,
            computedStage = computedStage,
            compositeScore = compositeOf(groups),
            computedStageStreak = streak,
            holdDays = rules.holdDays,
            upperBoundary = rules.upperBoundary,
            lowerBoundary = rules.lowerBoundary,
            groups = groups,
        )
    }

    // 계산할 수 있는 거래일: 그날까지의 종가가 평균 일수 + 기울기 일수개 이상이고 고점 일수개 이상이며,
    // 시리즈 항목마다 그날이나 그 전의 값이 있는 날
    private fun canScore(
        rules: RegimeRules,
        period: RegimePeriod,
        close: DailySeries,
        series: Map<String, DailySeries>,
        index: Int,
    ): Boolean {
        val count = index + 1
        if (count < period.averageDays + period.slopeDays || count < period.highDays) return false
        val day = close.dates[index]
        return rules.groups.all { group ->
            group.indicators.all { indicator ->
                when (val input = indicator.input) {
                    IndicatorInput.TargetClose -> true
                    is IndicatorInput.Series -> series.getValue(input.series.seriesId).floorIndex(day) >= 0
                }
            }
        }
    }

    // 표시 단계와 다른 계산 단계가 holdDays만큼 연속으로 같은 단계로 나오면 그 마지막 날부터 바뀐다.
    // 다른 단계가 끼거나 표시 단계와 같은 날이 끼면 연속이 끊긴다.
    private fun displayedStageOf(computedStages: List<MarketStage>, holdDays: Int): MarketStage {
        var displayed = computedStages.first()
        var pending: MarketStage? = null
        var pendingCount = 0
        for (stage in computedStages.drop(1)) {
            when (stage) {
                displayed -> {
                    pending = null
                    pendingCount = 0
                }
                pending -> pendingCount++
                else -> {
                    pending = stage
                    pendingCount = 1
                }
            }
            if (pending != null && pendingCount >= holdDays) {
                displayed = pending
                pending = null
                pendingCount = 0
            }
        }
        return displayed
    }

    private fun assessGroups(
        rules: RegimeRules,
        period: RegimePeriod,
        close: DailySeries,
        series: Map<String, DailySeries>,
        index: Int,
    ): List<GroupAssessment> =
        rules.groups.map { group ->
            val indicators = group.indicators.map { assessIndicator(it, period, close, series, index) }
            GroupAssessment(
                id = group.id,
                weight = group.weight,
                score = indicators.map { it.score }.average(),
                indicators = indicators,
            )
        }

    // 현재 상태는 단계를 가르기 전에 소수 여섯 자리로 반올림한다. 경계에 꼭 걸리는 날에 계산 오차로 단계가 달라지지 않게 한다.
    private fun compositeOf(groups: List<GroupAssessment>): Double = round(groups.sumOf { it.score * it.weight })

    private fun stageOf(rules: RegimeRules, score: Double): MarketStage = when {
        score >= rules.upperBoundary -> MarketStage.BULL
        score >= 0.0 -> MarketStage.NEUTRAL
        score >= rules.lowerBoundary -> MarketStage.WEAK
        else -> MarketStage.BEAR
    }

    // index는 그 대상의 거래일(종가가 있는 날)의 위치다.
    private fun assessIndicator(
        indicator: IndicatorRule,
        period: RegimePeriod,
        close: DailySeries,
        series: Map<String, DailySeries>,
        index: Int,
    ): IndicatorAssessment {
        val today = close.values[index]
        return when (val scoring = indicator.scoring) {
            is ScoringRule.AboveAverage -> {
                // 용어: 평균 괴리
                val averageDeviation = today / close.averageAt(index, period.averageDays) - 1
                val rounded = round(averageDeviation)
                val score = when {
                    rounded >= scoring.averageBand -> 1.0
                    rounded >= -scoring.averageBand -> 0.0
                    else -> -1.0
                }
                IndicatorAssessment(indicator.id, score, IndicatorEvidence.RatioEvidence(averageDeviation))
            }
            ScoringRule.FromHigh -> {
                val high = close.highAt(index, period.highDays)
                // 용어: 고점 대비 낙폭
                val dropFromHigh = (today - high) / high
                val decline = -round(dropFromHigh)
                val score = when {
                    decline <= period.shallowDrop -> 1.0
                    decline >= period.deepDrop -> -1.0
                    else -> 0.0
                }
                IndicatorAssessment(indicator.id, score, IndicatorEvidence.RatioEvidence(dropFromHigh))
            }
            ScoringRule.AverageSlope -> {
                val slope = close.averageAt(index, period.averageDays) /
                    close.averageAt(index - period.slopeDays, period.averageDays) - 1
                val score = if (round(slope) >= 0.0) 1.0 else -1.0
                IndicatorAssessment(indicator.id, score, IndicatorEvidence.RatioEvidence(slope))
            }
            is ScoringRule.HighIsGood -> {
                val (value, date) = seriesValue(indicator, series, close.dates[index])
                val score = when {
                    value >= scoring.plusFrom -> 1.0
                    value >= scoring.zeroFrom -> 0.0
                    else -> -1.0
                }
                IndicatorAssessment(indicator.id, score, IndicatorEvidence.ValueEvidence(value, date))
            }
            is ScoringRule.LowIsGood -> {
                val (value, date) = seriesValue(indicator, series, close.dates[index])
                val score = when {
                    value < scoring.plusBelow -> 1.0
                    value <= scoring.minusAbove -> 0.0
                    else -> -1.0
                }
                IndicatorAssessment(indicator.id, score, IndicatorEvidence.ValueEvidence(value, date))
            }
        }
    }

    // 그날이나 그 전의 가장 가까운 날의 값과 그 날짜. 시리즈 항목만 부른다.
    private fun seriesValue(indicator: IndicatorRule, series: Map<String, DailySeries>, day: LocalDate): Pair<Double, LocalDate> {
        val input = indicator.input as? IndicatorInput.Series
            ?: error("항목 ${indicator.id}의 점수 기준은 시리즈 값을 쓴다")
        val s = series.getValue(input.series.seriesId)
        val i = s.floorIndex(day)
        return s.values[i] to s.dates[i]
    }

    // 기준과 견주기 전에 소수 여섯 자리로 반올림한다(HALF_UP).
    private fun round(value: Double): Double =
        BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).toDouble()

    private companion object {
        const val DECIMALS = 6
    }
}
