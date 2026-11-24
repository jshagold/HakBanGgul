package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.annotation.StringRes
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.ui.KeyBadgeKind
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage

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
