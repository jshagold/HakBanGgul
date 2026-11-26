package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.annotation.StringRes
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.ui.KeyBadgeKind
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage

// 용어: 시장 분위기
// 시장 분위기 화면의 상태
data class MarketMoodUiState(
    val refreshButton: RefreshButtonState,
    // 있을 때만 보이는 안내 띠
    val banner: BannerUiState?,
    val body: MoodBody,
)

enum class RefreshButtonState {
    // 누를 수 있음
    ENABLED,

    // 받는 중
    REFRESHING,

    // 비활성
    DISABLED,
}

data class BannerUiState(
    val title: UiText,
    val message: UiText,
)

sealed interface MoodBody {
    // 아직 정해지지 않음
    data object Undecided : MoodBody

    data class KeyGuide(val rows: List<KeyGuideRow>) : MoodBody

    data class Assessment(val mood: MoodUiState) : MoodBody

    data object Loading : MoodBody

    data class Failed(val message: UiText) : MoodBody
}

data class KeyGuideRow(
    val source: DataSource,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val badge: KeyBadgeKind,
)

// 첫 화면의 판정
data class MoodUiState(
    // 전체 시장의 대상 ID. 전체 장세 카드와 주가 위치 카드의 링크가 연다.
    val broadMarketId: String,
    // 전체 장세 카드
    val regime: RegimeCardUiState,
    // 매매 판단 제목("지난 2년과 비교하면")과 그 아래 한 줄
    val valuationTitle: UiText,
    val valuationDescription: UiText,
    // 매매 판단의 두 카드(근거 없음, 링크 있음)
    val pricePosition: ValuationCardUiState,
    val rateBurden: ValuationCardUiState,
    // 섹터 목록
    val sectorHeader: SectorHeaderUiState,
    val sectors: List<SectorRowUiState>,
)

// 장세 카드(첫 화면과 섹터 화면의 장세 카드, 장세 화면의 메인 카드)
data class RegimeCardUiState(
    // 맨 위 왼쪽 글: "전체 장세 · 최근 3개월"
    val title: UiText,
    // 맨 위 오른쪽 글: "11월 23일 종가 기준 (미국)"
    val asOfText: UiText,
    // 표시 단계
    val stage: MarketStage,
    val stageName: UiText,
    val compositeScoreText: String,
    val compositeTone: ScoreTone,
    // 계산 단계의 이름
    val computedStageName: UiText,
    // 눈금에서 종합 점수가 놓일 자리. 0은 왼쪽 끝, 1은 오른쪽 끝
    val gaugePosition: Float,
    // 계산 단계가 표시 단계와 다를 때만 있다.
    val pendingNotice: UiText?,
    // 아래에 "근거와 다른 기간" 링크 줄을 보일지
    val showLink: Boolean,
)

// 매매 판단 카드 하나
data class ValuationCardUiState(
    // 카드 이름: "주가 위치", "금리 부담"
    val name: UiText,
    // 카드 이름 아래의 글: "전체 시장 SPY", "실질금리 2.79%"
    val subtitle: UiText,
    // "35 · 보통"
    val scoreText: UiText,
    // 표시 단계. 색을 고르는 데 쓴다.
    val stage: ValuationStage,
    val gauge: ValuationGaugeUiState,
    // 점수로 정한 단계와 표시 단계가 다른 날만 있다.
    val notice: UiText?,
    // 근거 줄, 위에서부터. 근거를 넣지 않는 카드에서는 빈 목록이다.
    val evidence: List<UiText>,
    // 아래에 "근거와 다른 기간" 링크 줄을 보일지
    val showLink: Boolean,
)

// 매매 판단 카드의 눈금
data class ValuationGaugeUiState(
    // 낮은 칸, 가운데 칸, 높은 칸의 폭(합 1)
    val segments: List<Float>,
    // 세 칸의 이름
    val labels: List<UiText>,
    // 표시의 자리. 0은 왼쪽 끝, 1은 오른쪽 끝
    val position: Float,
)

// 다른 기간 카드
sealed interface OtherPeriodCardUiState {
    // 카드 이름: 장세의 이름, "주가 위치", "금리 부담"
    val name: UiText

    // 이름 아래 기간 글: "최근 6개월 흐름", "지난 1년과 비교"
    val periodText: UiText

    data class Regime(
        override val name: UiText,
        override val periodText: UiText,
        val compositeScoreText: String,
        val compositeTone: ScoreTone,
        val stage: MarketStage,
        val stageName: UiText,
        val gaugePosition: Float,
        val pendingNotice: UiText?,
        // 근거 두 줄
        val evidence: List<UiText>,
    ) : OtherPeriodCardUiState

    data class Valuation(
        override val name: UiText,
        override val periodText: UiText,
        // "8 · 싼 편"
        val scoreText: UiText,
        val stage: ValuationStage,
        val gauge: ValuationGaugeUiState,
        val notice: UiText?,
    ) : OtherPeriodCardUiState

    // 받아 둔 값이 모자라 낼 수 없는 기간
    data class Unavailable(
        override val name: UiText,
        override val periodText: UiText,
        val message: UiText,
    ) : OtherPeriodCardUiState
}

// 섹터 목록의 머리 줄
data class SectorHeaderUiState(
    val nameText: UiText,
    // "장세"와 그 아래 줄의 "3개월"(두 줄)
    val regimeText: UiText,
    // "주가 위치"와 그 아래 줄의 "2년"(두 줄)
    val pricePositionText: UiText,
)

// 섹터 줄 하나
data class SectorRowUiState(
    val targetId: String,
    val name: UiText,
    // ETF 기호: "SMH"
    val symbol: String,
    // 값을 보이는 줄에서 그 섹터의 기준 날짜가 전체 시장의 것과 다를 때만 있다.
    val asOfText: UiText?,
    val content: SectorRowContent,
    val clickable: Boolean,
)

sealed interface SectorRowContent {
    // 장세의 표시 단계와 주가 위치의 표시 단계와 "9 · 싼 편"
    data class Values(
        val regimeStage: MarketStage,
        val regimeStageName: UiText,
        val valuationStage: ValuationStage,
        val valuationText: UiText,
    ) : SectorRowContent

    // "받는 중" 또는 "받지 못함"
    data class Status(val text: UiText) : SectorRowContent
}

data class GroupUiState(
    val name: UiText,
    // 덧붙이는 글: "전체 시장 SPY · 비중 70%", "비중 30%"
    val weightText: UiText,
    val scoreText: String,
    val scoreTone: ScoreTone,
    val indicators: List<IndicatorUiState>,
)

data class IndicatorUiState(
    val name: UiText,
    val suffix: UiText?,
    val evidence: UiText,
    val scoreText: String,
    val scoreTone: ScoreTone,
)
