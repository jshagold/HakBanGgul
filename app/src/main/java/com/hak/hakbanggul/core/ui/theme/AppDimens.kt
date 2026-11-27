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

    // 진행 표시: 고리의 바깥 지름(선 굵기까지 넣은 크기)과 굵기
    val progressTopBar: Dp = 19.dp,
    val progressTopBarStroke: Dp = 2.3.dp,
    val progressCard: Dp = 24.dp,
    val progressCardStroke: Dp = 2.9.dp,
    // 받는 중 카드에서 고리를 가운데에 두는 칸. 시안의 그림 칸 크기다.
    val progressCardSlot: Dp = 28.dp,
    // 섹터 줄의 작은 진행 표시: 칸(시안의 12px 그림), 고리의 바깥 지름과 굵기(viewBox 24, 반지름 9, 선 3을 줄인 값), 글과의 사이
    val progressSectorSlot: Dp = 12.dp,
    val progressSector: Dp = 10.5.dp,
    val progressSectorStroke: Dp = 1.5.dp,
    val progressSectorTextGap: Dp = 4.dp,

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

    // 매매 판단
    // 두 제목 덩어리("지난 5년과 비교하면", "장세의 근거")의 위 여백과 좌우 여백
    val sectionTitlePaddingTop: Dp = 8.dp,
    val sectionTitlePaddingHorizontal: Dp = 4.dp,
    // 매매 판단 카드의 안쪽 여백(네 쪽)
    val valuationCardPadding: Dp = 16.dp,
    // 근거 줄 덩어리의 위 여백(그 위에 구분선)
    val valuationEvidencePaddingTop: Dp = 10.dp,

    // 링크 줄("근거와 다른 기간 ›"): 높이(위에 구분선), 화살표
    val linkRowHeight: Dp = 44.dp,
    val linkArrow: Dp = 16.dp,
    // 링크가 있는 카드의 아래 여백: 장세 카드, 매매 판단 카드(시안의 카드 아래 여백과 링크의 margin-bottom: -8px을 합친 값)
    val regimeCardLinkPaddingBottom: Dp = 4.dp,
    val valuationCardLinkPaddingBottom: Dp = 0.dp,

    // 다른 기간 카드: 안쪽 여백 위아래와 좌우, 카드 안 덩어리 사이, 이름과 기간 글 사이, 카드 사이
    val otherCardPaddingVertical: Dp = 14.dp,
    val otherCardPaddingHorizontal: Dp = 16.dp,
    val otherCardGap: Dp = 10.dp,
    val otherCardNameGap: Dp = 1.dp,
    val otherCardsGap: Dp = 8.dp,

    // 섹터 목록 카드: 안쪽 여백 위, 좌우, 아래, 머리 줄 아래, 칸 사이, 줄 높이(최소), 장세 칸 폭, 주가 위치 칸 폭,
    // 화살표 칸, 상태 글 칸 폭, 이름과 기준 날짜 줄 사이
    val sectorCardPaddingTop: Dp = 12.dp,
    val sectorCardPaddingHorizontal: Dp = 16.dp,
    val sectorCardPaddingBottom: Dp = 4.dp,
    val sectorHeaderPaddingBottom: Dp = 8.dp,
    val sectorColumnGap: Dp = 8.dp,
    val sectorRowMinHeight: Dp = 52.dp,
    val sectorRegimeWidth: Dp = 52.dp,
    val sectorPriceWidth: Dp = 84.dp,
    val sectorArrow: Dp = 16.dp,
    val sectorStatusWidth: Dp = 160.dp,
    val sectorNameDateGap: Dp = 1.dp,

    // 상세 화면과 섹터 화면의 위쪽 줄: 좌우 여백, 뒤로 가기 버튼과 화면 이름 사이(아이콘은 icon, 버튼은 iconButton)
    val detailTopBarPaddingHorizontal: Dp = 4.dp,
    val detailTopBarGap: Dp = 4.dp,

    // 설정 화면의 위쪽 줄: 좌우 여백, 뒤로 가기 버튼과 화면 이름 사이, 뒤로 가기 아이콘
    val settingsTopBarPaddingHorizontal: Dp = 8.dp,
    val settingsTopBarGap: Dp = 4.dp,
    val backIcon: Dp = 24.dp,
    // 설정 화면 본문의 위아래 여백
    val settingsBodyPaddingVertical: Dp = 20.dp,

    // 출처 카드: 안쪽 여백, 카드 안의 덩어리 사이, 출처 이름 쪽과 딱지 사이
    val sourceCardPadding: Dp = 16.dp,
    val sourceCardGap: Dp = 12.dp,
    val sourceCardHeaderGap: Dp = 12.dp,
    // 입력 덩어리의 이름표, 칸, 칸 아래 안내 사이
    val inputGroupGap: Dp = 6.dp,

    // 입력 칸
    val inputHeight: Dp = 48.dp,
    val inputBorder: Dp = 1.dp,
    // 거절됐을 때의 테두리
    val inputBorderError: Dp = 2.dp,
    val inputPaddingHorizontal: Dp = 12.dp,

    // 저장된 키 칸. 키 전체가 보일 때는 높이가 늘어난다(최소 높이).
    val savedKeyMinHeight: Dp = 48.dp,
    val savedKeyBorder: Dp = 1.dp,
    // 틀림일 때의 테두리
    val savedKeyBorderError: Dp = 2.dp,
    val savedKeyPaddingStart: Dp = 12.dp,
    val savedKeyPaddingEnd: Dp = 4.dp,
    // 키 전체가 보일 때 글의 위아래 여백, 글과 버튼 사이
    val savedKeyTextPaddingVertical: Dp = 10.dp,
    val savedKeyTextButtonGap: Dp = 4.dp,

    // 칸 아래 안내: 알림 아이콘, 아이콘과 글 사이, 아이콘 위 여백
    val fieldNoticeIcon: Dp = 18.dp,
    val fieldNoticeIconGap: Dp = 6.dp,
    val fieldNoticeIconTop: Dp = 1.dp,

    // 발급 안내: "키 발급받기"의 위아래 여백, 그 아래 글과의 사이, 오른쪽 버튼과의 사이
    val issueLinkPaddingVertical: Dp = 6.dp,
    val issueNoteGap: Dp = 2.dp,
    val issueButtonGap: Dp = 12.dp,

    // 카드 안의 버튼: 높이, 저장 버튼의 좌우 여백, 키 지우기 버튼의 좌우 여백과 테두리
    val cardButtonHeight: Dp = 44.dp,
    val saveButtonPaddingHorizontal: Dp = 24.dp,
    val deleteButtonPaddingHorizontal: Dp = 16.dp,
    val deleteButtonBorder: Dp = 1.dp,

    // 확인 중 표시: 높이, 좌우 여백, 고리와 글 사이(고리 칸에서 잰다)
    val checkingHeight: Dp = 44.dp,
    val checkingPaddingHorizontal: Dp = 16.dp,
    val checkingGap: Dp = 8.dp,
    // 확인 중 고리: 바깥 지름(선 굵기까지 넣은 크기), 굵기, 고리를 가운데에 두는 칸
    val checkingProgress: Dp = 17.5.dp,
    val checkingProgressStroke: Dp = 2.5.dp,
    val checkingProgressSlot: Dp = 20.dp,

    // 홈 화면 위젯
    // 카드: 테두리와 모서리 반지름(cardBorder, AppShapes.card와 같은 값). 카드 바탕 그림 리소스에 적는다.
    val widgetCardBorder: Dp = 1.dp,
    val widgetCardRadius: Dp = 16.dp,
    // 작은 위젯: 안쪽 여백 위아래와 좌우, 키 안내·받지 못함에서 첫 줄과 안내 문구 사이
    val widgetSmallPaddingVertical: Dp = 8.dp,
    val widgetSmallPaddingHorizontal: Dp = 14.dp,
    val widgetSmallMessageGap: Dp = 2.dp,
    // 큰 위젯: 안쪽 여백(네 쪽 모두)
    val widgetLargePadding: Dp = 16.dp,
    // 작은 위젯의 버튼 자리: 가로, 세로, 카드 위쪽 끝에서, 오른쪽 끝에서. 아이콘 그림 칸
    val widgetSmallButtonWidth: Dp = 40.dp,
    val widgetSmallButtonHeight: Dp = 34.dp,
    val widgetSmallButtonTop: Dp = 0.dp,
    val widgetSmallButtonEnd: Dp = 2.dp,
    val widgetSmallIcon: Dp = 16.dp,
    // 큰 위젯의 버튼 자리: 가로, 세로, 카드 위쪽 끝에서, 오른쪽 끝에서. 아이콘 그림 칸
    val widgetLargeButtonWidth: Dp = 44.dp,
    val widgetLargeButtonHeight: Dp = 44.dp,
    val widgetLargeButtonTop: Dp = 2.dp,
    val widgetLargeButtonEnd: Dp = 2.dp,
    val widgetLargeIcon: Dp = 18.dp,
    // 진행 표시의 그림 칸(아이콘과 같은 칸에 놓인다)
    val widgetSmallProgress: Dp = 16.dp,
    val widgetLargeProgress: Dp = 18.dp,
    // 매매 판단 줄: 작은 위젯의 단계 이름과 두 줄 묶음 사이(가로), 큰 위젯의 단계 이름과 두 줄 묶음 사이(세로),
    // 줄 안의 이름 쪽과 단계 쪽 사이
    val widgetSmallValuationGap: Dp = 10.dp,
    val widgetLargeValuationGap: Dp = 4.dp,
    val widgetValuationNameGap: Dp = 3.dp,
)
