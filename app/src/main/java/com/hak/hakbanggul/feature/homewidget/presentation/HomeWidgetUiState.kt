package com.hak.hakbanggul.feature.homewidget.presentation

import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.homewidget.domain.WidgetContent
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshButton
import com.hak.hakbanggul.feature.homewidget.domain.WidgetState
import com.hak.hakbanggul.feature.homewidget.domain.WidgetValuation
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage

// 용어: 홈 화면 위젯
// HomeWidget에 그릴 것. 위젯은 이것만 보고 그린다.
data class HomeWidgetUiState(
    val title: UiText,
    val main: WidgetMain,
    val refreshButton: WidgetRefreshButton,
)

// 첫 줄 아래의 본문
sealed interface WidgetMain {

    // 판정: 단계(색을 고르는 데 쓴다), 단계 이름, 날짜 줄, 매매 판단 두 줄(주가 위치, 금리 부담 순)
    // 큰 위젯용 글과 작은 위젯용 글을 둘 다 담아, 놓인 위젯이 모두 같은 상태 하나를 받는다.
    data class Stage(
        val stage: MarketStage,
        val name: UiText,
        val asOfText: UiText,
        val pricePosition: WidgetValuationLine,
        val rateBurden: WidgetValuationLine,
    ) : WidgetMain

    // 키 안내, 받지 못함: 안내 문구와 "눌러서 앱 열기"(큰 위젯만 그린다)
    data class Message(val text: UiText, val openAppHint: UiText) : WidgetMain
}

// 용어: 매매 판단 줄
// 매매 판단 줄 하나에 그릴 것
data class WidgetValuationLine(
    // 표시 단계. 위젯이 단계의 색을 고르는 데 쓴다.
    val stage: ValuationStage,
    // 이름. 큰 위젯이 쓴다.
    val name: UiText,
    // 용어: 줄인 이름
    // 작은 위젯이 쓴다.
    val shortName: UiText,
    // 점수와 표시 단계("35 · 보통"). 큰 위젯이 쓴다.
    val scoreText: UiText,
    // 표시 단계의 이름. 작은 위젯이 쓴다.
    val stageName: UiText,
)

// WidgetState를 위젯에 그릴 것으로 바꾼다.
fun WidgetState.toUiState(): HomeWidgetUiState = when (val c = content) {
    is WidgetContent.Assessment -> HomeWidgetUiState(
        title = UiText(R.string.mood_now),
        // 날짜는 판정의 기준 날짜 그대로다(시간대를 바꾸지 않는다). 해는 넣지 않는다.
        main = WidgetMain.Stage(
            stage = c.stage,
            name = stageName(c.stage),
            asOfText = UiText(R.string.widget_as_of, listOf(c.asOfDate.monthValue, c.asOfDate.dayOfMonth)),
            pricePosition = valuationLine(ValuationKind.PRICE_POSITION, c.pricePosition),
            rateBurden = valuationLine(ValuationKind.RATE_BURDEN, c.rateBurden),
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

private enum class ValuationKind { PRICE_POSITION, RATE_BURDEN }

// 점수와 표시 단계를 그대로 적는다. 점수와 단계가 다른 날의 안내 줄과 금리 값의 날짜는 위젯에 적지 않는다.
private fun valuationLine(kind: ValuationKind, valuation: WidgetValuation): WidgetValuationLine {
    val stageName = valuationStageName(kind, valuation.displayedStage)
    return WidgetValuationLine(
        stage = valuation.displayedStage,
        name = UiText(if (kind == ValuationKind.PRICE_POSITION) R.string.price_position_name else R.string.rate_burden_name),
        shortName = UiText(if (kind == ValuationKind.PRICE_POSITION) R.string.widget_price_position_short else R.string.widget_rate_burden_short),
        scoreText = UiText(R.string.valuation_score, listOf(valuation.score, stageName)),
        stageName = stageName,
    )
}

// 표시 단계의 이름. 시장 분위기 화면과 같은 문구를 쓰고, 고르는 코드는 위젯 쪽에 한 벌 둔다.
private fun valuationStageName(kind: ValuationKind, stage: ValuationStage): UiText = UiText(
    when (stage) {
        ValuationStage.MIDDLE -> R.string.valuation_middle
        ValuationStage.LOW -> if (kind == ValuationKind.PRICE_POSITION) R.string.valuation_price_low else R.string.valuation_rate_low
        ValuationStage.HIGH -> if (kind == ValuationKind.PRICE_POSITION) R.string.valuation_price_high else R.string.valuation_rate_high
    },
)
