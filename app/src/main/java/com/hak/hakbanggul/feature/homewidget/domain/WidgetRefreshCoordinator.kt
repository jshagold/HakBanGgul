package com.hak.hakbanggul.feature.homewidget.domain

import com.hak.hakbanggul.feature.marketmood.domain.RefreshCondition
import com.hak.hakbanggul.feature.marketmood.domain.RefreshRequestResult
import com.hak.hakbanggul.feature.marketmood.domain.RequestRefreshUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

// 위젯이 시장 분위기에 받아 달라고 청하는 곳. 앱 전체에 하나다.
// 조건(6시간, 1분, 키)을 따지는 것과 받기를 겹쳐 시작하지 않는 것은 시장 분위기가 한다.
@Singleton
class WidgetRefreshCoordinator @Inject constructor(
    private val requestRefresh: RequestRefreshUseCase,
) {
    // 새로 고침을 눌러 받는 중인지. 메모리에만 둔다. 저장하면 받는 도중에 앱이 꺼졌을 때 참인 채로 남는다.
    private val _isManualRefreshing = MutableStateFlow(false)
    val isManualRefreshing: StateFlow<Boolean> = _isManualRefreshing.asStateFlow()

    // 까닭에 맞는 조건으로 청하고, 그것이 돌아올 때까지 기다린 뒤 돌아온다.
    // 취소가 아닌 예외는 밖으로 던지지 않는다.
    suspend fun request(trigger: WidgetRefreshTrigger) {
        when (trigger) {
            WidgetRefreshTrigger.FIRST_PLACED -> requestQuietly(RefreshCondition.WHEN_STALE)
            WidgetRefreshTrigger.PERIODIC -> requestQuietly(RefreshCondition.WHEN_KEYS_AVAILABLE)
            WidgetRefreshTrigger.MANUAL -> requestManual()
            // 앱을 바꾼 때는 놓은 때와 같이 오래되었을 때만 받고, 눌러 받는 중을 바꾸지 않는다.
            WidgetRefreshTrigger.APP_UPDATED -> requestQuietly(RefreshCondition.WHEN_STALE)
        }
    }

    // 눌러 받는 중이면 아무것도 하지 않는다. 보고 바꾸는 것을 한 번에 해 두 누름이 함께 와도 하나만 청한다.
    // 받지 않았으면 1초 뒤에, 그 밖에는 돌아온 때 바로 눌러 받는 중을 푼다. 어떻게 끝나든 푼다.
    private suspend fun requestManual() {
        if (!_isManualRefreshing.compareAndSet(expect = false, update = true)) return
        try {
            val result = requestQuietly(RefreshCondition.WHEN_NOT_RECENTLY_STARTED)
            if (result == RefreshRequestResult.SKIPPED) delay(SKIPPED_HOLD_MILLIS)
        } finally {
            withContext(NonCancellable) { _isManualRefreshing.value = false }
        }
    }

    // 취소가 아닌 예외는 결과 없음(null)으로 돌려준다.
    private suspend fun requestQuietly(condition: RefreshCondition): RefreshRequestResult? =
        try {
            requestRefresh(condition)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private companion object {
        // 받지 않았을 때 진행 표시를 보여 두는 시간
        const val SKIPPED_HOLD_MILLIS = 1_000L
    }
}
