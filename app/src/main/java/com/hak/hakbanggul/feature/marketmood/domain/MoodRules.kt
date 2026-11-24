package com.hak.hakbanggul.feature.marketmood.domain

// 판정 규칙. 코드가 아니라 데이터로 둔다. 목록의 순서가 화면의 순서다.
data class MoodRules(
    val groups: List<IndicatorGroupRule>,
    // 단계 경계
    val upperBoundary: Double,
    val lowerBoundary: Double,
    // 용어: 유지 일수
    val holdDays: Int,
)

// 용어: 묶음
data class IndicatorGroupRule(
    val id: String,
    val weight: Double,
    val indicators: List<IndicatorRule>,
)

// 용어: 항목
data class IndicatorRule(
    val id: String,
    val series: IndicatorSeries,
    val scoring: ScoringRule,
)
