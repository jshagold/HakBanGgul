package com.hak.hakbanggul.feature.settings.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// FRED에 키를 시험한다. 본문은 판정에만 쓰므로 원문으로 받는다.
interface FredKeyCheckApi {
    @GET("fred/series")
    suspend fun series(
        @Query("series_id") seriesId: String,
        @Query("file_type") fileType: String,
        @Query("api_key") apiKey: String,
    ): Response<ResponseBody>
}
