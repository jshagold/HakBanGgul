package com.hak.hakbanggul.feature.marketmood.domain

// 점수 기준의 종류와 기준 숫자
sealed interface ScoringRule {

    // 가격 추세: 평균선 위아래(추세 점수)와 최근 1년 고점에서의 하락률(낙폭 점수)의 평균
    data class PriceTrend(
        val averageDays: Int,
        val shallowDrawdown: Double,
        val deepDrawdown: Double,
    ) : ScoringRule

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
