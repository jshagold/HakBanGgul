package com.hak.hakbanggul.feature.marketmood.domain

// 받아 달라고 청하는 쪽이 고르는 받는 조건. 셋 모두 출처의 키가 모두 쓸 수 있어야 받는다.
enum class RefreshCondition {
    // 화면이 보이게 될 때 받는 조건과 같다. 판정을 낼 수 없거나, 받아 둔 종가가 하나도 없는 대상이 있거나,
    // 성공한 때가 없거나, 성공한 지 6시간이 넘었거나, 성공한 때가 지금보다 뒤다.
    WHEN_STALE,

    // 그 밖의 것은 따지지 않는다.
    WHEN_KEYS_AVAILABLE,

    // 마지막으로 받기를 시작한 때와 끝난 때 가운데 늦은 때로부터 1분이 넘었다.
    // 둘 다 없거나 늦은 때가 지금보다 뒤면 넘은 것으로 본다.
    WHEN_NOT_RECENTLY_STARTED,
}
