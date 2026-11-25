package com.hak.hakbanggul.feature.homewidget.di

import android.content.Context
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshCoordinator
import com.hak.hakbanggul.feature.homewidget.presentation.HomeWidgetStateHolder
import com.hak.hakbanggul.feature.homewidget.presentation.HomeWidgetUpdater
import com.hak.hakbanggul.feature.homewidget.presentation.WidgetRefreshScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// 시스템이 만드는 것(위젯, Receiver, ActionCallback, Worker)이 의존성을 받는 하나뿐인 통로.
// 그 뒤의 클래스는 모두 생성자로 받는다.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeWidgetEntryPoint {
    fun homeWidgetStateHolder(): HomeWidgetStateHolder
    fun widgetRefreshCoordinator(): WidgetRefreshCoordinator
    fun widgetRefreshScheduler(): WidgetRefreshScheduler
    fun homeWidgetUpdater(): HomeWidgetUpdater
}

fun homeWidgetEntryPoint(context: Context): HomeWidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, HomeWidgetEntryPoint::class.java)
