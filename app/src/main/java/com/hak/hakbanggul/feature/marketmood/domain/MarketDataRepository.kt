package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

// 받아 둔 값과 출처에서 받기
interface MarketDataRepository {

    fun observeStored(): Flow<StoredMarketData>

    // 출처 하나에서 그 시리즈들을 startDate부터 받는다. 하나라도 받지 못하거나 읽지 못하면 그 출처 전체가 Failure다.
    suspend fun fetch(source: DataSource, series: List<IndicatorSeries>, startDate: LocalDate): FetchResult

    // 넘긴 시리즈마다 받아 둔 값을 넘긴 값으로 통째로 바꾸고, succeededAt이 있으면 성공한 때로 적는다.
    // 저장하지 못하면 IOException을 던진다.
    suspend fun save(values: Map<String, List<DailyValue>>, succeededAt: Instant?)
}

// 받아 둔 값. values의 키는 시리즈 ID이고 목록은 날짜 오름차순이다. lastSuccessAt은 마지막으로 받기에 성공한 때다.
data class StoredMarketData(
    val values: Map<String, List<DailyValue>>,
    val lastSuccessAt: Instant?,
)

// 출처 하나의 받기 결과
sealed interface FetchResult {
    data class Success(val values: Map<String, List<DailyValue>>) : FetchResult
    data class Failure(val error: DataFetchError) : FetchResult
}
