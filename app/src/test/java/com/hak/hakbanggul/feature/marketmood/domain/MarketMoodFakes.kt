package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

// 받아 둔 값을 메모리에 두고, 받기의 답을 테스트가 정하는 가짜
class FakeMarketDataRepository(
    stored: StoredMarketData = StoredMarketData(emptyMap()),
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

    // 넘긴 값에 따라 저장을 실패시킬 때(예: 첫 묶음의 저장만)
    var failOnSaveIf: (Map<String, List<DailyValue>>) -> Boolean = { false }

    // requests: 시리즈 ID와 그 시작 날짜. 넘어온 순서 그대로다.
    data class FetchCall(val source: DataSource, val requests: List<Pair<String, LocalDate>>)
    data class SaveCall(val values: Map<String, List<DailyValue>>, val fetchedAt: Instant)

    override fun observeStored(): Flow<StoredMarketData> = stored

    override suspend fun fetch(source: DataSource, requests: List<SeriesRequest>): FetchResult {
        fetchCalls += FetchCall(source, requests.map { it.series.seriesId to it.startDate })
        callOrder += "fetch"
        return fetchAnswer(source, requests)
    }

    override suspend fun save(values: Map<String, List<DailyValue>>, fetchedAt: Instant) {
        callOrder += "save"
        saveCalls += SaveCall(values, fetchedAt)
        if (failOnSave || failOnSaveIf(values)) throw IOException("저장 실패")
        stored.update { current -> StoredMarketData(current.values + values) }
        values.keys.forEach { this.fetchedAt[it] = fetchedAt }
    }

    // 시리즈마다 마지막으로 받은 때. 테스트가 직접 정할 수도 있다.
    val fetchedAt = mutableMapOf<String, Instant>()

    // 켜면 받은 때를 읽지 못한다(빈 맵).
    var failOnReadFetchedAt = false

    override suspend fun getFetchedAt(): Map<String, Instant> =
        if (failOnReadFetchedAt) emptyMap() else fetchedAt.toMap()

    // 받기의 세 때. 테스트가 직접 정할 수도 있다.
    val stamps = mutableMapOf<RefreshStamp, Instant>()
    val saveStampCalls = mutableListOf<Pair<RefreshStamp, Instant>>()

    // 읽지 못하는 때(null을 돌려준다), 적지 못하는 때(IOException)
    val failOnReadStamp = mutableSetOf<RefreshStamp>()
    val failOnSaveStamp = mutableSetOf<RefreshStamp>()

    // fetch, save, saveStamp가 불린 차례. "fetch", "save", "saveStamp(STARTED)"처럼 남긴다.
    val callOrder = mutableListOf<String>()

    // 마지막으로 받기를 시작한 때(STARTED)
    var lastStartedAt: Instant?
        get() = stamps[RefreshStamp.STARTED]
        set(value) {
            if (value == null) stamps.remove(RefreshStamp.STARTED) else stamps[RefreshStamp.STARTED] = value
        }

    override suspend fun getStamp(stamp: RefreshStamp): Instant? =
        if (stamp in failOnReadStamp) null else stamps[stamp]

    override suspend fun saveStamp(stamp: RefreshStamp, at: Instant) {
        callOrder += "saveStamp($stamp)"
        saveStampCalls += stamp to at
        if (stamp in failOnSaveStamp) throw IOException("때 저장 실패: $stamp")
        stamps[stamp] = at
    }

    // 그 때를 적은 값들, 적은 차례대로
    fun savedStamps(stamp: RefreshStamp): List<Instant> = saveStampCalls.filter { it.first == stamp }.map { it.second }

    // 값을 그 데이터로 두고, 그 데이터에 든 시리즈 모두(ETF와 FRED)의 받은 때를 fetchedAt으로 둔다(null이면 비운다).
    fun setStored(builder: MoodTestData.Builder?, fetchedAt: Instant?) {
        val values = builder?.build() ?: emptyMap()
        stored.value = StoredMarketData(values)
        this.fetchedAt.clear()
        setFetchedAt(values.keys, fetchedAt)
    }

    // 그 시리즈들의 받은 때를 at으로 둔다(null이면 지운다).
    fun setFetchedAt(seriesIds: Collection<String>, at: Instant?) {
        seriesIds.forEach { if (at == null) fetchedAt.remove(it) else fetchedAt[it] = at }
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

// 테스트 스케줄러의 가상 시간에 묶은 시계: 시작 시각 + currentTime 밀리초
class SchedulerClock(private val start: Instant, private val scheduler: TestCoroutineScheduler) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = start.plusMillis(scheduler.currentTime)
}

// 기본 나눠 받기: 묶음 하나 7개, 65초 간격, 요청마다 10초 제한
val DEFAULT_PACING = FetchPacing(7, 65.seconds, 10.seconds)
