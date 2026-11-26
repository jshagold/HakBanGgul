package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// 매매 판단을 내는 순수 계산기. 지금 시각을 쓰지 않아 같은 값과 기준 날짜면 늘 같은 매매 판단이 난다.
class ValuationCalculator @Inject constructor() {

    // 메인 기간의 매매 판단. closes는 대상의 종가, values의 키는 시리즈 ID다.
    // 주가 위치, 금리 부담, 10년물, 기준금리, 물가 상승률 가운데 하나라도 낼 수 없으면 null이다.
    // 기간마다 내는 두 함수에 메인 기간 하나만 넘겨 만든다.
    fun assess(
        rules: ValuationRules,
        closes: List<DailyValue>?,
        values: Map<String, List<DailyValue>>,
        asOfDate: LocalDate,
    ): ValuationAssessment? {
        val main = rules.mainPeriod()
        val pricePosition = assessPricePositions(rules, closes, asOfDate, listOf(main))[main.id] ?: return null
        val rateBurden = assessRateBurdens(rules, values, asOfDate, listOf(main))[main.id] ?: return null
        return ValuationAssessment(
            pricePosition = pricePosition,
            rateBurden = rateBurden,
            lowBoundary = rules.lowBoundary,
            highBoundary = rules.highBoundary,
            buffer = rules.buffer,
            inflationTarget = rules.inflationTarget,
        )
    }

    // 기간마다의 주가 위치. 키는 기간 ID이고, 낼 수 없는 기간은 넣지 않는다.
    // 주가 위치의 날은 대상의 종가가 있는 날이다. 두 괴리, 낙폭, 3개월 수익률은 기간과 상관없이 한 번만 내고,
    // 위치, 점수, 점수로 정한 단계, 표시 단계만 기간마다 그 기간의 위치 기간으로 낸다.
    // 기준 날짜에 종가가 없거나 3개월 수익률을 낼 수 없으면 빈 맵이다.
    fun assessPricePositions(
        rules: ValuationRules,
        closes: List<DailyValue>?,
        asOfDate: LocalDate,
        periods: List<ValuationPeriod>,
    ): Map<String, PricePositionAssessment> {
        if (closes.isNullOrEmpty() || rules.smoothingDays < 1) return emptyMap()
        val series = DailySeries.of(closes)
        val asOfIndex = series.dates.binarySearch(asOfDate)
        if (asOfIndex < 0) return emptyMap()
        // 3개월 수익률은 점수와 단계에 쓰지 않지만, 낼 수 없으면 주가 위치를 낼 수 없는 것으로 본다.
        val threeMonthIndex = asOfIndex - rules.threeMonthDays
        if (threeMonthIndex < 0) return emptyMap()
        // 기준 날짜 뒤의 값은 쓰지 않는다.
        val values = series.values.copyOf(asOfIndex + 1)

        val shortDeviations = deviations(values, rules.shortAverageDays)
        val longDeviations = deviations(values, rules.longAverageDays)
        val drawdown = series.drawdownAt(asOfIndex)
        val threeMonthReturn = values[asOfIndex] / values[threeMonthIndex] - 1
        val weightSum = rules.shortWeight + rules.longWeight

        val result = LinkedHashMap<String, PricePositionAssessment>()
        for (period in periods) {
            val window = period.positionWindow
            if (window < 2) continue
            val shortPositions = positions(shortDeviations, window)
            val longPositions = positions(longDeviations, window)
            val pricePositions = DoubleArray(values.size) { i ->
                val s = shortPositions[i]
                val l = longPositions[i]
                if (s.isNaN() || l.isNaN()) Double.NaN else (s * rules.shortWeight + l * rules.longWeight) / weightSum
            }
            val scores = scores(pricePositions, rules.smoothingDays)
            val score = scores[asOfIndex] ?: continue
            val stages = stagesOf(scores.filterNotNull(), rules)
            result[period.id] = PricePositionAssessment(
                score = score,
                scoredStage = stages.scored,
                displayedStage = stages.displayed,
                shortDeviation = shortDeviations[asOfIndex],
                longDeviation = longDeviations[asOfIndex],
                drawdown = drawdown,
                threeMonthReturn = threeMonthReturn,
            )
        }
        return result
    }

