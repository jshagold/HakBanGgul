package com.hak.hakbanggul.feature.homewidget.domain

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import com.hak.hakbanggul.core.network.ApiKeyProvider
import com.hak.hakbanggul.feature.marketmood.domain.MoodAssessment
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMoodUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveRefreshStateUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

// 위젯에 무엇을 보일지 정한다. 위젯은 값을 저장하지 않고, 매번 시장 분위기와 설정이 내주는 값으로 만든다.
class ObserveWidgetStateUseCase @Inject constructor(
    private val observeMood: ObserveMoodUseCase,
    private val observeRefreshState: ObserveRefreshStateUseCase,
    private val apiKeyProvider: ApiKeyProvider,
    private val coordinator: WidgetRefreshCoordinator,
) {
    private data class Inputs(
        val mood: MoodAssessment?,
        val isRefreshing: Boolean,
        val keyMissingOrInvalid: Boolean,
        val isManualRefreshing: Boolean,
    )

    operator fun invoke(): Flow<WidgetState> = channelFlow {
        // 내준 content와 미뤄 둔 받지 못함. 모으는 쪽과 미뤄 둔 일이 다른 스레드에서 돌 수 있어 lock 안에서만 만진다.
        val lock = Mutex()
        var shown: WidgetContent? = null
        var isManualRefreshing = false
        var pendingNotReceived: Job? = null

        suspend fun sendCurrent() {
            val content = shown ?: return
            send(WidgetState(content, buttonFor(content, isManualRefreshing)))
        }

        inputs().collect { input ->
            lock.withLock {
                isManualRefreshing = input.isManualRefreshing
                val current = shown
                // 받는 중이고 판정이 없으면 전에 내준 것을 그대로 둔다. 처음 내주는 값은 표대로다.
                val target = if (current != null && input.isRefreshing && input.mood == null) current else select(input)
                if (target == WidgetContent.NotReceived && current != null && current != WidgetContent.NotReceived) {
                    // 받지 못함으로 바뀌는 것은 0.5초 뒤에도 여전히 받지 못함일 때에만 내준다.
                    if (pendingNotReceived == null) {
                        pendingNotReceived = launch {
                            delay(NOT_RECEIVED_DELAY_MILLIS)
                            lock.withLock {
                                pendingNotReceived = null
                                shown = WidgetContent.NotReceived
                                sendCurrent()
                            }
                        }
                    }
                } else {
                    pendingNotReceived?.cancel()
                    pendingNotReceived = null
                    shown = target
                }
                sendCurrent()
            }
        }
    }.distinctUntilChanged()

    private fun inputs(): Flow<Inputs> {
        // 판정을 읽다 예외가 나면 판정이 없는 것으로 보고, 다른 입력의 변화에는 계속 반응한다.
        val mood: Flow<MoodAssessment?> = observeMood().catch { cause ->
            if (cause is CancellationException) throw cause
            emit(null)
        }
        val keys: Flow<Boolean> = combine(DataSource.entries.map { apiKeyProvider.observeAvailability(it) }) { all ->
            all.any { it == ApiKeyAvailability.MISSING || it == ApiKeyAvailability.INVALID }
        }
        val refreshing: Flow<Boolean> = observeRefreshState().map { it.isRefreshing }
        return combine(mood, refreshing, keys, coordinator.isManualRefreshing) { m, r, k, manual ->
            Inputs(mood = m, isRefreshing = r, keyMissingOrInvalid = k, isManualRefreshing = manual)
        }
    }

    // 표에서 고른다(위 행이 먼저). 판정의 다른 값은 쓰지 않는다.
    private fun select(input: Inputs): WidgetContent = when {
        input.keyMissingOrInvalid -> WidgetContent.KeyGuide
        input.mood != null -> assessment(input.mood)
        else -> WidgetContent.NotReceived
    }

    // 매매 판단은 점수와 표시 단계만 옮긴다. 점수로 정한 단계, 근거 숫자, 경계와 완충은 옮기지 않아
    // 위젯이 쓰지 않는 값이 같은 상태인지의 판단에 섞이지 않게 한다.
    private fun assessment(mood: MoodAssessment): WidgetContent.Assessment = WidgetContent.Assessment(
        stage = mood.displayedStage,
        asOfDate = mood.asOfDate,
        pricePosition = WidgetValuation(mood.valuation.pricePosition.score, mood.valuation.pricePosition.displayedStage),
        rateBurden = WidgetValuation(mood.valuation.rateBurden.score, mood.valuation.rateBurden.displayedStage),
    )

    // 버튼은 그대로 두기와 미루기를 거치지 않고 바로 바뀐다.
    private fun buttonFor(content: WidgetContent, isManualRefreshing: Boolean): WidgetRefreshButton = when {
        isManualRefreshing -> WidgetRefreshButton.IN_PROGRESS
        content == WidgetContent.KeyGuide -> WidgetRefreshButton.DISABLED
        else -> WidgetRefreshButton.ENABLED
    }

    private companion object {
        // 받지 못함으로 바뀌는 것을 미루는 시간
        const val NOT_RECEIVED_DELAY_MILLIS = 500L
    }
}
