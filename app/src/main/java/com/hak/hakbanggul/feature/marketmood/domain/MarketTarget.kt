package com.hak.hakbanggul.feature.marketmood.domain

// 용어: 대상
// 장세와 주가 위치를 내는 대상 하나(전체 시장 또는 섹터). 화면의 이름은 Domain에 두지 않는다.
data class MarketTarget(
    val id: String,
    // 그 대상의 ETF 종가 시리즈
    val closeSeries: IndicatorSeries,
)
