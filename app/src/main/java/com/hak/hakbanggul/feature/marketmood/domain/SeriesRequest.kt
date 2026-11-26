package com.hak.hakbanggul.feature.marketmood.domain

import java.time.LocalDate

// 받을 시리즈 하나와 그 시작 날짜
data class SeriesRequest(
    val series: IndicatorSeries,
    val startDate: LocalDate,
)
