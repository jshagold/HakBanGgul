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

data class MoodUiState(
    // 표시 단계
    val stage: MarketStage,
    val stageName: UiText,
    val asOfText: UiText,
    val compositeScoreText: String,
    val compositeTone: ScoreTone,
    // 계산 단계의 이름
    val computedStageName: UiText,
    // 눈금에서 종합 점수가 놓일 자리. 0은 왼쪽 끝, 1은 오른쪽 끝
    val gaugePosition: Float,
    // 계산 단계가 표시 단계와 다를 때만 있다.
    val pendingNotice: UiText?,
    val groups: List<GroupUiState>,
    // 매매 판단의 두 카드
    val pricePosition: ValuationCardUiState,
    val rateBurden: ValuationCardUiState,
)

// 매매 판단 카드 하나. 카드 이름("주가 위치", "금리 부담")은 화면이 고정 문구로 적는다.
data class ValuationCardUiState(
    // 카드 이름 아래의 글
    val subtitle: UiText,
    // "35 · 보통"
    val scoreText: UiText,
    // 표시 단계. 색을 고르는 데 쓴다.
    val stage: ValuationStage,
    val gauge: ValuationGaugeUiState,
    // 점수로 정한 단계와 표시 단계가 다른 날만 있다.
    val notice: UiText?,
    // 근거 줄, 위에서부터
    val evidence: List<UiText>,
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

data class GroupUiState(
    val name: UiText,
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
