package com.hak.hakbanggul.feature.homewidget.presentation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hak.hakbanggul.feature.homewidget.di.homeWidgetEntryPoint
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshButton
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshTrigger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

// 위젯을 위해 받아 달라고 청하는 작업. 청이 끝나면 놓인 위젯을 다시 그린다.
// 받기가 실패해도, 예외가 나도 성공으로 끝낸다. 실패로 알려 다시 돌리면 한도를 넘은 때에 요청이 더 나간다. 다음 차례는 주기 작업이다.
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val trigger = inputData.getString(KEY_TRIGGER)
            ?.let { name -> WidgetRefreshTrigger.entries.firstOrNull { it.name == name } }
            ?: return Result.success()
        val entry = homeWidgetEntryPoint(applicationContext)
        try {
            entry.widgetRefreshCoordinator().request(trigger)
            if (trigger == WidgetRefreshTrigger.MANUAL) {
                // 진행 표시가 풀린 것이 위젯의 상태에 반영된 뒤에 다시 그린다.
                withTimeoutOrNull(STATE_WAIT_MILLIS) {
                    entry.homeWidgetStateHolder().state.first { it != null && it.refreshButton != WidgetRefreshButton.IN_PROGRESS }
                }
            }
            entry.homeWidgetUpdater().updateAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 성공으로 끝낸다.
        }
        return Result.success()
    }

    companion object {
        private const val KEY_TRIGGER = "trigger"

        // 진행 표시가 풀린 것을 기다리는 가장 긴 시간
        private const val STATE_WAIT_MILLIS = 5_000L

        fun inputFor(trigger: WidgetRefreshTrigger): Data = workDataOf(KEY_TRIGGER to trigger.name)
    }
}
