package com.hak.hakbanggul

import android.app.Application
import com.hak.hakbanggul.core.di.ApplicationScope
import com.hak.hakbanggul.feature.homewidget.presentation.HomeWidgetUpdater
import com.hak.hakbanggul.feature.homewidget.presentation.WidgetRefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

// 앱 전체의 Hilt 조립 시작점
@HiltAndroidApp
class HakBanGgulApp : Application() {

    @Inject
    lateinit var homeWidgetUpdater: HomeWidgetUpdater

    @Inject
    lateinit var widgetRefreshScheduler: WidgetRefreshScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    // 프로세스가 시작될 때 위젯을 지금 상태로 다시 그리게 하고, 위젯 주기 작업을 놓인 위젯에 맞춘다. 받기는 시작하지 않는다.
    override fun onCreate() {
        super.onCreate()
        homeWidgetUpdater.start()
        applicationScope.launch {
            try {
                widgetRefreshScheduler.syncPeriodic()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 다음 프로세스 시작 때 다시 맞춘다.
            }
        }
    }
}
