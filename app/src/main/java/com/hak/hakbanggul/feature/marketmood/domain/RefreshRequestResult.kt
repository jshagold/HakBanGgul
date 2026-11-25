package com.hak.hakbanggul.feature.marketmood.domain

// 용어: 받아 달라는 청
// RefreshRequest의 결과. 받기가 성공했는지는 담지 않는다. 실패의 원인은 RefreshState.lastFailure에 있다.
enum class RefreshRequestResult {
    // 이 청으로 받기를 시작했고, 그 받기가 끝났다.
    REFRESHED,

    // 이미 받는 중이어서 새로 시작하지 않았고, 그 받기가 끝났다.
    JOINED,

    // 조건에 맞지 않아 받지 않았다.
    SKIPPED,
}
