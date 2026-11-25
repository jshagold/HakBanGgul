package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

// 받아 둔 값과 출처에서 받기
// 시장 분위기 안에서만 쓴다. 다른 기능은 이것을 직접 쓰지 않고 ObserveMoodUseCase, RequestRefreshUseCase, ObserveRefreshStateUseCase를 쓴다.
interface MarketDataRepository {

    fun observeStored(): Flow<StoredMarketData>

    // 출처 하나에서 그 시리즈들을 startDate부터 받는다. 하나라도 받지 못하거나 읽지 못하면 그 출처 전체가 Failure다.
    suspend fun fetch(source: DataSource, series: List<IndicatorSeries>, startDate: LocalDate): FetchResult

    // 넘긴 시리즈마다 받아 둔 값을 넘긴 값으로 통째로 바꾸고, succeededAt이 있으면 성공한 때로 적는다.
    // 저장하지 못하면 IOException을 던진다.
    suspend fun save(values: Map<String, List<DailyValue>>, succeededAt: Instant?)

    // 용어: 마지막으로 받기를 시작한 때
    // lastStartedAt을 돌려준다. 적힌 적이 없거나 읽지 못하면 null이다. 예외를 던지지 않는다.
    // 받아 둔 값(StoredMarketData)에 싣지 않는다. 실으면 받기를 시작할 때마다 판정을 다시 계산한다.
    suspend fun getLastStartedAt(): Instant?

    // 마지막으로 받기를 시작한 때를 적는다. 앞서 적힌 것은 바꾼다. 적지 못하면 IOException을 던진다.
    suspend fun saveLastStartedAt(startedAt: Instant)
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
