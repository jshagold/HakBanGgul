package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

// 받기 한 번. FRED는 한 번에, Twelve Data는 분당 한도에 맞게 묶음으로 나눠 받고, 요청 하나가 성공할 때마다 바로 저장한다.
// 시장 분위기 안에서만 쓴다. 다른 기능은 이것을 직접 쓰지 않고 RequestRefreshUseCase를 쓴다.
class RefreshMarketDataUseCase @Inject constructor(
    private val marketDataRepository: MarketDataRepository,
    private val rulesRepository: MoodRulesRepository,
    private val calculator: MoodCalculator,
    private val pacing: FetchPacing,
    private val clock: Clock,
) {
    // onBroadMarketReceived: 전체 시장이 든 첫 묶음과 FRED를 모두 받아 저장했을 때 한 번 부른다.
    // 돌아오기 직전(성공, 실패, 예외 모두)에 끝난 때를 적는다. 코루틴이 취소된 때는 적지 않는다.
    suspend operator fun invoke(onBroadMarketReceived: () -> Unit = {}): RefreshOutcome {
        val outcome = try {
            refresh(onBroadMarketReceived)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saveStampQuietly(RefreshStamp.FINISHED, clock.instant())
            throw e
        }
        saveStampQuietly(RefreshStamp.FINISHED, clock.instant())
        return outcome
    }

    private suspend fun refresh(onBroadMarketReceived: () -> Unit): RefreshOutcome {
        val now = clock.instant()
        // 출처에 요청을 보내기 전에 시작한 때를 적는다. 적지 못해도 받기는 이어 간다.
        saveStampQuietly(RefreshStamp.STARTED, now)
        val rules = rulesRepository.observe().first()
        // 시리즈마다 받는 때의 미국 동부 시간 날짜에서 그 시리즈의 받는 기간(해)을 뺀 날부터 받는다.
        val today = now.atZone(MARKET_ZONE).toLocalDate()
        val requests = rules.fetchYearsBySeries().map { (series, years) -> SeriesRequest(series, today.minusYears(years.toLong())) }
        // 대상의 종가가 앞에 오므로(targets() 순서) 첫 묶음에 전체 시장이 든다.
        val batches = requests.filter { it.series.source == DataSource.TWELVE_DATA }.chunked(pacing.batchSize.coerceAtLeast(1))
        val fredRequests = requests.filter { it.series.source == DataSource.FRED }

        val state = RefreshRun(
            rules = rules,
            now = now,
            pending = batches.size + if (fredRequests.isEmpty()) 0 else 1,
            fredSaved = fredRequests.isEmpty(),
            onBroadMarketReceived = onBroadMarketReceived,
        )

        coroutineScope {
            if (fredRequests.isNotEmpty()) {
                launch {
                    val result = fetchWithTimeout(DataSource.FRED, fredRequests)
                    state.finish(result, isFred = true, isFirstBatch = false)
                }
            }
            if (batches.isNotEmpty()) {
                launch { fetchTwelveData(batches, state) }
            }
        }
        return state.outcome()
    }

    // 묶음을 차례로 보낸다. 첫 묶음은 Twelve Data에 마지막으로 요청을 보낸 때로부터, 다음 묶음은 앞 묶음을 보낸 때로부터
    // batchInterval이 지난 뒤에 보낸다. 출처가 키를 거절하면 남은 묶음은 보내지 않는다.
    private suspend fun fetchTwelveData(batches: List<List<SeriesRequest>>, state: RefreshRun) {
        var previousSentAt: Instant? = lastTwelveDataRequest()
        for ((index, batch) in batches.withIndex()) {
            waitUntilInterval(previousSentAt)
            val sentAt = clock.instant()
            previousSentAt = sentAt
            saveStampQuietly(RefreshStamp.TWELVE_DATA_REQUESTED, sentAt)
            val result = fetchWithTimeout(DataSource.TWELVE_DATA, batch)
            state.finish(result, isFred = false, isFirstBatch = index == 0)
            if (result is FetchResult.Failure && result.error in KEY_ERRORS) {
                state.skip(batches.size - index - 1)
                return
            }
        }
    }

    // 지금보다 뒤로 적혀 있으면 없는 것으로 본다(기기 시각이 바뀐 때).
    private suspend fun lastTwelveDataRequest(): Instant? {
        val at = try {
            marketDataRepository.getStamp(RefreshStamp.TWELVE_DATA_REQUESTED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return at?.takeUnless { it.isAfter(clock.instant()) }
    }

    // 앞 요청의 때와 지금의 차이로 기다리는 시간을 정한다.
    private suspend fun waitUntilInterval(previousSentAt: Instant?) {
        if (previousSentAt == null) return
        val elapsed = Duration.between(previousSentAt, clock.instant()).toMillis()
        val wait = pacing.batchInterval.inWholeMilliseconds - elapsed
        if (wait > 0) delay(wait)
    }

    // 요청 하나에 제한을 건다. 넘기면 연결 안 됨과 같게 다룬다. 저장소가 예외를 던지면 그 요청의 실패(OTHER)로 다룬다.
    private suspend fun fetchWithTimeout(source: DataSource, requests: List<SeriesRequest>): FetchResult =
        try {
            withTimeoutOrNull(pacing.requestTimeout) { marketDataRepository.fetch(source, requests) }
                ?: FetchResult.Failure(DataFetchError.NO_CONNECTION)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchResult.Failure(DataFetchError.OTHER)
        }

    private suspend fun saveStampQuietly(stamp: RefreshStamp, at: Instant) {
        try {
            marketDataRepository.saveStamp(stamp, at)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 적지 못해도 받기와 결과는 그대로다.
        }
    }

    // 받기 한 번의 진행. 요청이 끝날 때마다 저장하고, 마지막으로 끝난 요청의 저장에 성공한 때를 싣는다.
    private inner class RefreshRun(
        private val rules: MoodRules,
        private val now: Instant,
        // 아직 끝나지 않은 요청의 수
        private var pending: Int,
        private var fredSaved: Boolean,
        private val onBroadMarketReceived: () -> Unit,
    ) {
        private val mutex = Mutex()
        private val kindById = rules.fetchYearsBySeries().keys.associate { it.seriesId to it.kind }
        private val errors = mutableListOf<DataFetchError>()
        private var skipped = false
        private var saveFailed = false
        private var canAssess = false
        private var firstBatchSaved = false
        private var broadMarketNotified = false

        suspend fun finish(result: FetchResult, isFred: Boolean, isFirstBatch: Boolean) = mutex.withLock {
            pending--
            // 장이 끝나지 않은 날 거르기는 그 요청의 응답을 받은 때로 한다.
            val received = when (result) {
                is FetchResult.Success -> result.values.mapValues { (id, values) -> dropUnclosedDays(id, values, clock.instant()) }
                is FetchResult.Failure -> {
                    errors += result.error
                    null
                }
            }
            val last = pending == 0
            val succeededAt = if (last && allSucceeded()) successStampOrNull(received.orEmpty()) else null
            if (received != null && (received.isNotEmpty() || succeededAt != null)) {
                val saved = save(received, succeededAt)
                if (saved && isFred) fredSaved = true
                if (saved && isFirstBatch) firstBatchSaved = true
            }
            if (fredSaved && firstBatchSaved && !broadMarketNotified) {
                broadMarketNotified = true
                onBroadMarketReceived()
            }
        }

        // 보내지 않는 묶음. 모두 성공한 것이 아니므로 성공한 때는 적지 않는다.
        suspend fun skip(count: Int) = mutex.withLock {
            if (count > 0) skipped = true
            pending -= count
        }

        // 원인은 모든 요청에서 모은다: 한도 넘음 > 연결 안 됨 > 그 밖(키 거절, 읽을 수 없음, 저장하지 못함, 판정을 낼 수 없음)
        fun outcome(): RefreshOutcome = when {
            DataFetchError.RATE_LIMITED in errors -> RefreshOutcome.Failure(RefreshFailure.RATE_LIMITED)
            DataFetchError.NO_CONNECTION in errors -> RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION)
            allSucceeded() && canAssess -> RefreshOutcome.Success
            else -> RefreshOutcome.Failure(RefreshFailure.OTHER)
        }

        private fun allSucceeded() = errors.isEmpty() && !skipped && !saveFailed

        // 값을 받아 둔 것에 덮은 것으로 판정을 낼 수 있을 때만 받는 때(받기를 시작한 시각)를 성공한 때로 싣는다.
        private suspend fun successStampOrNull(received: Map<String, List<DailyValue>>): Instant? {
            val stored = marketDataRepository.observeStored().first().values
            canAssess = calculator.assess(rules, stored + received) != null
            return if (canAssess) now else null
        }

        private suspend fun save(values: Map<String, List<DailyValue>>, succeededAt: Instant?): Boolean =
            try {
                marketDataRepository.save(values, succeededAt)
                true
            } catch (e: IOException) {
                saveFailed = true
                canAssess = false
                false
            }

        // 일 종가 시리즈에서만(날마다의 값, 달마다의 값은 거르지 않는다), 받은 때가 그 날짜의 미국 동부 시간 오후 4시보다 앞인 날의 값은
        // 장이 끝나지 않은 값이라 뺀다.
        private fun dropUnclosedDays(id: String, values: List<DailyValue>, receivedAt: Instant): List<DailyValue> {
            if (kindById[id] != SeriesKind.DAILY_CLOSE) return values
            return values.filter { !receivedAt.isBefore(it.date.atTime(MARKET_CLOSE).atZone(MARKET_ZONE).toInstant()) }
        }
    }

    private companion object {
        val MARKET_ZONE: ZoneId = ZoneId.of("America/New_York")
        val MARKET_CLOSE: LocalTime = LocalTime.of(16, 0)
        val KEY_ERRORS = setOf(DataFetchError.KEY_REJECTED, DataFetchError.KEY_INVALID)
    }
}
