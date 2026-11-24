package com.hak.hakbanggul.feature.marketmood.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// FRED 시리즈의 관측값. 본문을 원문으로 받아 가른 뒤에 읽는다. 키는 인터셉터가 붙인다.
interface FredObservationsApi {
    @GET("fred/series/observations")
    suspend fun observations(
        @Query("series_id") seriesId: String,
        // YYYY-MM-DD
        @Query("observation_start") observationStart: String,
        @Query("file_type") fileType: String = "json",
    ): Response<ResponseBody>
}
