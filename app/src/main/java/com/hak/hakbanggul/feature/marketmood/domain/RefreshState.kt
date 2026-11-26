package com.hak.hakbanggul.feature.marketmood.domain

// 받는 중인지와 가장 최근 받기의 실패 원인. 성공했거나 받은 적이 없으면 lastFailure는 null이다.
data class RefreshState(
    val isRefreshing: Boolean,
    val lastFailure: RefreshFailure?,
    // 용어: 전체 시장의 값을 받음
    // 이번 받기에서 전체 시장의 값(SPY가 든 첫 묶음과 FRED)을 받아 저장했는지. 받는 중이 아니면 늘 거짓이다.
    val broadMarketReceived: Boolean = false,
)
