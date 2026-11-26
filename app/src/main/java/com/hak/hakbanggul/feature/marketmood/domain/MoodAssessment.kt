package com.hak.hakbanggul.feature.marketmood.domain

import java.time.LocalDate

// 판정 하나. 저장하지 않고 볼 때마다 처음부터 계산한다.
// holdDays, upperBoundary, lowerBoundary는 규칙의 값을 그대로 옮긴 것이다.
data class MoodAssessment(
    // 용어: 기준 날짜
    val asOfDate: LocalDate,
    // 용어: 표시 단계
    val displayedStage: MarketStage,
    // 용어: 계산 단계
    val computedStage: MarketStage,
    // 용어: 현재 상태(화면에서는 종합 점수)
    val compositeScore: Double,
    // 기준 날짜의 계산 단계가 기준 날짜까지 연속으로 이어진 거래일 수
    val computedStageStreak: Int,
    val holdDays: Int,
    val upperBoundary: Double,
    val lowerBoundary: Double,
    val groups: List<GroupAssessment>,
    // 판정이 있으면 매매 판단도 늘 있다.
    val valuation: ValuationAssessment,
)

data class GroupAssessment(
    val id: String,
    val weight: Double,
    val score: Double,
    val indicators: List<IndicatorAssessment>,
)

data class IndicatorAssessment(
    val id: String,
    val score: Double,
    val evidence: IndicatorEvidence,
)

// 항목 점수의 근거
sealed interface IndicatorEvidence {

    // 주식 항목의 비율 하나: 평균 대비는 평균 괴리, 고점 대비는 고점 대비 낙폭(0 이하), 평균의 기울기는 기울기.
    // 반올림 전의 값이다.
    data class RatioEvidence(
        val ratio: Double,
    ) : IndicatorEvidence

    // 그날이나 그 전의 가장 가까운 날의 값과 그 날짜
    data class ValueEvidence(
        val value: Double,
        val valueDate: LocalDate,
    ) : IndicatorEvidence
}
