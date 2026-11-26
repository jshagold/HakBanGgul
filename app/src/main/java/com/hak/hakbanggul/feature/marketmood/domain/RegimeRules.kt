package com.hak.hakbanggul.feature.marketmood.domain

// 용어: 장세
// 장세의 규칙. 공식은 하나이고 기간(RegimePeriod)마다 숫자만 다르다.
data class RegimeRules(
    val groups: List<IndicatorGroupRule>,
    // 단계 경계
    val upperBoundary: Double,
    val lowerBoundary: Double,
    // 용어: 유지 일수
    val holdDays: Int,
    // 짧은 기간부터
    val periods: List<RegimePeriod>,
    // 용어: 메인 기간
    val mainPeriodId: String,
) {
    fun mainPeriod(): RegimePeriod = periods.first { it.id == mainPeriodId }
}

// 용어: 기간
// 장세의 기간 하나. 일수는 그 대상의 거래일(종가가 있는 날)로 센다.
data class RegimePeriod(
    val id: String,
    val length: PeriodLength,
    // 평균 대비의 평균 일수(N)
    val averageDays: Int,
    // 용어: 고점 일수
    // 고점 대비의 고점을 찾는 일수(H)
    val highDays: Int,
    // 용어: 얕은 고점 기준
    // 하락률(크기, 0.05는 5%)이 이 값 이하면 +1
    val shallowDrop: Double,
    // 용어: 깊은 고점 기준
    // 하락률이 이 값 이상이면 -1
    val deepDrop: Double,
    // 용어: 기울기 일수
    // 평균의 기울기에서 견주는 앞 거래일의 수(S)
    val slopeDays: Int,
)
