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
    var fetchAnswer: suspend (DataSource, List<IndicatorSeries>, LocalDate) -> FetchResult = { _, series, _ ->
        FetchResult.Success(remote.build(series.map { it.seriesId }))
    }
    val fetchCalls = mutableListOf<FetchCall>()

    val saveCalls = mutableListOf<SaveCall>()
    var failOnSave = false

    data class FetchCall(val source: DataSource, val seriesIds: List<String>, val startDate: LocalDate)
    data class SaveCall(val values: Map<String, List<DailyValue>>, val succeededAt: Instant?)

    override fun observeStored(): Flow<StoredMarketData> = stored

    override suspend fun fetch(source: DataSource, series: List<IndicatorSeries>, startDate: LocalDate): FetchResult {
        fetchCalls += FetchCall(source, series.map { it.seriesId }, startDate)
        return fetchAnswer(source, series, startDate)
    }

    override suspend fun save(values: Map<String, List<DailyValue>>, succeededAt: Instant?) {
        saveCalls += SaveCall(values, succeededAt)
        if (failOnSave) throw IOException("저장 실패")
        stored.update { current ->
            StoredMarketData(current.values + values, succeededAt ?: current.lastSuccessAt)
        }
    }

    fun setStored(builder: MoodTestData.Builder?, lastSuccessAt: Instant?) {
        stored.value = StoredMarketData(builder?.build() ?: emptyMap(), lastSuccessAt)
    }
}

// 규칙을 테스트가 정하는 가짜. 처음에는 기본 규칙이다.
class FakeMoodRulesRepository(rules: MoodRules = DefaultMoodRules.rules) : MoodRulesRepository {
    val rules = MutableStateFlow(rules)
    override fun observe(): Flow<MoodRules> = rules
}

// 지금 시각을 테스트가 정하는 가짜
class FakeClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}
