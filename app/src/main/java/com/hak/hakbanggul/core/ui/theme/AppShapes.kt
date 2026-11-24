package com.hak.hakbanggul.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 앱의 모서리. 값은 화면 시안에서 뽑았다.
@Immutable
data class AppShapes(
    val card: Shape = RoundedCornerShape(16.dp),
    // 버튼과 입력 칸
    val button: Shape = RoundedCornerShape(10.dp),
    val banner: Shape = RoundedCornerShape(12.dp),
    // 다른 날 안내 상자
    val notice: Shape = RoundedCornerShape(10.dp),
    // 딱지는 알약 모양
    val badge: Shape = RoundedCornerShape(percent = 50),
    // 눈금 막대와 눈금의 표시. Canvas에 그리므로 반지름 값으로 둔다.
    val gaugeBarRadius: Dp = 5.dp,
    val gaugeMarkerRadius: Dp = 2.dp,
)
