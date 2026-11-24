package com.hak.hakbanggul.feature.marketmood.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// Twelve Data의 일별 시세. 오류를 HTTP 200의 본문으로 주는 경우가 있어 본문을 원문으로 받아 가른 뒤에 읽는다.
// 키는 인터셉터가 붙인다.
interface TwelveDataTimeSeriesApi {
    @GET("time_series")
    suspend fun timeSeries(
        // 쉼표로 이은 티커
        @Query("symbol") symbol: String,
        // YYYY-MM-DD
        @Query("start_date") startDate: String,
        @Query("interval") interval: String = "1day",
        @Query("outputsize") outputSize: Int = 5000,
    ): Response<ResponseBody>
}
