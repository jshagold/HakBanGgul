package com.hak.hakbanggul.feature.homewidget.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hak.hakbanggul.feature.homewidget.di.homeWidgetEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// 앱이 새 버전으로 바뀌었다는 시스템의 알림을 받아, 위젯이 놓여 있으면 받는 작업을 넣는다.
// 여기서 받기를 청하고 기다리지 않는다. 청하는 것은 WidgetRefreshWorker가 넘겨받은 까닭(APP_UPDATED)으로 한다.
class AppUpdatedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val scheduler = homeWidgetEntryPoint(context).widgetRefreshScheduler()
        // 세는 일이 suspend라 알림을 붙잡아 둔 채 끝낸다. 어떻게 끝나든 놓아준다.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduler.onAppUpdated()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 앱을 열거나 새로 고침을 누르거나 다음 주기 작업에 받는다.
            } finally {
                pending.finish()
            }
        }
    }
}
