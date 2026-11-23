package com.hak.hakbanggul.feature.settings.domain

// 용어: 키의 상태
// 저장되는 키의 상태. 키 없음은 저장된 키가 없는 것으로 나타낸다.
enum class ApiKeyStatus {
    // 저장됨
    SAVED,

    // 틀림
    INVALID,
}