    // 기간마다의 금리 부담. 키는 기간 ID이고, 낼 수 없는 기간은 넣지 않는다.
    // 금리 부담의 날은 실질금리의 값이 있는 날이다. 실질금리의 날, 1년 전과의 차이, 10년물, 기준금리, 물가는 한 번만 내고,
    // 실질금리의 위치, 점수, 단계만 기간마다 낸다. 기간과 상관없는 값 가운데 하나라도 낼 수 없으면 빈 맵이다.
    fun assessRateBurdens(
        rules: ValuationRules,
        values: Map<String, List<DailyValue>>,
        asOfDate: LocalDate,
        periods: List<ValuationPeriod>,
    ): Map<String, RateBurdenAssessment> {
        if (rules.smoothingDays < 1) return emptyMap()
        val realYield = seriesOf(values, rules.realYieldSeries) ?: return emptyMap()
        val realYieldIndex = realYield.floorIndex(asOfDate)
        if (realYieldIndex < 0) return emptyMap()
        // 1년 전 값: 기준 날짜에서 yearAgoDays일(달력) 앞의 날이나 그 전의 날 가운데 값이 있는 가장 가까운 날의 값
        val yearAgoIndex = realYield.floorIndex(asOfDate.minusDays(rules.yearAgoDays.toLong()))
        if (yearAgoIndex < 0) return emptyMap()
        val treasury10y = datedValueOf(values, rules.treasury10ySeries, asOfDate) ?: return emptyMap()
        val policyRate = datedValueOf(values, rules.policyRateSeries, asOfDate) ?: return emptyMap()
        val inflation = inflationOf(values[rules.consumerPriceSeries.seriesId], asOfDate) ?: return emptyMap()
        // 실질금리의 날 뒤의 값은 쓰지 않는다.
        val yields = realYield.values.copyOf(realYieldIndex + 1)

        val result = LinkedHashMap<String, RateBurdenAssessment>()
        for (period in periods) {
            val window = period.positionWindow
            if (window < 2) continue
            val scores = scores(positions(yields, window), rules.smoothingDays)
            val score = scores[realYieldIndex] ?: continue
            val stages = stagesOf(scores.filterNotNull(), rules)
            result[period.id] = RateBurdenAssessment(
                realYieldDate = realYield.dates[realYieldIndex],
                score = score,
                scoredStage = stages.scored,
                displayedStage = stages.displayed,
                realYield = yields[realYieldIndex],
                yearChange = yields[realYieldIndex] - realYield.values[yearAgoIndex],
                treasury10y = treasury10y,
                policyRate = policyRate,
                inflationRate = inflation.rate,
                inflationMonth = inflation.month,
            )
        }
        return result
    }

    private fun seriesOf(values: Map<String, List<DailyValue>>, series: IndicatorSeries): DailySeries? {
        val list = values[series.seriesId]
        return if (list.isNullOrEmpty()) null else DailySeries.of(list)
    }

    // 기준 날짜나 그 전의 날 가운데 값이 있는 가장 가까운 날의 값과 그 날짜
    private fun datedValueOf(values: Map<String, List<DailyValue>>, series: IndicatorSeries, asOfDate: LocalDate): DatedValue? {
        val dailySeries = seriesOf(values, series) ?: return null
        val index = dailySeries.floorIndex(asOfDate)
        if (index < 0) return null
        return DatedValue(dailySeries.values[index], dailySeries.dates[index])
    }

    private class Inflation(val month: YearMonth, val rate: Double)

    // 물가의 1년 상승률과 그 달. 그 달은 12달 전 달의 값도 있는 가장 최근 달이고, 기준 날짜가 든 달까지만 본다.
    private fun inflationOf(list: List<DailyValue>?, asOfDate: LocalDate): Inflation? {
        if (list.isNullOrEmpty()) return null
        val lastMonth = YearMonth.from(asOfDate)
        val byMonth = HashMap<YearMonth, Double>()
        list.sortedBy { it.date }.forEach { byMonth.putIfAbsent(YearMonth.from(it.date), it.value) }
        val month = byMonth.keys
            .filter { !it.isAfter(lastMonth) && it.minusMonths(12) in byMonth }
            .maxOrNull() ?: return null
        return Inflation(month, byMonth.getValue(month) / byMonth.getValue(month.minusMonths(12)) - 1)
    }

