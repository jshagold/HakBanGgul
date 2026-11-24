package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

// 받아 둔 일별 값과 규칙으로 판정을 내는 순수 계산기.
// 항목이 무엇인지 모르고 규칙의 목록만 돈다. 지금 시각을 쓰지 않아 같은 값이면 늘 같은 판정이 난다.
class MoodCalculator @Inject constructor() {

    // values의 키는 시리즈 ID다. 목록의 순서에 기대지 않는다.
    // 기준 날짜의 항목 점수를 모든 항목에 대해 낼 수 없으면 null이다.
    fun assess(rules: MoodRules, values: Map<String, List<DailyValue>>): MoodAssessment? {
        val indicators = rules.groups.flatMap { it.indicators }
        if (indicators.isEmpty() || rules.groups.any { it.indicators.isEmpty() }) return null

        val series = HashMap<String, Series>()
        for (indicator in indicators) {
            val id = indicator.series.seriesId
            if (id in series) continue
            val list = values[id]
            if (list.isNullOrEmpty()) return null
            series[id] = Series.of(list)
        }

        // 거래일은 값의 종류가 DAILY_CLOSE인 항목 모두의 값이 있는 날이다.
        val closeIds = indicators.filter { it.series.kind == SeriesKind.DAILY_CLOSE }.map { it.series.seriesId }.distinct()
        if (closeIds.isEmpty()) return null
        // 용어: 거래일
        val tradingDays = closeIds
            .map { series.getValue(it).dates.toSet() }
            .reduce { acc, dates -> acc intersect dates }
            .sorted()
        if (tradingDays.isEmpty()) return null

        val asOfDate = tradingDays.last()
        val scorableDays = tradingDays.filter { day -> indicators.all { canScore(it, series.getValue(it.series.seriesId), day) } }
        if (scorableDays.isEmpty() || scorableDays.last() != asOfDate) return null

        // 첫 거래일부터 기준 날짜까지 날마다 계산 단계를 내어 표시 단계를 구한다.
        val computedStages = scorableDays.map { day -> stageOf(rules, compositeOf(assessGroups(rules, series, day))) }
        val displayedStage = displayedStageOf(computedStages, rules.holdDays)
        val computedStage = computedStages.last()
        val streak = computedStages.asReversed().takeWhile { it == computedStage }.size

        val groups = assessGroups(rules, series, asOfDate)
        return MoodAssessment(
            asOfDate = asOfDate,
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

    private fun assessGroups(rules: MoodRules, series: Map<String, Series>, day: LocalDate): List<GroupAssessment> =
        rules.groups.map { group ->
            val indicators = group.indicators.map { assessIndicator(it, series.getValue(it.series.seriesId), day) }
            GroupAssessment(
                id = group.id,
                weight = group.weight,
                score = indicators.map { it.score }.average(),
                indicators = indicators,
            )
        }

    // 현재 상태는 단계를 가르기 전에 소수 여섯 자리로 반올림한다. 경계에 꼭 걸리는 날에 계산 오차로 단계가 달라지지 않게 한다.
    private fun compositeOf(groups: List<GroupAssessment>): Double {
        val sum = groups.sumOf { it.score * it.weight }
        return BigDecimal.valueOf(sum).setScale(COMPOSITE_DECIMALS, RoundingMode.HALF_UP).toDouble()
    }

    private fun stageOf(rules: MoodRules, score: Double): MarketStage = when {
        score >= rules.upperBoundary -> MarketStage.BULL
        score >= 0.0 -> MarketStage.NEUTRAL
        score >= rules.lowerBoundary -> MarketStage.WEAK
        else -> MarketStage.BEAR
    }

    // 계산 단계를 낼 수 있는지: 평균에 쓸 종가가 모자라지 않고 1년 전 날까지 받아 두었는지(PriceTrend), 그날이나 그 전에 값이 있는지
    private fun canScore(indicator: IndicatorRule, series: Series, day: LocalDate): Boolean {
        val index = series.floorIndex(day)
        if (index < 0) return false
        return when (val scoring = indicator.scoring) {
            is ScoringRule.PriceTrend ->
                index + 1 >= scoring.averageDays && !series.dates.first().isAfter(day.minusYears(1))
            is ScoringRule.HighIsGood, is ScoringRule.LowIsGood -> true
        }
    }

    private fun assessIndicator(indicator: IndicatorRule, series: Series, day: LocalDate): IndicatorAssessment {
        val index = series.floorIndex(day)
        val value = series.values[index]
        return when (val scoring = indicator.scoring) {
            is ScoringRule.PriceTrend -> {
                var sum = 0.0
                for (i in index - scoring.averageDays + 1..index) sum += series.values[i]
                val average = sum / scoring.averageDays
                val trendScore = if (value >= average) 1 else -1

                // 최근 1년 최고 종가: 1년 전 같은 날부터 그날까지(두 날 포함)의 종가 가운데 가장 높은 값
                val yearAgo = day.minusYears(1)
                var high = value
                var i = index
                while (i >= 0 && !series.dates[i].isBefore(yearAgo)) {
                    if (series.values[i] > high) high = series.values[i]
                    i--
                }
                val drawdown = (value - high) / high
                val decline = -drawdown
                val drawdownScore = when {
                    decline <= scoring.shallowDrawdown -> 1
                    decline >= scoring.deepDrawdown -> -1
                    else -> 0
                }
                IndicatorAssessment(
                    id = indicator.id,
                    score = (trendScore + drawdownScore) / 2.0,
                    evidence = IndicatorEvidence.PriceEvidence(
                        aboveAverageRatio = (value - average) / average,
                        drawdown = drawdown,
                    ),
                )
            }
            is ScoringRule.HighIsGood -> {
                val score = when {
                    value >= scoring.plusFrom -> 1.0
                    value >= scoring.zeroFrom -> 0.0
                    else -> -1.0
                }
                IndicatorAssessment(indicator.id, score, IndicatorEvidence.ValueEvidence(value, series.dates[index]))
            }
            is ScoringRule.LowIsGood -> {
                val score = when {
                    value < scoring.plusBelow -> 1.0
                    value <= scoring.minusAbove -> 0.0
                    else -> -1.0
                }
                IndicatorAssessment(indicator.id, score, IndicatorEvidence.ValueEvidence(value, series.dates[index]))
            }
        }
    }

    // 날짜 오름차순으로 정리한 시리즈. 같은 날짜가 여럿이면 앞의 것 하나만 둔다.
    private class Series(val dates: List<LocalDate>, val values: DoubleArray) {

        // 그날이나 그 전의 날 가운데 값이 있는 가장 가까운 날의 위치. 없으면 -1
        fun floorIndex(day: LocalDate): Int {
            val found = dates.binarySearch(day)
            return if (found >= 0) found else -(found + 1) - 1
        }

        companion object {
            fun of(list: List<DailyValue>): Series {
                val sorted = list.sortedBy { it.date }.distinctBy { it.date }
                return Series(sorted.map { it.date }, DoubleArray(sorted.size) { sorted[it].value })
            }
        }
    }

    private companion object {
        const val COMPOSITE_DECIMALS = 6
    }
}
