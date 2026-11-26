package com.hak.hakbanggul.feature.marketmood.domain

// 용어: 매매 판단의 단계
// 주가 위치와 금리 부담이 함께 쓴다. 이름(싼 편/낮음 등)은 화면이 카드에 따라 고른다.
enum class ValuationStage {
    // 싼 편, 낮음
    LOW,

    // 보통
    MIDDLE,

    // 비싼 편, 높음
    HIGH,
}
