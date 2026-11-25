package com.hak.hakbanggul.feature.homewidget.presentation

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.hak.hakbanggul.feature.homewidget.di.homeWidgetEntryPoint

// 새로 고침을 누름. 눌러 받는 작업을 넣기만 하고 돌아온다. 이 자리는 시스템이 10초쯤만 기다려 주어 받기를 기다리지 않는다.
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        homeWidgetEntryPoint(context).widgetRefreshScheduler().requestManualRefresh()
    }
}

// 비활성 버튼과 진행 표시의 누름. 누름을 받아 아무 일도 하지 않는다(받지 않으면 카드의 앱 열기로 넘어간다).
class IgnoreWidgetClickAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Unit
}
