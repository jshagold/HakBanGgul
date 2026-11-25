package com.hak.hakbanggul.feature.homewidget.domain

// 위젯 하나에 보일 것
data class WidgetState(
    val content: WidgetContent,
    val refreshButton: WidgetRefreshButton,
)