    // 날마다의 괴리 = 그날의 종가 ÷ 그날을 포함한 최근 days개 종가의 평균 - 1. 그 개수만큼의 종가가 없는 날은 NaN이다.
    private fun deviations(closes: DoubleArray, days: Int): DoubleArray = DoubleArray(closes.size) { i ->
        if (days < 1 || i + 1 < days) {
            Double.NaN
        } else {
            var sum = 0.0
            for (j in i - days + 1..i) sum += closes[j]
            closes[i] / (sum / days) - 1
        }
    }

    // 용어: 위치
    // 날마다의 위치(0~1). 그날을 포함한 최근 window개 값이 모두 있는 날에만 내고(NaN이 아닌 값), 날마다 창의 값을 처음부터 다시 센다.
    // 위치 = (창에서 그날의 값을 뺀 나머지 가운데 작은 값의 수 + 같은 값의 수 × 0.5) ÷ (window - 1)
    private fun positions(values: DoubleArray, window: Int): DoubleArray {
        val result = DoubleArray(values.size) { Double.NaN }
        for (i in window - 1 until values.size) {
            val today = values[i]
            if (today.isNaN()) continue
            var less = 0
            var equal = 0
            var complete = true
            for (j in i - window + 1 until i) {
                val other = values[j]
                if (other.isNaN()) {
                    complete = false
                    break
                }
                if (other < today) less++ else if (other == today) equal++
            }
            if (complete) result[i] = (less + equal * 0.5) / (window - 1)
        }
        return result
    }

    // 날마다의 점수 = 그날을 포함한 최근 smoothingDays개 날의 위치 평균 × 100을 반올림한 정수. 낼 수 없는 날은 null이다.
    private fun scores(positions: DoubleArray, smoothingDays: Int): List<Int?> = List(positions.size) { i ->
        if (i + 1 < smoothingDays) {
            null
        } else {
            var sum = 0.0
            var complete = true
            for (j in i - smoothingDays + 1..i) {
                if (positions[j].isNaN()) {
                    complete = false
                    break
                }
                sum += positions[j]
            }
            if (complete) roundScore(sum / smoothingDays * 100) else null
        }
    }

    // 소수 첫째 자리가 5 이상이면 올린다(12.5는 13). 계산 오차로 12.4999…가 되어 내려가지 않게 소수 여섯 자리로 먼저 반올림한다.
    private fun roundScore(value: Double): Int =
        BigDecimal.valueOf(value)
            .setScale(SCORE_DECIMALS, RoundingMode.HALF_UP)
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()

    private class Stages(val scored: ValuationStage, val displayed: ValuationStage)

    // 점수를 낼 수 있는 첫날부터 마지막 날까지 날마다 표시 단계를 따진다. 주가 위치와 금리 부담이 함께 쓴다.
    // 점수를 낼 수 있는 날은 이어져 있다(창이 한 번 차면 뒤의 날도 늘 찬다).
    private fun stagesOf(scores: List<Int>, rules: ValuationRules): Stages {
        var displayed = scoredStageOf(scores.first(), rules)
        for (score in scores.drop(1)) {
            displayed = when (displayed) {
                ValuationStage.MIDDLE -> scoredStageOf(score, rules)
                ValuationStage.LOW -> when {
                    score >= rules.highBoundary -> ValuationStage.HIGH
                    score > rules.lowBoundary + rules.buffer -> ValuationStage.MIDDLE
                    else -> ValuationStage.LOW
                }
                ValuationStage.HIGH -> when {
                    score <= rules.lowBoundary -> ValuationStage.LOW
                    score < rules.highBoundary - rules.buffer -> ValuationStage.MIDDLE
                    else -> ValuationStage.HIGH
                }
            }
        }
        return Stages(scoredStageOf(scores.last(), rules), displayed)
    }

    private fun scoredStageOf(score: Int, rules: ValuationRules): ValuationStage = when {
        score <= rules.lowBoundary -> ValuationStage.LOW
        score >= rules.highBoundary -> ValuationStage.HIGH
        else -> ValuationStage.MIDDLE
    }

    private companion object {
        const val SCORE_DECIMALS = 6
    }
}
