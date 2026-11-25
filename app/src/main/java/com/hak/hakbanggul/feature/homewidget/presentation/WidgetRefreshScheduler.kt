package com.hak.hakbanggul.feature.homewidget.presentation

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// 위젯을 위한 받기 작업을 넣고 뺀다. 기기를 껐다 켠 뒤에 주기 작업이 이어지는 것은 WorkManager가 스스로 한다.
@Singleton
class WidgetRefreshScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    // 첫 위젯을 놓음: 주기 작업을 넣되(있으면 그대로) 놓은 때 받는 작업을 하나 넣는다. 연결이 없으면 연결이 돌아온 뒤에 돈다.
    fun onWidgetPlaced() {
        enqueuePeriodic()
        val firstPlaced = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(connected)
            .setInputData(WidgetRefreshWorker.inputFor(WidgetRefreshTrigger.FIRST_PLACED))
            .build()
        workManager.enqueueUniqueWork(FIRST_PLACED_WORK, ExistingWorkPolicy.KEEP, firstPlaced)
    }

    // 놓인 작은 위젯과 큰 위젯을 합쳐 하나도 없을 때에만 주기 작업을 뺀다.
    suspend fun onWidgetRemoved() {
        if (placedCount() == 0) workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    // 새로 고침을 누름: 같은 작업이 있거나 돌고 있으면 넣지 않는다. 연결 조건은 붙이지 않는다(연결이 없으면 곧 실패해 버튼으로 돌아온다).
    // Android 12 이상에서는 급한 작업으로 넣어 절전 중이어도 곧 돌게 한다. 그보다 낮으면 앱이 깨어 있는 동안 곧바로 돈다.
    fun requestManualRefresh() {
        val builder = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(WidgetRefreshWorker.inputFor(WidgetRefreshTrigger.MANUAL))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        workManager.enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, builder.build())
    }

    // 놓인 위젯이 있으면 주기 작업을 넣고(있으면 그대로), 없으면 뺀다. 시스템의 알림을 놓친 경우에 대비해 프로세스가 시작될 때 부른다.
    suspend fun syncPeriodic() {
        if (placedCount() > 0) enqueuePeriodic() else workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    // 약 6시간마다, 연결이 있을 때. 첫 차례도 6시간 뒤다(놓은 때 받는 것은 FIRST_PLACED가 한다).
    private fun enqueuePeriodic() {
        val periodic = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setInitialDelay(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(connected)
            .setInputData(WidgetRefreshWorker.inputFor(WidgetRefreshTrigger.PERIODIC))
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    private suspend fun placedCount(): Int {
        val manager = GlanceAppWidgetManager(context)
        return manager.getGlanceIds(SmallMoodWidget::class.java).size +
            manager.getGlanceIds(LargeMoodWidget::class.java).size
    }

    private companion object {
        // Glance가 쓰는 작업 이름과 겹치지 않게 앱의 이름을 앞에 붙인다.
        const val PERIODIC_WORK = "hakbanggul.homewidget.refresh.periodic"
        const val FIRST_PLACED_WORK = "hakbanggul.homewidget.refresh.first_placed"
        const val MANUAL_WORK = "hakbanggul.homewidget.refresh.manual"
        const val PERIOD_HOURS = 6L
    }
}
