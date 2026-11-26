package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

// 받기 한 번. 출처마다 동시에 받아, 성공한 출처의 값을 한 번에 저장한다.
// 시장 분위기 안에서만 쓴다. 다른 기능은 이것을 직접 쓰지 않고 RequestRefreshUseCase를 쓴다.
class RefreshMarketDataUseCase @Inject constructor(
    private val marketDataRepository: MarketDataRepository,
    private val rulesRepository: MoodRulesRepository,
    private val calculator: MoodCalculator,
    private val clock: Clock,
) {
    suspend operator fun invoke(): RefreshOutcome {
        val now = clock.instant()
        // 출처에 요청을 보내기 전에 시작한 때를 적는다. 적지 못해도 받기는 이어 간다.
        try {
            marketDataRepository.saveLastStartedAt(now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 무시
        }
        val rules = rulesRepository.observe().first()
        // 시리즈마다 받는 때의 미국 동부 시간 날짜에서 그 시리즈의 받는 기간(해)을 뺀 날부터 받는다.
        val today = now.atZone(MARKET_ZONE).toLocalDate()
        val requestsBySource = rules.fetchYearsBySeries()
            .map { (series, years) -> SeriesRequest(series, today.minusYears(years.toLong())) }
            .groupBy { it.series.source }
            .toSortedMap(compareBy { it.ordinal })
        val kindById = requestsBySource.values.flatten().associate { it.series.seriesId to it.series.kind }

        val results: List<FetchResult> = coroutineScope {
            requestsBySource.map { (source, requests) -> async { fetchWithTimeout(source, requests) } }.awaitAll()
        }

        val received = results
            .filterIsInstance<FetchResult.Success>()
            .flatMap { it.values.entries }
            .associate { (id, values) -> id to dropUnclosedDays(values, kindById[id], now) }
        val errors = results.filterIsInstance<FetchResult.Failure>().map { it.error }
        val allSucceeded = errors.isEmpty()

        // 모든 출처가 성공했고, 받아 둔 값에 새 값을 덮은 것으로 판정을 낼 수 있을 때만 성공한 때를 적는다.
        var canAssess = false
        if (allSucceeded) {
            val stored = marketDataRepository.observeStored().first().values
            canAssess = calculator.assess(rules, stored + received) != null
        }

        var saveFailed = false
        if (received.isNotEmpty()) {
            try {
                marketDataRepository.save(received, if (canAssess) now else null)
            } catch (e: IOException) {
                saveFailed = true
            }
        }

        return when {
            DataFetchError.RATE_LIMITED in errors -> RefreshOutcome.Failure(RefreshFailure.RATE_LIMITED)
            DataFetchError.NO_CONNECTION in errors -> RefreshOutcome.Failure(RefreshFailure.NO_CONNECTION)
            allSucceeded && canAssess && !saveFailed -> RefreshOutcome.Success
            else -> RefreshOutcome.Failure(RefreshFailure.OTHER)
        }
    }

    // 출처마다의 받기 전체에 10초 제한을 건다. 넘기면 연결 안 됨과 같게 다룬다.
    private suspend fun fetchWithTimeout(source: DataSource, requests: List<SeriesRequest>): FetchResult =
        try {
            withTimeoutOrNull(FETCH_TIMEOUT_MILLIS) { marketDataRepository.fetch(source, requests) }
                ?: FetchResult.Failure(DataFetchError.NO_CONNECTION)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 저장소가 예외를 던지면 그 출처의 실패로 다룬다. 다른 출처의 받기는 그대로 둔다.
            FetchResult.Failure(DataFetchError.OTHER)
        }

    // 일 종가 시리즈에서만(날마다의 값, 달마다의 값은 거르지 않는다), 받는 때가 그 날짜의 미국 동부 시간 오후 4시보다 앞인 날의 값은 장이 끝나지 않은 값이라 뺀다.
    private fun dropUnclosedDays(values: List<DailyValue>, kind: SeriesKind?, now: Instant): List<DailyValue> {
        if (kind != SeriesKind.DAILY_CLOSE) return values
        return values.filter { !now.isBefore(it.date.atTime(MARKET_CLOSE).atZone(MARKET_ZONE).toInstant()) }
    }

    private companion object {
        val MARKET_ZONE: ZoneId = ZoneId.of("America/New_York")
        val MARKET_CLOSE: LocalTime = LocalTime.of(16, 0)
        const val FETCH_TIMEOUT_MILLIS = 10_000L
    }
}
