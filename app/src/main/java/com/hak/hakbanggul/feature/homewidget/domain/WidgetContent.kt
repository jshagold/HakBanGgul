package com.hak.hakbanggul.feature.homewidget.domain

import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import java.time.LocalDate

// 홈 화면 위젯이 보여 주는 것 셋
sealed interface WidgetContent {

    // 판정. 표시 단계와 기준 날짜만 쓴다.
    data class Assessment(val stage: MarketStage, val asOfDate: LocalDate) : WidgetContent

    // 용어: 키 안내
    // 출처 중 하나라도 키가 없거나 틀림
    data object KeyGuide : WidgetContent

    // 용어: 받지 못함
    // 키는 모두 쓸 수 있는데 판정을 낼 값이 없음
    data object NotReceived : WidgetContent
}
