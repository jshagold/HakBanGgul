package com.hak.hakbanggul.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 앱의 간격과 크기. 값은 화면 시안에서 뽑았다(1px = 1dp).
@Immutable
data class AppDimens(
    // 화면
    val screenPadding: Dp = 16.dp,
    val cardGap: Dp = 16.dp,

    // 카드
    val cardBorder: Dp = 1.dp,
    // 종합 판정 카드와 안내 카드의 안쪽 여백
    val cardPadding: Dp = 20.dp,
    // 묶음 카드의 위·좌우 여백과 아래 여백
    val groupCardPadding: Dp = 16.dp,
    val groupCardPaddingBottom: Dp = 4.dp,
    // 묶음 카드 머리와 첫 항목 줄 사이
    val groupHeaderPaddingBottom: Dp = 12.dp,
    // 종합 판정 카드 안의 덩어리 사이
    val assessmentGap: Dp = 12.dp,
    // 안내 카드 안의 덩어리 사이
    val guideGap: Dp = 16.dp,
    // 안내 카드의 제목과 설명 사이
    val guideTitleGap: Dp = 6.dp,

    // 줄
    val rowPaddingVertical: Dp = 12.dp,
    val divider: Dp = 1.dp,
    // 이름과 그 아래 글 사이
    val nameGap: Dp = 2.dp,
    // 줄 안의 왼쪽 글과 오른쪽 값 사이
    val rowContentGap: Dp = 12.dp,
    // 아이콘과 글 사이(받지 못함 카드의 알림 아이콘)
    val iconTextGap: Dp = 8.dp,
    // 진행 표시와 글 사이(받는 중 카드)
    val progressTextGap: Dp = 12.dp,

    // 위쪽 줄
    val topBarHeight: Dp = 56.dp,
    val topBarPaddingStart: Dp = 16.dp,
    val topBarPaddingEnd: Dp = 4.dp,
    val iconButton: Dp = 44.dp,
    val icon: Dp = 22.dp,

    // 버튼
    val filledButtonHeight: Dp = 48.dp,
    // 글만 있는 버튼("다시 시도")의 좌우 여백과 높이
    val textButtonPaddingHorizontal: Dp = 10.dp,
    val textButtonHeight: Dp = 44.dp,

    // 딱지
    val badgePaddingVertical: Dp = 4.dp,
    val badgePaddingHorizontal: Dp = 10.dp,

    // 진행 표시
    val progressStroke: Dp = 2.5.dp,
    val progressTopBar: Dp = 22.dp,
    val progressCard: Dp = 28.dp,

    // 안내 띠
    val bannerPaddingStart: Dp = 14.dp,
    val bannerPaddingEnd: Dp = 8.dp,
    val bannerPaddingVertical: Dp = 12.dp,
    val bannerGap: Dp = 8.dp,

    // 다른 날 안내 상자
    val noticePaddingVertical: Dp = 10.dp,
    val noticePaddingHorizontal: Dp = 12.dp,

    // 눈금
    val gaugeHeight: Dp = 18.dp,
    val gaugeBarTop: Dp = 4.dp,
    val gaugeBarHeight: Dp = 10.dp,
    val gaugeMarkerWidth: Dp = 4.dp,
    // 표시 둘레의 흰 테
    val gaugeMarkerOutline: Dp = 2.dp,
    // 막대와 단계 이름 사이
    val gaugeLabelGap: Dp = 6.dp,

    // 맨 아래 안내
    val footerGap: Dp = 8.dp,
    val footerPaddingHorizontal: Dp = 4.dp,
    val footerPaddingTop: Dp = 4.dp,
    val footerLinkPaddingVertical: Dp = 6.dp,
)
