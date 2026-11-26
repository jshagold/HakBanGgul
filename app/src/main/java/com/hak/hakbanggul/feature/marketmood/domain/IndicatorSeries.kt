package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataSource

// 항목이 쓰는 시리즈. seriesId는 티커나 출처의 ID다.
data class IndicatorSeries(
    val source: DataSource,
    val seriesId: String,
    val kind: SeriesKind,
)

// 값의 종류
enum class SeriesKind {
    // 일 종가
    DAILY_CLOSE,

    // 날마다의 값
    DAILY_VALUE,

    // 달마다의 값. 그 달 1일의 날짜로 담는다(FRED가 주는 날짜 그대로).
    MONTHLY_VALUE,
}
