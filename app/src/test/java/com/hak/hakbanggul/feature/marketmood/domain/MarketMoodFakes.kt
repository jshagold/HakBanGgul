package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// 받아 둔 값을 메모리에 두고, 받기의 답을 테스트가 정하는 가짜
class FakeMarketDataRepository(
    stored: StoredMarketData = StoredMarketData(emptyMap(), null),
) : MarketDataRepository {
    val stored = MutableStateFlow(stored)

    // 출처에서 돌려줄 값. fetchAnswer를 바꾸지 않으면 이 값에서 요청한 시리즈만 꺼내 돌려준다.
    var remote: MoodTestData.Builder = MoodTestData.dataA()
    var fetchAnswer: suspend (DataSource, List<SeriesRequest>) -> FetchResult = { _, requests ->
        FetchResult.Success(remote.build(requests.map { it.series.seriesId }))
    }
    val fetchCalls = mutableListOf<FetchCall>()

    val saveCalls = mutableListOf<SaveCall>()
    var failOnSave = false

    // requests: 시리즈 ID와 그 시작 날짜. 넘어온 순서 그대로다.
    data class FetchCall(val source: DataSource, val requests: List<Pair<String, LocalDate>>)
    data class SaveCall(val values: Map<String, List<DailyValue>>, val succeededAt: Instant?)

    override fun observeStored(): Flow<StoredMarketData> = stored

    override suspend fun fetch(source: DataSource, requests: List<SeriesRequest>): FetchResult {
        fetchCalls += FetchCall(source, requests.map { it.series.seriesId to it.startDate })
        callOrder += "fetch"
        return fetchAnswer(source, requests)
    }

    override suspend fun save(values: Map<String, List<DailyValue>>, succeededAt: Instant?) {
        saveCalls += SaveCall(values, succeededAt)
        if (failOnSave) throw IOException("저장 실패")
        stored.update { current ->
            StoredMarketData(current.values + values, succeededAt ?: current.lastSuccessAt)
        }
    }

    // 마지막으로 받기를 시작한 때. 테스트가 직접 정할 수도 있다.
    var lastStartedAt: Instant? = null
    val saveLastStartedAtCalls = mutableListOf<Instant>()
    var failOnSaveLastStartedAt = false

    // fetch와 saveLastStartedAt이 불린 차례
    val callOrder = mutableListOf<String>()

    override suspend fun getLastStartedAt(): Instant? = lastStartedAt

    override suspend fun saveLastStartedAt(startedAt: Instant) {
        callOrder += "saveLastStartedAt"
        saveLastStartedAtCalls += startedAt
        if (failOnSaveLastStartedAt) throw IOException("시작한 때 저장 실패")
        lastStartedAt = startedAt
    }

    fun setStored(builder: MoodTestData.Builder?, lastSuccessAt: Instant?) {
        stored.value = StoredMarketData(builder?.build() ?: emptyMap(), lastSuccessAt)
    }
}

// 규칙을 테스트가 정하는 가짜. 처음에는 시험용 규칙이다.
class FakeMoodRulesRepository(rules: MoodRules = MoodTestData.RULES) : MoodRulesRepository {
    val rules = MutableStateFlow(rules)
    override fun observe(): Flow<MoodRules> = rules
}

// 지금 시각을 테스트가 정하는 가짜
class FakeClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}
