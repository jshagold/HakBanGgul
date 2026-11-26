package com.hak.hakbanggul.feature.homewidget.domain

import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage

// 용어: 위젯의 매매 판단 값
// 매매 판단 하나(주가 위치 또는 금리 부담)에서 위젯이 쓰는 값. 점수(0~100)와 표시 단계만 옮긴다.
data class WidgetValuation(
    val score: Int,
    val displayedStage: ValuationStage,
)
