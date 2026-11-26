package com.hak.hakbanggul.feature.marketmood.data

import com.hak.hakbanggul.core.database.DailyValueDao
import com.hak.hakbanggul.core.database.DailyValueEntity
import com.hak.hakbanggul.core.database.SyncStampDao
import com.hak.hakbanggul.core.database.SyncStampEntity
import com.hak.hakbanggul.core.database.TransactionRunner
import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.SourceCallExecutor
import com.hak.hakbanggul.core.network.SourceResponseClassifier
import com.hak.hakbanggul.feature.marketmood.domain.FetchResult
import com.hak.hakbanggul.feature.marketmood.domain.IndicatorSeries
import com.hak.hakbanggul.feature.marketmood.domain.SeriesKind
import com.hak.hakbanggul.feature.marketmood.domain.SeriesRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate
import java.util.Collections

// 출처에서 받기: 요청을 나누는 방법과 실패의 처리 (가짜 Api)
class MarketDataRepositoryImplFetchTest {

    private val json = "application/json".toMediaType()

    private fun ok(body: String): Response<ResponseBody> = Response.success(body.toResponseBody(json))

    private fun error(code: Int): Response<ResponseBody> = Response.error(code, "".toResponseBody(json))

    private fun close(symbol: String) = """{"meta":{"symbol":"$symbol"},"values":[{"datetime":"2026-11-23","close":"100.0"}],"status":"ok"}"""

    private val spyBody = close("SPY")
    private val threeBody = """{"QQQ":${close("QQQ")},"XLK":${close("XLK")},"SMH":${close("SMH")}}"""

    // 요청마다의 응답을 테스트가 정하는 가짜 Api

    private class FakeTwelveData(val answer: (String, String) -> Response<ResponseBody>) : TwelveDataTimeSeriesApi {
        val calls: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf())
        override suspend fun timeSeries(symbol: String, startDate: String, interval: String, outputSize: Int): Response<ResponseBody> {
            calls += symbol to startDate
            return answer(symbol, startDate)
        }
    }

    private class FakeFred(val answer: (String) -> Response<ResponseBody>) : FredObservationsApi {
        val calls: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf())
        override suspend fun observations(seriesId: String, observationStart: String, fileType: String): Response<ResponseBody> {
            calls += seriesId to observationStart
            return answer(seriesId)
        }
    }

    // 받기 테스트에서는 저장하지 않는다.
    private object UnusedDailyValueDao : DailyValueDao {
        override fun observeAll(): Flow<List<DailyValueEntity>> = emptyFlow()
        override suspend fun deleteBySeries(seriesId: String) = error("쓰지 않음")
        override suspend fun insertAll(rows: List<DailyValueEntity>) = error("쓰지 않음")
        override suspend fun deleteAll() = error("쓰지 않음")
    }

    private object UnusedSyncStampDao : SyncStampDao {
        override fun observe(name: String): Flow<Long?> = emptyFlow()
        override suspend fun upsert(stamp: SyncStampEntity) = error("쓰지 않음")
        override suspend fun deleteAll() = error("쓰지 않음")
    }

    private object UnusedTransactionRunner : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = error("쓰지 않음")
    }

    private fun repository(twelveData: TwelveDataTimeSeriesApi = FakeTwelveData { _, _ -> error("쓰지 않음") }, fred: FredObservationsApi = FakeFred { error("쓰지 않음") }) =
        MarketDataRepositoryImpl(
            twelveData,
            fred,
            SourceCallExecutor(SourceResponseClassifier()),
            MarketResponseMapper(Json { ignoreUnknownKeys = true }),
            UnusedDailyValueDao,
            UnusedSyncStampDao,
            UnusedTransactionRunner,
        )

    private fun stock(ticker: String, start: String) =
        SeriesRequest(IndicatorSeries(DataSource.TWELVE_DATA, ticker, SeriesKind.DAILY_CLOSE), LocalDate.parse(start))

    private fun fred(id: String, start: String) =
        SeriesRequest(IndicatorSeries(DataSource.FRED, id, SeriesKind.DAILY_VALUE), LocalDate.parse(start))

    private val stockRequests = listOf(
        stock("SPY", "2017-11-23"),
        stock("QQQ", "2024-11-23"),
        stock("XLK", "2024-11-23"),
        stock("SMH", "2024-11-23"),
    )

    // R-41 시작 날짜가 같은 종목끼리 한 요청, 하나라도 실패하면 출처 실패

    @Test
    fun `R-41 시작 날짜가 같은 종목끼리 한 요청으로 묶어 두 번 부르고 네 종목의 값이 모두 있다`() = runTest {
        val api = FakeTwelveData { symbol, _ -> ok(if (symbol == "SPY") spyBody else threeBody) }

        val result = repository(twelveData = api).fetch(DataSource.TWELVE_DATA, stockRequests)

        assertEquals(listOf("SPY" to "2017-11-23", "QQQ,XLK,SMH" to "2024-11-23"), api.calls.sortedBy { it.second })
        assertEquals(2, api.calls.size)
        assertTrue(result is FetchResult.Success)
        assertEquals(setOf("SPY", "QQQ", "XLK", "SMH"), (result as FetchResult.Success).values.keys)
    }

    @Test
    fun `R-41 두 번째 요청이 HTTP 429이면 RATE_LIMITED이고 SPY의 값도 돌려주지 않는다`() = runTest {
        val api = FakeTwelveData { symbol, _ -> if (symbol == "SPY") ok(spyBody) else error(429) }

        val result = repository(twelveData = api).fetch(DataSource.TWELVE_DATA, stockRequests)

        assertEquals(FetchResult.Failure(DataFetchError.RATE_LIMITED), result)
    }

    @Test
    fun `R-41 첫 번째 요청이 읽을 수 없는 본문이고 두 번째가 HTTP 429이면 앞 요청의 OTHER`() = runTest {
        val api = FakeTwelveData { symbol, _ -> if (symbol == "SPY") ok("""{"meta":{"symbol":"SPY"},"status":"ok"}""") else error(429) }

        val result = repository(twelveData = api).fetch(DataSource.TWELVE_DATA, stockRequests)

        assertEquals(FetchResult.Failure(DataFetchError.OTHER), result)
    }

    @Test
    fun `R-41 FRED는 시리즈마다 그 시작 날짜로 부른다`() = runTest {
        val api = FakeFred { ok("""{"observations":[{"date":"2026-11-23","value":"2.0"}]}""") }

        val result = repository(fred = api).fetch(DataSource.FRED, listOf(fred("DFII10", "2020-11-23"), fred("DGS10", "2024-11-23")))

        assertEquals(listOf("DFII10" to "2020-11-23", "DGS10" to "2024-11-23"), api.calls.sortedBy { it.first })
        assertEquals(setOf("DFII10", "DGS10"), (result as FetchResult.Success).values.keys)
    }

    @Test
    fun `요청 목록이 비면 빈 Success`() = runTest {
        assertEquals(FetchResult.Success(emptyMap()), repository().fetch(DataSource.TWELVE_DATA, emptyList()))
    }
}
