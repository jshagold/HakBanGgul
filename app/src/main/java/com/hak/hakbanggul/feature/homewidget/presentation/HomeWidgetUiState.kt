package com.hak.hakbanggul.feature.homewidget.presentation

import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.homewidget.domain.WidgetContent
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshButton
import com.hak.hakbanggul.feature.homewidget.domain.WidgetState
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage

// 용어: 홈 화면 위젯
// HomeWidget에 그릴 것. 위젯은 이것만 보고 그린다.
data class HomeWidgetUiState(
    val title: UiText,
    val main: WidgetMain,
    val refreshButton: WidgetRefreshButton,
)

// 첫 줄 아래의 본문
sealed interface WidgetMain {

    // 판정: 단계(색을 고르는 데 쓴다), 단계 이름, 날짜 줄
    data class Stage(val stage: MarketStage, val name: UiText, val asOfText: UiText) : WidgetMain

    // 키 안내, 받지 못함: 안내 문구와 "눌러서 앱 열기"(큰 위젯만 그린다)
    data class Message(val text: UiText, val openAppHint: UiText) : WidgetMain
}

// WidgetState를 위젯에 그릴 것으로 바꾼다.
fun WidgetState.toUiState(): HomeWidgetUiState = when (val c = content) {
    is WidgetContent.Assessment -> HomeWidgetUiState(
        title = UiText(R.string.mood_now),
        // 날짜는 판정의 기준 날짜 그대로다(시간대를 바꾸지 않는다). 해는 넣지 않는다.
        main = WidgetMain.Stage(
            stage = c.stage,
            name = stageName(c.stage),
            asOfText = UiText(R.string.widget_as_of, listOf(c.asOfDate.monthValue, c.asOfDate.dayOfMonth)),
        ),
        refreshButton = refreshButton,
    )
    WidgetContent.KeyGuide -> message(R.string.key_guide_title, refreshButton)
    WidgetContent.NotReceived -> message(R.string.mood_failed_title, refreshButton)
}

private fun message(textRes: Int, button: WidgetRefreshButton) = HomeWidgetUiState(
    title = UiText(R.string.app_name),
    main = WidgetMain.Message(text = UiText(textRes), openAppHint = UiText(R.string.widget_open_app)),
    refreshButton = button,
)

private fun stageName(stage: MarketStage): UiText = UiText(
    when (stage) {
        MarketStage.BULL -> R.string.stage_bull
        MarketStage.NEUTRAL -> R.string.stage_neutral
        MarketStage.WEAK -> R.string.stage_weak
        MarketStage.BEAR -> R.string.stage_bear
    },
)
