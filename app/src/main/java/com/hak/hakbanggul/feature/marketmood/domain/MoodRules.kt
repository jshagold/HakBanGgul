package com.hak.hakbanggul.feature.marketmood.domain

// 판정 규칙. 코드가 아니라 데이터로 둔다. 목록의 순서가 화면의 순서다.
data class MoodRules(
    // 용어: 전체 시장
    val broadMarket: MarketTarget,
    // 용어: 섹터
    // 이 순서가 화면의 순서다.
    val sectors: List<MarketTarget>,
    val regime: RegimeRules,
    // 용어: 매매 판단
    val valuation: ValuationRules,
    // 용어: 받는 기간
    // 장세의 시리즈 항목과 그 밖의 매매 판단 시리즈(10년물, 기준금리, 소비자물가)의 받는 기간(해)
    val fetchYears: Int,
) {
    // 전체 시장, 그 뒤 섹터 순서
    fun targets(): List<MarketTarget> = listOf(broadMarket) + sectors

    fun target(id: String): MarketTarget? = targets().firstOrNull { it.id == id }

    // 받을 시리즈마다 받는 기간(해). 시리즈 ID가 같은 시리즈가 여럿이면 처음 나온 것 하나만 두고 그중 긴 기간을 쓴다.
    // 순서는 대상의 종가(targets() 순서) → 장세의 시리즈 항목 → 실질금리 → 10년물 → 기준금리 → 소비자물가다.
    // 대상의 종가(TargetClose)를 쓰는 항목은 시리즈가 아니라 넣지 않는다.
    fun fetchYearsBySeries(): Map<IndicatorSeries, Int> {
        val entries = buildList {
            targets().forEach { add(it.closeSeries to valuation.priceFetchYears) }
            regime.groups.forEach { group ->
                group.indicators.forEach { indicator ->
                    val input = indicator.input
                    if (input is IndicatorInput.Series) add(input.series to fetchYears)
                }
            }
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
    val input: IndicatorInput,
    val scoring: ScoringRule,
)
