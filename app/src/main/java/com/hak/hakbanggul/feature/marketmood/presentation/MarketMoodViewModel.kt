package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import com.hak.hakbanggul.core.ui.KeyBadgeKind
import com.hak.hakbanggul.core.ui.SourceLabels
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.GroupAssessment
import com.hak.hakbanggul.feature.marketmood.domain.IndicatorAssessment
import com.hak.hakbanggul.feature.marketmood.domain.IndicatorEvidence
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRefresher
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.MoodAssessment
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMoodUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveSourceKeysUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RefreshFailure
import com.hak.hakbanggul.feature.marketmood.domain.RefreshState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MarketMoodViewModel @Inject constructor(
    observeMood: ObserveMoodUseCase,
    observeSourceKeys: ObserveSourceKeysUseCase,
    private val refresher: MarketDataRefresher,
) : ViewModel() {

    private val formatter = MoodTextFormatter()

    // 판정, 키 쓸 수 있음 여부, 받기 상태를 합쳐 만든다. 값이 처음 오기 전에는 Undecided다.
    val uiState: StateFlow<MarketMoodUiState> =
        combine(observeMood(), observeSourceKeys(), refresher.state, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, INITIAL_STATE)

    fun onRefreshClick() = refresher.refresh()

    fun onRetryClick() = refresher.refresh()

    fun onScreenShown() = refresher.onScreenShown()

    private fun buildState(
        mood: MoodAssessment?,
        keys: Map<DataSource, ApiKeyAvailability>,
        refresh: RefreshState,
    ): MarketMoodUiState {
        val lastFailure = refresh.lastFailure
        val body = when {
            keys.values.any { it != ApiKeyAvailability.AVAILABLE } -> MoodBody.KeyGuide(keyGuideRows(keys))
            mood != null -> MoodBody.Assessment(moodState(mood))
            lastFailure == null || refresh.isRefreshing -> MoodBody.Loading
            else -> MoodBody.Failed(failedMessage(lastFailure))
        }
        val refreshButton = when {
            body is MoodBody.KeyGuide || body is MoodBody.Undecided -> RefreshButtonState.DISABLED
            refresh.isRefreshing -> RefreshButtonState.REFRESHING
            else -> RefreshButtonState.ENABLED
        }
        val banner = if (body is MoodBody.Assessment && lastFailure != null) banner(lastFailure) else null
        return MarketMoodUiState(refreshButton, banner, body)
    }

    private fun keyGuideRows(keys: Map<DataSource, ApiKeyAvailability>): List<KeyGuideRow> =
        keys.map { (source, availability) ->
            KeyGuideRow(
                source = source,
                nameRes = SourceLabels.nameRes(source),
                descriptionRes = SourceLabels.descriptionRes(source),
                badge = when (availability) {
                    ApiKeyAvailability.MISSING -> KeyBadgeKind.NONE
                    ApiKeyAvailability.AVAILABLE -> KeyBadgeKind.SAVED
                    ApiKeyAvailability.INVALID -> KeyBadgeKind.INVALID
                },
            )
        }

    private fun banner(failure: RefreshFailure): BannerUiState = when (failure) {
        RefreshFailure.RATE_LIMITED ->
            BannerUiState(UiText(R.string.banner_rate_limited_title), UiText(R.string.banner_rate_limited_message))
        RefreshFailure.NO_CONNECTION ->
            BannerUiState(UiText(R.string.banner_stale_title), UiText(R.string.banner_no_connection_message))
        RefreshFailure.OTHER ->
            BannerUiState(UiText(R.string.banner_stale_title), UiText(R.string.banner_other_message))
    }

    private fun failedMessage(failure: RefreshFailure): UiText = when (failure) {
        RefreshFailure.RATE_LIMITED -> UiText(R.string.mood_failed_rate_limited)
        RefreshFailure.NO_CONNECTION -> UiText(R.string.mood_failed_no_connection)
        RefreshFailure.OTHER -> UiText(R.string.mood_failed_other)
    }

    private fun moodState(mood: MoodAssessment): MoodUiState = MoodUiState(
        stage = mood.displayedStage,
        stageName = stageName(mood.displayedStage),
        asOfText = UiText(R.string.mood_as_of, listOf(mood.asOfDate.monthValue, mood.asOfDate.dayOfMonth)),
        compositeScoreText = formatter.groupScore(mood.compositeScore),
        compositeTone = formatter.scoreTone(mood.compositeScore, GROUP_DECIMALS),
        computedStageName = stageName(mood.computedStage),
        gaugePosition = gaugePosition(mood.compositeScore, mood.upperBoundary, mood.lowerBoundary),
        pendingNotice = if (mood.computedStage != mood.displayedStage) {
            UiText(
                R.string.mood_pending_notice,
                listOf(stageName(mood.computedStage), stageName(mood.displayedStage), mood.holdDays, mood.computedStageStreak),
            )
        } else {
            null
        },
        groups = mood.groups.map { groupState(it, mood.asOfDate) },
    )

    private fun groupState(group: GroupAssessment, asOfDate: LocalDate): GroupUiState = GroupUiState(
        name = UiText(IndicatorLabels.groupNameRes(group.id)),
        weightText = UiText(R.string.group_weight, listOf(formatter.weightPercent(group.weight))),
        scoreText = formatter.groupScore(group.score),
        scoreTone = formatter.scoreTone(group.score, GROUP_DECIMALS),
        indicators = group.indicators.map { indicatorState(it, asOfDate) },
    )

    private fun indicatorState(indicator: IndicatorAssessment, asOfDate: LocalDate): IndicatorUiState {
        val label = IndicatorLabels.indicator(indicator.id)
        return IndicatorUiState(
            name = UiText(label.nameRes),
            suffix = label.suffixRes?.let { UiText(it) },
            evidence = evidence(indicator.evidence, label.valueFormat, asOfDate),
            scoreText = formatter.itemScore(indicator.score),
            scoreTone = formatter.scoreTone(indicator.score, ITEM_DECIMALS),
        )
    }

    private fun evidence(evidence: IndicatorEvidence, format: IndicatorLabels.ValueFormat?, asOfDate: LocalDate): UiText =
        when (evidence) {
            is IndicatorEvidence.PriceEvidence -> UiText(
                R.string.evidence_price,
                listOf(formatter.signedPercent(evidence.aboveAverageRatio), formatter.signedPercent(evidence.drawdown)),
            )
            is IndicatorEvidence.ValueEvidence -> {
                val valueText = when (format) {
                    IndicatorLabels.ValueFormat.SIGNED_PERCENT_POINT -> formatter.signedPercentPoint(evidence.value)
                    IndicatorLabels.ValueFormat.PERCENT, null -> formatter.percent(evidence.value)
                }
                // 값의 날짜가 기준 날짜와 다를 때만 그 날짜를 붙인다.
                if (evidence.valueDate == asOfDate) {
                    UiText(R.string.evidence_value, listOf(valueText))
                } else {
                    UiText(
                        R.string.evidence_value_with_date,
                        listOf(valueText, evidence.valueDate.monthValue, evidence.valueDate.dayOfMonth),
                    )
                }
            }
        }

    private fun stageName(stage: MarketStage): UiText = UiText(
        when (stage) {
            MarketStage.BULL -> R.string.stage_bull
            MarketStage.NEUTRAL -> R.string.stage_neutral
            MarketStage.WEAK -> R.string.stage_weak
            MarketStage.BEAR -> R.string.stage_bear
        },
    )

    // 네 칸(물장, 약세, 중립, 불장)의 폭이 같고 단계 경계가 칸의 경계에 오도록 칸마다 나눠 계산한다.
    private fun gaugePosition(score: Double, upper: Double, lower: Double): Float {
        val position = when {
            score < lower -> 0.25 * (score + 1) / (lower + 1)
            score < 0.0 -> 0.25 + 0.25 * (score - lower) / (0 - lower)
            score < upper -> 0.5 + 0.25 * score / upper
            else -> 0.75 + 0.25 * (score - upper) / (1 - upper)
        }
        return position.coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        const val ITEM_DECIMALS = 1
        const val GROUP_DECIMALS = 2

        val INITIAL_STATE = MarketMoodUiState(
            refreshButton = RefreshButtonState.DISABLED,
            banner = null,
            body = MoodBody.Undecided,
        )
    }
}
