package com.hak.hakbanggul.feature.marketmood.domain

// 매매 판단의 규칙. 코드가 아니라 데이터로 둔다.
// 주가 위치는 대상의 종가로 내고, 주가 위치와 금리 부담이 같은 기간 목록을 쓴다.
data class ValuationRules(
    // 용어: 짧은 평균 일수
    val shortAverageDays: Int,
    // 용어: 긴 평균 일수
    val longAverageDays: Int,
    // 긴 평균의 이름("3년")
    val longAverageLength: PeriodLength,
    // 용어: 짧은 쪽 비중
    val shortWeight: Double,
    // 용어: 긴 쪽 비중
    val longWeight: Double,
    // 용어: 평균 일수
    val smoothingDays: Int,
    // 용어: 낮은 경계
    val lowBoundary: Int,
    // 용어: 높은 경계
    val highBoundary: Int,
    // 용어: 완충
    val buffer: Int,
    // 용어: 3개월로 보는 날 수
    val threeMonthDays: Int,
    // 3개월의 이름("3개월")
    val threeMonthLength: PeriodLength,
    // 짧은 기간부터
    val periods: List<ValuationPeriod>,
    val mainPeriodId: String,
    // 대상의 종가의 받는 기간(해)
    val priceFetchYears: Int,
    // 용어: 실질금리
    val realYieldSeries: IndicatorSeries,
    val realYieldFetchYears: Int,
    // 용어: 10년물 국채 금리
    val treasury10ySeries: IndicatorSeries,
    // 용어: 기준금리
    val policyRateSeries: IndicatorSeries,
    // 용어: 소비자물가
    val consumerPriceSeries: IndicatorSeries,
    // 용어: 1년 전으로 보는 날 수
    // 달력의 날 수
    val yearAgoDays: Int,
    // 용어: 물가 목표
    // 비율(2%는 0.02)
    val inflationTarget: Double,
) {
    fun mainPeriod(): ValuationPeriod = periods.first { it.id == mainPeriodId }
}

// 매매 판단의 기간 하나
data class ValuationPeriod(
    val id: String,
    val length: PeriodLength,
    // 용어: 위치 기간
    // 값의 개수로 센다.
    val positionWindow: Int,
)
