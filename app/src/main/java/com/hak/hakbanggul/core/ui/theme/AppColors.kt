package com.hak.hakbanggul.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// 앱의 색. 값은 화면 시안에서 뽑았다. 밝은 화면 하나만 둔다.
@Immutable
data class AppColors(
    // 화면 바탕, 다른 날 안내 상자의 바탕, 저장된 키 칸의 바탕
    val background: Color = Color(0xFFF4F5F7),
    // 카드, 위쪽 줄
    val card: Color = Color(0xFFFFFFFF),
    // 카드 테두리, 구분선, 위쪽 줄 아래 선, 진행 표시의 바탕 고리
    val border: Color = Color(0xFFE3E6EA),
    // 기본 글, 아이콘, 눈금의 표시
    val text: Color = Color(0xFF1B1F24),
    // 덧붙이는 글, 근거 숫자, 날짜, 맨 아래 안내
    val textMuted: Color = Color(0xFF4A5360),
    // 누를 수 없는 아이콘, 테두리만 있는 버튼의 테두리
    val disabled: Color = Color(0xFF8A93A0),
    // 채운 버튼의 바탕, 링크, 진행 표시의 도는 부분
    val accent: Color = Color(0xFF2457C5),
    // 채운 버튼 위의 글
    val onAccent: Color = Color(0xFFFFFFFF),
    // 안내 띠의 글, 알림 아이콘
    val warningText: Color = Color(0xFF9F1D14),
    // 안내 띠의 바탕
    val warningBackground: Color = Color(0xFFFCE9E6),
    // 입력 칸의 오류 테두리
    val errorBorder: Color = Color(0xFFB3261E),

    // 장세의 단계 이름
    val stageBull: Color = Color(0xFFC5221F),
    val stageNeutral: Color = Color(0xFF4A5360),
    val stageWeak: Color = Color(0xFF3D7BD9),
    val stageBear: Color = Color(0xFF1D4AA8),

    // 눈금의 네 칸
    val gaugeBear: Color = Color(0xFF1D4AA8),
    val gaugeWeak: Color = Color(0xFF8AB4F0),
    val gaugeNeutral: Color = Color(0xFFB7BEC8),
    val gaugeBull: Color = Color(0xFFD93025),

    // 항목 점수, 묶음 점수, 종합 점수
    val scorePositive: Color = Color(0xFFC5221F),
    val scoreZero: Color = Color(0xFF4A5360),
    val scoreNegative: Color = Color(0xFF1D4AA8),

    // 키의 상태 딱지
    val badgeNoneBackground: Color = Color(0xFFE9ECF0),
    val badgeNoneText: Color = Color(0xFF4A5360),
    val badgeSavedBackground: Color = Color(0xFFE4ECFB),
    val badgeSavedText: Color = Color(0xFF1D4AA8),
    val badgeInvalidBackground: Color = Color(0xFFFCE9E6),
    val badgeInvalidText: Color = Color(0xFF9F1D14),

    // 설정 화면. 값이 위의 색과 같아도 쓰는 곳이 달라 따로 둔다.
    // 비활성 입력 칸의 테두리와 바탕
    val inputDisabledBorder: Color = Color(0xFFC9CED6),
    val inputDisabledBackground: Color = Color(0xFFECEEF1),
    // 비활성 채운 버튼의 바탕
    val filledButtonDisabledBackground: Color = Color(0xFFE3E6EA),
    // 확인 중 표시의 바탕, 글과 고리의 도는 부분, 고리의 바탕 고리
    val checkingBackground: Color = Color(0xFFE4ECFB),
    val checkingContent: Color = Color(0xFF1D4AA8),
    val checkingTrack: Color = Color(0xFFB9CBEF),

    // 매매 판단. 값이 위의 색과 같아도 쓰는 곳이 달라 따로 둔다. 홈 화면 위젯도 쓴다.
    // 단계의 글(점수와 단계 이름): 싼 편·낮음, 보통, 비싼 편·높음
    val valuationLow: Color = Color(0xFF0E7C6B),
    val valuationMiddle: Color = Color(0xFF4A5360),
    val valuationHigh: Color = Color(0xFFB45309),
    // 눈금의 세 칸: 싼 편·낮음, 보통, 비싼 편·높음
    val valuationGaugeLow: Color = Color(0xFF5FB8A8),
    val valuationGaugeMiddle: Color = Color(0xFFB7BEC8),
    val valuationGaugeHigh: Color = Color(0xFFE9A23B),

    // 섹터 줄의 "›"(disabled와 값이 조금 다르다). "근거와 다른 기간 ›"의 글과 화살표는 accent를 쓴다.
    val sectorChevron: Color = Color(0xFF8A929C),

    // 홈 화면 위젯. 값이 gaugeNeutral과 같아도 쓰는 곳이 달라 따로 둔다.
    // 비활성 새로 고침 아이콘
    val widgetRefreshDisabled: Color = Color(0xFFB7BEC8),
)
