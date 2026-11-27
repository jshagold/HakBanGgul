package com.hak.hakbanggul.feature.marketmood.domain

// 받는 중인지와 가장 최근 받기의 실패 원인. 성공했거나 받은 적이 없으면 lastFailure는 null이다.
data class RefreshState(
    val isRefreshing: Boolean,
    val lastFailure: RefreshFailure?,
    // 용어: 전체 시장의 값을 받음
    // 이번 받기에서 받을 전체 시장의 값(SPY를 받으면 SPY가 든 첫 묶음과 FRED, 받지 않으면 FRED)을 받아 저장했는지. 받는 중이 아니면 늘 거짓이다.
    val broadMarketReceived: Boolean = false,
    // 용어: 받을 차례인 ETF
    // 이번 받기에서 받을 ETF(종가 시리즈 ID) 가운데 그 요청이 아직 끝나지 않은 것. 받는 중이 아니면 비어 있다.
    val pendingSeriesIds: Set<String> = emptySet(),
    // 용어: 아직 보내지 않은 종목 수
    // 첫 묶음을 보낸 뒤 아직 보내지 않은 ETF의 수. 첫 묶음을 보내기 전과 받는 중이 아닐 때는 0이다.
    val unsentCount: Int = 0,
)
