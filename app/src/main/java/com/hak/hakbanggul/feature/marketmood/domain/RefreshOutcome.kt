package com.hak.hakbanggul.feature.marketmood.domain

// 용어: 받기
// 받기 한 번의 결과
sealed interface RefreshOutcome {
    data object Success : RefreshOutcome
    data class Failure(val reason: RefreshFailure) : RefreshOutcome
}

// 화면이 가르는 받기 실패의 원인
enum class RefreshFailure {
    RATE_LIMITED,
    NO_CONNECTION,
    OTHER,
}
