package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.di.ApplicationScope
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

// 받기 관리자. 앱 전체에 하나만 있고, 받기를 앱 범위로 돌려 화면을 벗어나도 끝까지 받아 저장한다.
@Singleton
class MarketDataRefresher @Inject constructor(
    private val refreshMarketData: RefreshMarketDataUseCase,
    private val observeMood: ObserveMoodUseCase,
    private val observeSourceKeys: ObserveSourceKeysUseCase,
    private val marketDataRepository: MarketDataRepository,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    // 메모리에만 둔다. 앱을 다시 켜면 실패가 남아 있지 않다.
    private val _state = MutableStateFlow(RefreshState(isRefreshing = false, lastFailure = null))
    val state: StateFlow<RefreshState> = _state.asStateFlow()

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

    // 받는 중이 아니면 받기를 시작한다. 받는 중이면 아무것도 하지 않는다.
    fun refresh() {
        // 받는 중인지 보는 것과 받는 중으로 두는 것을 한 번에 한다.
        while (true) {
            val current = _state.value
            if (current.isRefreshing) return
            if (_state.compareAndSet(current, current.copy(isRefreshing = true))) break
        }
        scope.launch {
            var outcome: RefreshOutcome = RefreshOutcome.Failure(RefreshFailure.OTHER)
            try {
                outcome = refreshMarketData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 받기 도중의 예기치 못한 예외로 앱이 멈추지 않게 그 밖의 실패로 다룬다.
                outcome = RefreshOutcome.Failure(RefreshFailure.OTHER)
            } finally {
                val failure = (outcome as? RefreshOutcome.Failure)?.reason
                _state.update { RefreshState(isRefreshing = false, lastFailure = failure) }
            }
        }
    }

    // 화면이 보이게 될 때 받을지 정한다.
    fun onScreenShown() {
        scope.launch {
            if (!observeSourceKeys().first().allAvailable()) return@launch
            val mood = observeMood().first()
            val lastSuccessAt = marketDataRepository.observeStored().first().lastSuccessAt
            val now = clock.instant()
            val shouldRefresh = mood == null ||
                lastSuccessAt == null ||
                Duration.between(lastSuccessAt, now) > STALE_AFTER ||
                lastSuccessAt.isAfter(now)
            if (shouldRefresh) refresh()
        }
    }

    private fun Map<*, ApiKeyAvailability>.allAvailable(): Boolean =
        isNotEmpty() && values.all { it == ApiKeyAvailability.AVAILABLE }

    private companion object {
        // 마지막 성공에서 이만큼을 넘으면 새로 받는다. 꼭 6시간이면 넘지 않은 것이다.
        val STALE_AFTER: Duration = Duration.ofHours(6)
    }
}
