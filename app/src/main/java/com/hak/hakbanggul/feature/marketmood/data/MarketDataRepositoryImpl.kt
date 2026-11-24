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
import com.hak.hakbanggul.feature.marketmood.domain.IndicatorSeries
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRepository
import com.hak.hakbanggul.feature.marketmood.domain.StoredMarketData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    override fun observeStored(): Flow<StoredMarketData> =
        combine(dailyValueDao.observeAll(), syncStampDao.observe(REFRESH_STAMP)) { rows, stamp ->
            StoredMarketData(
                values = rows
                    .groupBy({ it.seriesId }, { DailyValue(LocalDate.parse(it.date), it.value) })
                    .mapValues { (_, values) -> values.sortedBy { it.date } },
                lastSuccessAt = stamp?.let { Instant.ofEpochMilli(it) },
            )
        }
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                clearQuietly()
                attempt < MAX_READ_RETRIES
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                emit(StoredMarketData(values = emptyMap(), lastSuccessAt = null))
            }

    override suspend fun fetch(source: DataSource, series: List<IndicatorSeries>, startDate: LocalDate): FetchResult {
        if (series.isEmpty()) return FetchResult.Success(emptyMap())
        val start = startDate.toString()
        return when (source) {
            DataSource.TWELVE_DATA -> fetchTwelveData(series.map { it.seriesId }, start)
            DataSource.FRED -> fetchFred(series.map { it.seriesId }, start)
        }
    }

    // 네 종목을 한 요청에 넣는다.
    private suspend fun fetchTwelveData(symbols: List<String>, start: String): FetchResult =
        when (val result = executor.execute(DataSource.TWELVE_DATA) { twelveDataApi.timeSeries(symbols.joinToString(","), start) }) {
            is SourceCallResult.Failure -> FetchResult.Failure(result.error)
            is SourceCallResult.Success ->
                mapper.mapTwelveData(result.body, symbols)?.let { FetchResult.Success(it) }
                    ?: FetchResult.Failure(DataFetchError.OTHER)
        }

    // 시리즈마다 요청 하나다. 하나라도 실패하면 그 오류로 출처 전체가 실패다(앞의 시리즈의 오류가 먼저다).
    private suspend fun fetchFred(seriesIds: List<String>, start: String): FetchResult {
        val results = coroutineScope {
            seriesIds.map { id ->
                async {
                    when (val result = executor.execute(DataSource.FRED) { fredApi.observations(id, start) }) {
                        is SourceCallResult.Failure -> FetchResult.Failure(result.error)
                        is SourceCallResult.Success ->
                            mapper.mapFred(result.body)?.let { FetchResult.Success(mapOf(id to it)) }
                                ?: FetchResult.Failure(DataFetchError.OTHER)
                    }
                }
            }.awaitAll()
        }
        results.firstOrNull { it is FetchResult.Failure }?.let { return it }
        return FetchResult.Success(results.filterIsInstance<FetchResult.Success>().flatMap { it.values.entries }.associate { it.toPair() })
    }

    // 한 트랜잭션 안에서 넘긴 시리즈마다 행을 모두 지우고 새로 넣는다. 받아 둔 값에 덧붙이지 않는다.
    override suspend fun save(values: Map<String, List<DailyValue>>, succeededAt: Instant?) {
        try {
            transactionRunner.run {
                values.forEach { (seriesId, list) ->
                    dailyValueDao.deleteBySeries(seriesId)
                    dailyValueDao.insertAll(list.map { DailyValueEntity(seriesId, it.date.toString(), it.value) })
                }
                if (succeededAt != null) {
                    syncStampDao.upsert(SyncStampEntity(REFRESH_STAMP, succeededAt.toEpochMilli()))
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
        // 받기에 마지막으로 성공한 때의 이름
        const val REFRESH_STAMP = "market_mood_refresh"

        // 표를 비운 뒤 다시 읽는 횟수
        const val MAX_READ_RETRIES = 1L
    }
}
