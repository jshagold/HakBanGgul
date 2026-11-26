package com.hak.hakbanggul.feature.marketmood.domain

// 판정 규칙. 코드가 아니라 데이터로 둔다. 목록의 순서가 화면의 순서다.
data class MoodRules(
    val groups: List<IndicatorGroupRule>,
    // 단계 경계
    val upperBoundary: Double,
    val lowerBoundary: Double,
    // 용어: 유지 일수
    val holdDays: Int,
    // 용어: 매매 판단
    val valuation: ValuationRules,
    // 용어: 받는 기간
    // 장세의 항목과 그 밖의 매매 판단 시리즈(10년물, 기준금리, 소비자물가)의 받는 기간(해)
    val fetchYears: Int,
) {
    // 받을 시리즈마다 받는 기간(해). 시리즈 ID가 같은 시리즈가 여럿이면 처음 나온 것 하나만 두고 그중 긴 기간을 쓴다.
    // 순서는 묶음과 항목의 순서, 그 뒤에 대상 → 실질금리 → 10년물 → 기준금리 → 소비자물가 순이다.
    fun fetchYearsBySeries(): Map<IndicatorSeries, Int> {
        val entries = buildList {
            groups.forEach { group -> group.indicators.forEach { add(it.series to fetchYears) } }
            add(valuation.priceSeries to valuation.priceFetchYears)
            add(valuation.realYieldSeries to valuation.realYieldFetchYears)
            add(valuation.treasury10ySeries to fetchYears)
            add(valuation.policyRateSeries to fetchYears)
            add(valuation.consumerPriceSeries to fetchYears)
        }
        val seriesById = LinkedHashMap<String, IndicatorSeries>()
        val yearsById = HashMap<String, Int>()
        for ((series, years) in entries) {
            seriesById.putIfAbsent(series.seriesId, series)
            yearsById[series.seriesId] = maxOf(yearsById[series.seriesId] ?: years, years)
        }
        return seriesById.entries.associate { (id, series) -> series to yearsById.getValue(id) }
    }
}

// 용어: 묶음
data class IndicatorGroupRule(
    val id: String,
    val weight: Double,
    val indicators: List<IndicatorRule>,
)

// 용어: 항목
data class IndicatorRule(
    val id: String,
    val series: IndicatorSeries,
    val scoring: ScoringRule,
)
