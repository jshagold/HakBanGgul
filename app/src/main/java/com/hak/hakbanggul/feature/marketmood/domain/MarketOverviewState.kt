package com.hak.hakbanggul.feature.marketmood.domain

// 한 벌의 상태
sealed interface MarketOverviewState {
    // 용어: 계산 중
    // 앱이 켜진 뒤(이 프로세스에서) 아직 한 벌을 한 번도 계산해 내지 않음.
    // 규칙을 싣는 것은 상세 화면이 계산 중에도 위쪽 줄의 이름을 그리기 위함이다.
    data class Calculating(val rules: MoodRules) : MarketOverviewState

    // 계산한 한 벌. null은 판정을 낼 수 없음이다.
    data class Ready(val overview: MarketOverview?) : MarketOverviewState
}
