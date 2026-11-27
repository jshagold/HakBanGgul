package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.di.ApplicationScope
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

// 받기 관리자. 앱 전체에 하나만 있고, 받기를 앱 범위로 돌려 화면을 벗어나도 끝까지 받아 저장한다.
// 처음 쓰일 때 만들어진다. 화면 없이 만들어져도(다른 기능 때문에 깨어난 때) request로 받을 수 있다.
// 시장 분위기 안에서만 쓴다. 다른 기능은 이것을 직접 쓰지 않고 RequestRefreshUseCase, ObserveRefreshStateUseCase를 쓴다.
@Singleton
class MarketDataRefresher @Inject constructor(
    private val refreshMarketData: RefreshMarketDataUseCase,
    private val observeMood: ObserveMoodUseCase,
    private val observeSourceKeys: ObserveSourceKeysUseCase,
    private val marketDataRepository: MarketDataRepository,
    private val rulesRepository: MoodRulesRepository,
    private val freshness: FetchFreshness,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    // 메모리에만 둔다. 앱을 다시 켜면 실패가 남아 있지 않다.
    private val _state = MutableStateFlow(RefreshState(isRefreshing = false, lastFailure = null))
    val state: StateFlow<RefreshState> = _state.asStateFlow()

    // 받는 중인 받기가 끝나면 완료된다. 받는 중이 아니면 null이다. lock 안에서만 읽고 쓴다.
    private val lock = Any()
    private var running: CompletableDeferred<Unit>? = null

    init {
        // 출처의 키가 모두 AVAILABLE이 아니던 것이 모두 AVAILABLE로 바뀌면 받는다. 처음 받은 값은 바뀐 것으로 보지 않는다.
        // 화면이 안 보이는 동안(설정 화면에서 키를 저장하는 때)에도 알아야 해 여기에 둔다.
        scope.launch {
            var wasAllAvailable: Boolean? = null
            observeSourceKeys().collect { keys ->
                val allAvailable = keys.allAvailable()
                if (wasAllAvailable == false && allAvailable) refresh()
                wasAllAvailable = allAvailable
            }
        }
    }

    // 받는 중이 아니면 받기를 시작한다. 받는 중이면 아무것도 하지 않는다. 받기가 끝나기를 기다리지 않는다.
    fun refresh() {
        startOrJoin()
    }

    // 화면이 보이게 될 때 받을지 정한다.
    fun onScreenShown() {
        scope.launch {
            if (meets(RefreshCondition.WHEN_STALE)) refresh()
        }
    }

    // 받아 달라는 청. 받는 중이면 조건을 따지지 않고 그 받기가 끝나기를 기다린다(JOINED).
    // 아니면 조건을 따져, 맞지 않으면 곧바로 SKIPPED, 맞으면 받기를 시작해 끝나기를 기다린다(REFRESHED).
    // 받기는 앱 범위에서 돌아, 부른 쪽이 취소되어도 끝까지 이어진다. 받기가 실패해도 예외를 던지지 않는다.
    suspend fun request(condition: RefreshCondition): RefreshRequestResult {
        synchronized(lock) { running }?.let {
            it.await()
            return RefreshRequestResult.JOINED
        }
        if (!meets(condition)) return RefreshRequestResult.SKIPPED
        // 조건을 따지는 사이에 다른 받기가 시작됐으면 그 받기를 기다린다.
        val (run, started) = startOrJoin()
        run.await()
        return if (started) RefreshRequestResult.REFRESHED else RefreshRequestResult.JOINED
    }

    // 받는 중이면 그 받기를, 아니면 새로 시작한 받기를 돌려준다. 두 번째 값은 새로 시작했는지다.
    // 받는 중인지 보는 것과 받는 중으로 두는 것을 lock 안에서 한 번에 한다.
    private fun startOrJoin(): Pair<CompletableDeferred<Unit>, Boolean> {
        val run = synchronized(lock) {
            running?.let { return it to false }
            CompletableDeferred<Unit>().also {
                running = it
                _state.value = _state.value.copy(isRefreshing = true, broadMarketReceived = false)
            }
        }
        // ATOMIC: 시작 전에 취소되어도 finally까지 돌아 받는 중이 풀린다.
        scope.launch(start = CoroutineStart.ATOMIC) {
            var outcome: RefreshOutcome = RefreshOutcome.Failure(RefreshFailure.OTHER)
            try {
                // 이 받기가 아직 받는 중일 때만 전체 시장의 값을 받음으로 둔다. 묶음 사이를 기다리는 동안에도 받는 중이다.
                outcome = refreshMarketData {
                    synchronized(lock) {
                        if (running === run) _state.value = _state.value.copy(broadMarketReceived = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 받기 도중의 예기치 못한 예외로 앱이 멈추지 않게 그 밖의 실패로 다룬다.
                outcome = RefreshOutcome.Failure(RefreshFailure.OTHER)
            } finally {
                val failure = (outcome as? RefreshOutcome.Failure)?.reason
                synchronized(lock) {
                    running = null
                    // 끝난 때는 RefreshMarketDataUseCase가 돌아오기 전에 적었다. 이 순서를 바꾸지 않는다.
                    _state.value = RefreshState(isRefreshing = false, lastFailure = failure, broadMarketReceived = false)
                }
                run.complete(Unit)
            }
        }
        return run to true
    }

    // 받는 조건을 따지는 곳은 여기 하나다. onScreenShown도 WHEN_STALE로 이것을 쓴다.
    private suspend fun meets(condition: RefreshCondition): Boolean {
        if (!observeSourceKeys().first().allAvailable()) return false
        val now = clock.instant()
        return when (condition) {
            RefreshCondition.WHEN_STALE -> {
                val mood = observeMood().first()
                val stored = marketDataRepository.observeStored().first()
                val rules = rulesRepository.observe().first()
                val fetchedAt = marketDataRepository.getFetchedAt()
                // 받아 둔 종가가 없는 대상도 최신이 아니다.
                val staleEtf = freshness.staleTargets(rules, stored.values, fetchedAt, now).isNotEmpty()
                mood == null || staleEtf || isOver(freshness.fredFetchedAt(rules, fetchedAt), now, STALE_AFTER)
            }
            RefreshCondition.WHEN_KEYS_AVAILABLE -> true
            RefreshCondition.WHEN_NOT_RECENTLY_STARTED -> {
                // 시작한 때와 끝난 때 가운데 늦은 때. 받는 중에 앱이 꺼지면 끝난 때가 앞선 받기의 것일 수 있다.
                val started = marketDataRepository.getStamp(RefreshStamp.STARTED)
                val finished = marketDataRepository.getStamp(RefreshStamp.FINISHED)
                isOver(listOfNotNull(started, finished).maxOrNull(), now, RESTART_AFTER)
            }
        }
    }

    // since에서 limit을 넘었는지. 꼭 limit이면 넘지 않은 것이다. since가 없거나 지금보다 뒤면 넘은 것으로 본다.
    private fun isOver(since: Instant?, now: Instant, limit: Duration): Boolean =
        since == null || since.isAfter(now) || Duration.between(since, now) > limit

    private fun Map<*, ApiKeyAvailability>.allAvailable(): Boolean =
        isNotEmpty() && values.all { it == ApiKeyAvailability.AVAILABLE }

    private companion object {
        // FRED 시리즈의 받은 때 가운데 가장 이른 때에서 이만큼을 넘으면 새로 받는다.
        val STALE_AFTER: Duration = Duration.ofHours(6)

        // WHEN_NOT_RECENTLY_STARTED: 시작한 때와 끝난 때 가운데 늦은 때에서 이만큼을 넘으면 받는다.
        val RESTART_AFTER: Duration = Duration.ofMinutes(1)
    }
}
