package com.hak.hakbanggul.feature.marketmood.domain

import java.time.LocalDate

// 한 대상, 한 기간의 장세. MoodAssessment의 장세 부분과 같은 필드다.
// holdDays, upperBoundary, lowerBoundary는 규칙의 값을 그대로 옮긴 것이다.
data class RegimeAssessment(
    // 그 대상의 가장 최근 거래일
    val asOfDate: LocalDate,
    val displayedStage: MarketStage,
    val computedStage: MarketStage,
    val compositeScore: Double,
    val computedStageStreak: Int,
    val holdDays: Int,
    val upperBoundary: Double,
    val lowerBoundary: Double,
    val groups: List<GroupAssessment>,
)
