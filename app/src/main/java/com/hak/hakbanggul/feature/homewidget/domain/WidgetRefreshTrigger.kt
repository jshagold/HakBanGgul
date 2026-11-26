package com.hak.hakbanggul.feature.homewidget.domain

// 받아 달라고 청하게 된 까닭
enum class WidgetRefreshTrigger {
    // 첫 위젯을 놓음
    FIRST_PLACED,

    // 약 6시간마다
    PERIODIC,

    // 새로 고침을 누름
    MANUAL,

    // 용어: 앱을 바꾼 때
    // 앱이 새 버전으로 바뀜(위젯이 놓여 있을 때)
    APP_UPDATED,
}
