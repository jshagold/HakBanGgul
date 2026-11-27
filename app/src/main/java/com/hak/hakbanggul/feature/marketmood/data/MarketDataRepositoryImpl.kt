package com.hak.hakbanggul.feature.marketmood.data

import com.hak.hakbanggul.core.database.DailyValueDao
import com.hak.hakbanggul.core.database.DailyValueEntity
import com.hak.hakbanggul.core.database.SyncStampDao
import com.hak.hakbanggul.core.database.SyncStampEntity
import com.hak.hakbanggul.core.database.TransactionRunner
import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.SourceCallExecutor
import com.hak.hakbanggul.core.network.SourceCallResult
import com.hak.hakbanggul.feature.marketmood.domain.FetchResult
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRepository
import com.hak.hakbanggul.feature.marketmood.domain.RefreshStamp
import com.hak.hakbanggul.feature.marketmood.domain.SeriesRequest
import com.hak.hakbanggul.feature.marketmood.domain.StoredMarketData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

// 출처에서 받기와 받아 둔 값의 저장. 접속 주소와 키는 core/network가 맡는다.
class MarketDataRepositoryImpl @Inject constructor(
    private val twelveDataApi: TwelveDataTimeSeriesApi,
    private val fredApi: FredObservationsApi,
    private val executor: SourceCallExecutor,
    private val mapper: MarketResponseMapper,
    private val dailyValueDao: DailyValueDao,
    private val syncStampDao: SyncStampDao,
    private val transactionRunner: TransactionRunner,
) : MarketDataRepository {

    // 받아 둔 값이 읽히지 않으면 앱을 멈추지 않고 두 표를 비운 뒤 다시 읽는다.
    // 다시 읽어도 안 되면 받아 둔 값이 없는 것으로 내준다.
    // daily_values만 읽는다. Room은 한 트랜잭션에 sync_stamps가 함께 바뀌어도 이 표가 바뀌면 다시 내보내므로 같은 값은 거른다.
    override fun observeStored(): Flow<StoredMarketData> =
        dailyValueDao.observeAll()
            .map { rows ->
                StoredMarketData(
                    values = rows
                        .groupBy({ it.seriesId }, { DailyValue(LocalDate.parse(it.date), it.value) })
                        .mapValues { (_, values) -> values.sortedBy { it.date } },
                )
            }
            .distinctUntilChanged()
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                clearQuietly()
                attempt < MAX_READ_RETRIES
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                emit(StoredMarketData(values = emptyMap()))
            }

    override suspend fun fetch(source: DataSource, requests: List<SeriesRequest>): FetchResult {
        if (requests.isEmpty()) return FetchResult.Success(emptyMap())
        return when (source) {
            DataSource.TWELVE_DATA -> fetchTwelveData(requests)
            DataSource.FRED -> fetchFred(requests)
        }
    }

    // 시작 날짜가 같은 종목끼리 요청 하나로 묶는다. 요청끼리의 순서는 처음 나온 순서다.
    // 요청 가운데 하나라도 실패하면 출처 전체가 실패이고 받은 값을 하나도 돌려주지 않는다(앞 요청의 오류가 먼저다).
    private suspend fun fetchTwelveData(requests: List<SeriesRequest>): FetchResult {
        val batches = requests.groupBy({ it.startDate }, { it.series.seriesId })
        val results = coroutineScope {
            batches.map { (startDate, symbols) ->
                async {
                    val start = startDate.toString()
                    when (val result = executor.execute(DataSource.TWELVE_DATA) { twelveDataApi.timeSeries(symbols.joinToString(","), start) }) {
                        is SourceCallResult.Failure -> FetchResult.Failure(result.error)
                        is SourceCallResult.Success ->
                            mapper.mapTwelveData(result.body, symbols)?.let { FetchResult.Success(it) }
                                ?: FetchResult.Failure(DataFetchError.OTHER)
                    }
                }
            }.awaitAll()
        }
        return merge(results)
    }

    // 시리즈마다 요청 하나다. 하나라도 실패하면 그 오류로 출처 전체가 실패다(앞의 시리즈의 오류가 먼저다).
    private suspend fun fetchFred(requests: List<SeriesRequest>): FetchResult {
        val results = coroutineScope {
            requests.map { request ->
                async {
                    val id = request.series.seriesId
                    val start = request.startDate.toString()
                    when (val result = executor.execute(DataSource.FRED) { fredApi.observations(id, start) }) {
                        is SourceCallResult.Failure -> FetchResult.Failure(result.error)
                        is SourceCallResult.Success ->
                            mapper.mapFred(result.body)?.let { FetchResult.Success(mapOf(id to it)) }
                                ?: FetchResult.Failure(DataFetchError.OTHER)
                    }
                }
            }.awaitAll()
        }
        return merge(results)
    }

    // 요청들의 결과를 하나로 합친다. 실패가 하나라도 있으면 앞의 실패를 돌려준다.
    private fun merge(results: List<FetchResult>): FetchResult {
        results.firstOrNull { it is FetchResult.Failure }?.let { return it }
        return FetchResult.Success(results.filterIsInstance<FetchResult.Success>().flatMap { it.values.entries }.associate { it.toPair() })
    }

    // 한 트랜잭션 안에서 넘긴 시리즈마다 행을 모두 지우고 새로 넣고, 그 시리즈의 받은 때를 적는다. 받아 둔 값에 덧붙이지 않는다.
    override suspend fun save(values: Map<String, List<DailyValue>>, fetchedAt: Instant) {
        try {
            transactionRunner.run {
                values.forEach { (seriesId, list) ->
                    dailyValueDao.deleteBySeries(seriesId)
                    dailyValueDao.insertAll(list.map { DailyValueEntity(seriesId, it.date.toString(), it.value) })
                    syncStampDao.upsert(SyncStampEntity(FETCHED_STAMP_PREFIX + seriesId, fetchedAt.toEpochMilli()))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("받아 둔 값을 저장하지 못함", e)
        }
    }

    // 받은 때의 행만 읽는다. 다른 때의 행은 이름의 앞부분이 달라 들지 않는다.
    override suspend fun getFetchedAt(): Map<String, Instant> =
        try {
            syncStampDao.getByPrefix(FETCHED_STAMP_PREFIX)
                .associate { it.name.removePrefix(FETCHED_STAMP_PREFIX) to Instant.ofEpochMilli(it.timestampMillis) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }

    // 받은 때와 다른 행에 적는다. 읽지 못하면 없는 것으로 본다.
    override suspend fun getStamp(stamp: RefreshStamp): Instant? =
        try {
            syncStampDao.observe(stampName(stamp)).first()?.let { Instant.ofEpochMilli(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    // 받기 중에 적는 것이라 save의 트랜잭션과 묶지 않는다.
    // 받아 둔 값의 Flow는 daily_values만 보므로 이 행이 바뀌어도 새 값을 내보내지 않는다.
    override suspend fun saveStamp(stamp: RefreshStamp, at: Instant) {
        try {
            syncStampDao.upsert(SyncStampEntity(stampName(stamp), at.toEpochMilli()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("받기의 때를 적지 못함", e)
        }
    }

    private fun stampName(stamp: RefreshStamp): String = when (stamp) {
        RefreshStamp.STARTED -> REFRESH_STARTED_STAMP
        RefreshStamp.FINISHED -> REFRESH_FINISHED_STAMP
        RefreshStamp.TWELVE_DATA_REQUESTED -> TWELVE_DATA_REQUESTED_STAMP
    }

    // 비우다가 난 예외는 무시한다.
    private suspend fun clearQuietly() {
        try {
            dailyValueDao.deleteAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 무시
        }
        try {
            syncStampDao.deleteAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 무시
        }
    }

    private companion object {
        // 시리즈마다 마지막으로 받은 때의 이름 앞부분. 뒤에 시리즈 ID가 붙는다.
        // 3.0.0의 성공한 때의 행(market_mood_refresh)은 지우지도 읽지도 쓰지도 않는다.
        const val FETCHED_STAMP_PREFIX = "market_mood_fetched:"

        // 마지막으로 받기를 시작한 때의 이름(앱을 바꿔도 남는다)
        const val REFRESH_STARTED_STAMP = "market_mood_refresh_started"

        // 마지막으로 받기가 끝난 때의 이름
        const val REFRESH_FINISHED_STAMP = "market_mood_refresh_finished"

        // Twelve Data에 마지막으로 요청을 보낸 때의 이름
        const val TWELVE_DATA_REQUESTED_STAMP = "twelve_data_last_request"

        // 표를 비운 뒤 다시 읽는 횟수
        const val MAX_READ_RETRIES = 1L
    }
}
