package com.hak.hakbanggul.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// 앱의 글자. 글꼴은 기기의 기본 글꼴을 쓴다. 시안의 1px은 1sp로 옮겼다.
@Immutable
data class AppTypography(
    // 장세의 단계 이름
    val stageName: TextStyle = style(48, FontWeight.Bold, lineHeight = 1.2.em),
    // 위쪽 줄의 앱 이름, 안내 카드의 제목, 묶음 점수
    val title: TextStyle = style(20, FontWeight.Bold),
    // 묶음 이름
    val groupName: TextStyle = style(17, FontWeight.Bold),
    // 항목 점수
    val itemScore: TextStyle = style(16, FontWeight.Bold),
    // 종합 점수 줄
    val body: TextStyle = style(15, FontWeight.Normal),
    // 항목 이름, 출처 이름
    val bodyMedium: TextStyle = style(15, FontWeight.Medium),
    // 버튼 글, 종합 점수 줄의 점수와 단계 이름
    val bodyBold: TextStyle = style(15, FontWeight.Bold),
    // "지금 장은"
    val small: TextStyle = style(14, FontWeight.Normal),
    // 안내 카드의 설명
    val smallLoose: TextStyle = style(14, FontWeight.Normal, lineHeight = 1.6.em),
    // 안내 띠의 첫 줄, "다시 시도"
    val smallBold: TextStyle = style(14, FontWeight.Bold),
    // 기준 날짜, 비중, 근거 숫자, 덧붙이는 글
    val caption: TextStyle = style(13, FontWeight.Normal),
    // 안내 띠의 둘째 줄
    val captionBanner: TextStyle = style(13, FontWeight.Normal, lineHeight = 1.5.em),
    // 다른 날 안내
    val captionNotice: TextStyle = style(13, FontWeight.Normal, lineHeight = 1.55.em),
    // 딱지의 글
    val badge: TextStyle = style(13, FontWeight.Medium),
    // 맨 아래 안내
    val footer: TextStyle = style(12, FontWeight.Normal, lineHeight = 1.6.em),
    // 약관 링크
    val footerLink: TextStyle = style(12, FontWeight.Medium),
    // 눈금 아래의 단계 이름
    val gaugeLabel: TextStyle = style(11, FontWeight.Normal),

    // 설정 화면
    // 입력 칸의 이름표("API 키")
    val inputLabel: TextStyle = style(13, FontWeight.Medium),
    // 가려진 키
    val maskedKey: TextStyle = style(15, FontWeight.Normal, letterSpacing = 1.sp),
    // 키 전체
    val fullKey: TextStyle = style(15, FontWeight.Normal, lineHeight = 1.4.em),
    // "키 발급받기"
    val issueLink: TextStyle = style(14, FontWeight.Medium),
    // "무료, 카드 등록 없음"
    val issueNote: TextStyle = style(12, FontWeight.Normal),

    // 홈 화면 위젯. 줄 높이는 Glance에서 정할 수 없어 두지 않는다.
    // 작은 위젯: 첫 줄과 날짜 줄, 단계 이름, 안내 문구
    val widgetSmallLine: TextStyle = style(12, FontWeight.Normal),
    val widgetSmallStage: TextStyle = style(28, FontWeight.Bold),
    val widgetSmallMessage: TextStyle = style(14, FontWeight.Bold),
    // 큰 위젯: 첫 줄, 단계 이름, 안내 문구, 마지막 줄(날짜, "눌러서 앱 열기")
    val widgetLargeTitle: TextStyle = style(13, FontWeight.Normal),
    val widgetLargeStage: TextStyle = style(48, FontWeight.Bold),
    val widgetLargeMessage: TextStyle = style(17, FontWeight.Bold),
    val widgetLargeFooter: TextStyle = style(12, FontWeight.Normal),
)

private fun style(
    size: Int,
    weight: FontWeight,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)
