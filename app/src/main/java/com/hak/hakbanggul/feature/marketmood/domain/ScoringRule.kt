package com.hak.hakbanggul.feature.marketmood.domain

// 점수 기준의 종류와 기준 숫자. 기간마다 다른 숫자(평균 일수, 고점 일수, 고점 기준, 기울기 일수)는 RegimePeriod에 있다.
sealed interface ScoringRule {

    // 용어: 평균 대비
    // 평균 괴리 ≥ averageBand면 +1, -averageBand ≤ 괴리 < averageBand면 0, 그 밖 -1
    data class AboveAverage(
        // 용어: 평균 대비 기준
        // 비율(0.02는 2%)
        val averageBand: Double,
    ) : ScoringRule

    // 용어: 고점 대비
    // 하락률 ≤ 얕은 고점 기준이면 +1, ≥ 깊은 고점 기준이면 -1, 그 사이 0
    data object FromHigh : ScoringRule

    // 용어: 평균의 기울기
    // 기울기 ≥ 0이면 +1, 그 밖 -1
    data object AverageSlope : ScoringRule

    // 값이 높을수록 좋음: plusFrom 이상 +1, zeroFrom 이상 0, 그 밖 -1
    data class HighIsGood(
        val zeroFrom: Double,
        val plusFrom: Double,
    ) : ScoringRule

    // 값이 낮을수록 좋음: plusBelow 미만 +1, minusAbove 이하 0, 그 밖 -1
    data class LowIsGood(
        val plusBelow: Double,
        val minusAbove: Double,
    ) : ScoringRule
}
