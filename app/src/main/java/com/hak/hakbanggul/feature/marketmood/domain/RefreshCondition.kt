package com.hak.hakbanggul.feature.marketmood.domain

// 받아 달라고 청하는 쪽이 고르는 받는 조건. 셋 모두 출처의 키가 모두 쓸 수 있어야 받는다.
enum class RefreshCondition {
    // 화면이 보이게 될 때 받는 조건과 같다. 판정을 낼 수 없거나, 성공한 때가 없거나, 성공한 지 6시간이 넘었거나, 성공한 때가 지금보다 뒤다.
    WHEN_STALE,

    // 그 밖의 것은 따지지 않는다.
    WHEN_KEYS_AVAILABLE,

    // 마지막으로 받기를 시작한 지 1분이 넘었다. 시작한 때가 없거나 지금보다 뒤면 넘은 것으로 본다.
    WHEN_NOT_RECENTLY_STARTED,
}
