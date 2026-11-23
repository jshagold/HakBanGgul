package com.hak.hakbanggul.core.network

// 출처 응답의 종류
enum class SourceResponseKind {
    // 받아들임
    ACCEPTED,

    // 키 거절
    KEY_REJECTED,

    // 그 밖의 실패
    OTHER_FAILURE,
}
