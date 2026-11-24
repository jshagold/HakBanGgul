package com.hak.hakbanggul.feature.marketmood.domain

// 받는 중인지와 가장 최근 받기의 실패 원인. 성공했거나 받은 적이 없으면 lastFailure는 null이다.
data class RefreshState(
    val isRefreshing: Boolean,
    val lastFailure: RefreshFailure?,
)
