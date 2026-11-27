package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// 받아 둔 값과 출처에서 받기
// 시장 분위기 안에서만 쓴다. 다른 기능은 이것을 직접 쓰지 않고 ObserveMoodUseCase, RequestRefreshUseCase, ObserveRefreshStateUseCase를 쓴다.
interface MarketDataRepository {

    fun observeStored(): Flow<StoredMarketData>

    // 출처 하나에서 요청마다 그 시리즈를 그 시작 날짜부터 받는다. 요청을 어떻게 나누는지는 Data 층이 정한다.
    // 요청 가운데 하나라도 받지 못하거나 읽지 못하면 그 출처 전체가 Failure이고, 앞 요청의 오류가 먼저다.
    suspend fun fetch(source: DataSource, requests: List<SeriesRequest>): FetchResult

    // 한 트랜잭션 안에서 넘긴 시리즈마다 받아 둔 값을 넘긴 값으로 통째로 바꾸고, 그 시리즈마다 마지막으로 받은 때를 fetchedAt으로 적는다.
    // 저장하지 못하면 IOException을 던진다.
    suspend fun save(values: Map<String, List<DailyValue>>, fetchedAt: Instant)

    // 시리즈 ID마다 마지막으로 받은 때. 읽지 못하면 빈 맵이다. 예외를 던지지 않는다.
    // 받아 둔 값(StoredMarketData)에 싣지 않는다. 실으면 받을 때마다 한 벌을 다시 계산한다.
    suspend fun getFetchedAt(): Map<String, Instant>

    // 그 때를 돌려준다. 적힌 적이 없거나 읽지 못하면 null이다. 예외를 던지지 않는다.
    suspend fun getStamp(stamp: RefreshStamp): Instant?

    // 그 때를 적는다. 앞서 적힌 것은 바꾼다. 적지 못하면 IOException을 던진다.
    // 받아 둔 값의 Flow는 때가 바뀌어도 새 값을 내보내지 않는다.
    suspend fun saveStamp(stamp: RefreshStamp, at: Instant)
}

// 받아 둔 값. values의 키는 시리즈 ID이고 목록은 날짜 오름차순이다.
data class StoredMarketData(
    val values: Map<String, List<DailyValue>>,
)

// 출처 하나의 받기 결과
sealed interface FetchResult {
    data class Success(val values: Map<String, List<DailyValue>>) : FetchResult
    data class Failure(val error: DataFetchError) : FetchResult
}
