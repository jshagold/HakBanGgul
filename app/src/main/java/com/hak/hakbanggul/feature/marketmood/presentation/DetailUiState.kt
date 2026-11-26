package com.hak.hakbanggul.feature.marketmood.presentation

import com.hak.hakbanggul.core.ui.UiText

// 용어: 상세 화면
// 상세 화면(장세, 주가 위치, 금리 부담)과 섹터 화면의 상태. 새로 고침, 안내 띠, 받는 중은 두지 않는다.
sealed interface DetailUiState<out T> {
    // 한 벌과 키 쓸 수 있음 여부를 받기 전
    data object Undecided : DetailUiState<Nothing>

    data class Shown<T>(val content: T) : DetailUiState<T>

    // 키가 모두 쓸 수 있지 않거나, 한 벌이 없거나, 대상을 찾지 못하거나, 메인 콘텐츠를 낼 수 없음
    data object BackToMain : DetailUiState<Nothing>
}

// 장세 화면
data class RegimeDetailUiState(
    // 화면 이름: "전체 장세", "반도체 장세"
    val title: UiText,
    // 섹터만: "SMH"
    val symbol: String?,
    // 메인 카드(링크 없음)
    val main: RegimeCardUiState,
    // 근거 아래 한 줄: "최근 3개월 기준"
    val evidenceBasis: UiText,
    val groups: List<GroupUiState>,
    val otherPeriods: List<OtherPeriodCardUiState>,
)

// 주가 위치 화면
data class PricePositionDetailUiState(
    // 위쪽 줄의 대상 글: "전체 시장 SPY"
    val targetText: UiText,
    val valuationTitle: UiText,
    val valuationDescription: UiText,
    // 메인 카드(근거 두 줄, 링크 없음)
    val main: ValuationCardUiState,
    val otherPeriods: List<OtherPeriodCardUiState>,
)

// 금리 부담 화면
data class RateBurdenDetailUiState(
    val valuationTitle: UiText,
    val valuationDescription: UiText,
    // 메인 카드(근거 세 줄, 링크 없음)
    val main: ValuationCardUiState,
    val otherPeriods: List<OtherPeriodCardUiState>,
)

// 섹터 화면
data class SectorUiState(
    val targetId: String,
    val name: UiText,
    val symbol: String,
    // 장세 카드(링크 있음)
    val regime: RegimeCardUiState,
    val valuationTitle: UiText,
    val valuationDescription: UiText,
    // 주가 위치 카드(근거 없음, 링크 있음)
    val pricePosition: ValuationCardUiState,
)
