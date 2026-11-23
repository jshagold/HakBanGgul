package com.hak.hakbanggul.feature.settings.domain

// 키 저장 시도의 결과
enum class SaveKeyOutcome {
    SAVED,
    REJECTED,
    NO_CONNECTION,
    UNAVAILABLE,
    STORE_FAILED,
}
