package com.hak.hakbanggul.feature.marketmood.domain

// 항목이 받는 값
sealed interface IndicatorInput {
    // 그 대상의 종가
    data object TargetClose : IndicatorInput

    // 대상과 상관없는 시리즈 하나
    data class Series(val series: IndicatorSeries) : IndicatorInput
}
