package com.hak.hakbanggul.feature.settings.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

// Twelve Data에 키를 시험한다. 본문은 판정에만 쓰므로 원문으로 받는다.
interface TwelveDataKeyCheckApi {
    // authorization: "apikey <키>"
    @GET("api_usage")
    suspend fun apiUsage(@Header("Authorization") authorization: String): Response<ResponseBody>
}
