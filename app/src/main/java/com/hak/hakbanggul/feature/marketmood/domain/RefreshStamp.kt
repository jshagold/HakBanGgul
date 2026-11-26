package com.hak.hakbanggul.feature.marketmood.domain

// 받기의 때. 받아 둔 값(StoredMarketData)에 싣지 않는다. 실으면 때를 적을 때마다 판정을 다시 계산한다.
enum class RefreshStamp {
    // 용어: 마지막으로 받기를 시작한 때
    STARTED,

    // 용어: 마지막으로 받기가 끝난 때
    // lastFinishedAt. 성공, 실패, 예외 모두 적는다.
    FINISHED,

    // Twelve Data에 마지막으로 요청을 보낸 때. 분당 한도를 넘지 않게 다음 묶음을 언제 보낼지 정하는 데 쓴다.
    TWELVE_DATA_REQUESTED,
}
