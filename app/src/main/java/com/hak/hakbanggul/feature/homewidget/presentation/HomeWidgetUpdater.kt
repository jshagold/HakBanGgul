package com.hak.hakbanggul.feature.homewidget.presentation

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.hak.hakbanggul.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

// 위젯의 상태가 바뀌면 놓인 위젯을 다시 그리게 한다. 시스템이 정한 주기로 다시 그리는 것은 쓰지 않는다.
@Singleton
class HomeWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val stateHolder: HomeWidgetStateHolder,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    // 처음 null이 아닌 값이 나온 때와 바뀔 때마다 놓인 두 위젯을 모두 다시 그린다. 여러 번 불려도 한 번만 모은다.
    // 받는 중에 앱이 꺼져 진행 표시가 그려진 채 남았더라도, 앱이 다시 깨어나는 때에 버튼으로 돌아온다.
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            stateHolder.state.filterNotNull().distinctUntilChanged().collect {
                try {
                    updateAll()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 다음 값에서 다시 그린다.
                }
            }
        }
    }

    // 놓인 두 위젯을 지금 상태로 다시 그린다.
    suspend fun updateAll() {
        SmallMoodWidget().updateAll(context)
        LargeMoodWidget().updateAll(context)
    }
}
