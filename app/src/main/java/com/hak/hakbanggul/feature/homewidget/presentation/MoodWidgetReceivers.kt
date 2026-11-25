package com.hak.hakbanggul.feature.homewidget.presentation

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.hak.hakbanggul.feature.homewidget.di.homeWidgetEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// 작은 위젯의 Receiver
class SmallMoodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallMoodWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        onFirstPlaced(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        onLastRemoved(context, goAsync())
    }
}

// 큰 위젯의 Receiver
class LargeMoodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeMoodWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        onFirstPlaced(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        onLastRemoved(context, goAsync())
    }
}

// 그 위젯의 첫 개가 놓였다.
private fun onFirstPlaced(context: Context) {
    homeWidgetEntryPoint(context).widgetRefreshScheduler().onWidgetPlaced()
}

// 그 위젯의 마지막 개가 치워졌다. 세는 일이 suspend라 알림을 붙잡아 둔 채 끝낸다.
private fun onLastRemoved(context: Context, pending: android.content.BroadcastReceiver.PendingResult) {
    val scheduler = homeWidgetEntryPoint(context).widgetRefreshScheduler()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            scheduler.onWidgetRemoved()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 다음 프로세스 시작 때 syncPeriodic이 맞춘다.
        } finally {
            pending.finish()
        }
    }
}
