package com.hak.hakbanggul.feature.marketmood.domain

import java.time.LocalDate
import java.time.YearMonth

// 매매 판단 하나. 다른 기능(홈 화면 위젯)도 받는 모델이다.
// lowBoundary, highBoundary, buffer, inflationTarget은 규칙의 값을 그대로 옮긴 것이다.
data class ValuationAssessment(
    // 용어: 주가 위치
    val pricePosition: PricePositionAssessment,
    // 용어: 금리 부담
    val rateBurden: RateBurdenAssessment,
    val lowBoundary: Int,
    val highBoundary: Int,
    val buffer: Int,
    val inflationTarget: Double,
)

// 기준 날짜의 주가 위치. 괴리, 낙폭, 수익률은 비율이다(+4.1%는 0.041).
data class PricePositionAssessment(
    val score: Int,
    // 용어: 점수로 정한 단계
    val scoredStage: ValuationStage,
    val displayedStage: ValuationStage,
    // 용어: 짧은 괴리
    val shortDeviation: Double,
    // 용어: 긴 괴리
    val longDeviation: Double,
    // 용어: 낙폭
    val drawdown: Double,
    // 용어: 3개월 수익률
    val threeMonthReturn: Double,
)

// 금리 부담. 금리 값은 받은 값 그대로(퍼센트 단위, 2.79%는 2.79)이고, inflationRate는 비율이다(3.7%는 0.037).
data class RateBurdenAssessment(
    // 용어: 실질금리의 날
    val realYieldDate: LocalDate,
    val score: Int,
    val scoredStage: ValuationStage,
    val displayedStage: ValuationStage,
    val realYield: Double,
    // 용어: 1년 전과의 차이
    val yearChange: Double,
    val treasury10y: DatedValue,
    val policyRate: DatedValue,
    // 용어: 물가의 1년 상승률
    val inflationRate: Double,
    val inflationMonth: YearMonth,
)

// 값과 그 값의 날짜
data class DatedValue(
    val value: Double,
    val date: LocalDate,
)
