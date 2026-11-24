package com.hak.hakbanggul.feature.marketmood.data

import kotlinx.serialization.Serializable

// Twelve Data time_series 응답에서 종목 하나의 부분. 값은 글자로 온다.
@Serializable
data class TwelveDataTimeSeriesDto(
    val status: String? = null,
    val values: List<TwelveDataBarDto>? = null,
)

@Serializable
data class TwelveDataBarDto(
    val datetime: String? = null,
    val close: String? = null,
)
