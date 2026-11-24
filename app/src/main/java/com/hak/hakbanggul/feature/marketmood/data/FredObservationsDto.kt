package com.hak.hakbanggul.feature.marketmood.data

import kotlinx.serialization.Serializable

// FRED series/observations 응답. 값이 없는 날은 "."으로 온다.
@Serializable
data class FredObservationsDto(
    val observations: List<FredObservationDto>? = null,
)

@Serializable
data class FredObservationDto(
    val date: String? = null,
    val value: String? = null,
)
