package com.hak.hakbanggul.core.model

// 출처에서 받기가 실패한 종류
enum class DataFetchError {
    // 키가 없어 요청을 보내지 않음
    KEY_MISSING,

    // 틀림으로 표시된 키라 요청을 보내지 않음
    KEY_INVALID,

    // 출처가 키를 거절함
    KEY_REJECTED,

    // 출처의 요청 한도를 넘음
    RATE_LIMITED,

    // 연결 안 됨, 시간 초과
    NO_CONNECTION,

    // 그 밖의 실패
    OTHER,
}
