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
import com.hak.hakbanggul.feature.marketmood.domain.PricePositionAssessment
import com.hak.hakbanggul.feature.marketmood.domain.RateBurdenAssessment
import com.hak.hakbanggul.feature.marketmood.domain.ValuationAssessment
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
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
        pricePosition = pricePositionCard(mood.valuation),
        rateBurden = rateBurdenCard(mood.valuation, mood.asOfDate),
    )

    // 매매 판단의 두 카드

    private enum class ValuationCard { PRICE_POSITION, RATE_BURDEN }

    private fun pricePositionCard(valuation: ValuationAssessment): ValuationCardUiState {
        val price: PricePositionAssessment = valuation.pricePosition
        return valuationCard(
            card = ValuationCard.PRICE_POSITION,
            valuation = valuation,
            subtitle = UiText(R.string.price_position_subtitle),
            score = price.score,
            scoredStage = price.scoredStage,
            displayedStage = price.displayedStage,
            evidence = listOf(
                UiText(
                    R.string.price_evidence_average,
                    listOf(formatter.signedPercent(price.shortDeviation), formatter.signedPercent(price.longDeviation)),
                ),
                UiText(
                    R.string.price_evidence_drawdown,
                    listOf(formatter.signedPercent(price.drawdown), formatter.signedPercent(price.threeMonthReturn)),
                ),
            ),
        )
    }

    private fun rateBurdenCard(valuation: ValuationAssessment, asOfDate: LocalDate): ValuationCardUiState {
        val rate: RateBurdenAssessment = valuation.rateBurden
        // 0인지는 적는 값(소수 두 자리로 반올림한 값)으로 가른다.
        val yearChangeLine = when (formatter.scoreTone(rate.yearChange, YEAR_CHANGE_DECIMALS)) {
            ScoreTone.POSITIVE -> UiText(
                R.string.rate_evidence_year_change,
                listOf(formatter.signedPercentPoint(rate.yearChange), UiText(R.string.rate_evidence_rising)),
            )
            ScoreTone.NEGATIVE -> UiText(
                R.string.rate_evidence_year_change,
                listOf(formatter.signedPercentPoint(rate.yearChange), UiText(R.string.rate_evidence_falling)),
            )
            ScoreTone.ZERO -> UiText(R.string.rate_evidence_year_same)
        }
        return valuationCard(
            card = ValuationCard.RATE_BURDEN,
            valuation = valuation,
            subtitle = UiText(
                R.string.rate_burden_subtitle,
                listOf(datedValue(formatter.percent(rate.realYield), rate.realYieldDate, asOfDate)),
            ),
            score = rate.score,
            scoredStage = rate.scoredStage,
            displayedStage = rate.displayedStage,
            evidence = listOf(
                yearChangeLine,
                UiText(
                    R.string.rate_evidence_rates,
                    listOf(
                        datedValue(formatter.percent(rate.treasury10y.value), rate.treasury10y.date, asOfDate),
                        datedValue(formatter.percent(rate.policyRate.value), rate.policyRate.date, asOfDate),
                    ),
                ),
                UiText(
                    R.string.rate_evidence_inflation,
                    listOf(
                        formatter.ratioPercent(rate.inflationRate),
                        formatter.targetPercent(valuation.inflationTarget),
                        formatter.inflationGap(rate.inflationRate, valuation.inflationTarget),
                        rate.inflationMonth.monthValue,
                    ),
                ),
            ),
        )
    }

    private fun valuationCard(
        card: ValuationCard,
        valuation: ValuationAssessment,
        subtitle: UiText,
        score: Int,
        scoredStage: ValuationStage,
        displayedStage: ValuationStage,
        evidence: List<UiText>,
    ): ValuationCardUiState {
        val low = valuation.lowBoundary
        val high = valuation.highBoundary
        val lowWidth = (low + 1) / 100f
        val highWidth = (100 - high + 1) / 100f
        val notice = when {
            scoredStage == displayedStage -> null
            displayedStage == ValuationStage.LOW ->
                UiText(R.string.valuation_notice_low, listOf(score, valuationStageName(card, displayedStage), low + valuation.buffer + 1))
            displayedStage == ValuationStage.HIGH ->
                UiText(R.string.valuation_notice_high, listOf(score, valuationStageName(card, displayedStage), high - valuation.buffer - 1))
            else -> null
        }
        return ValuationCardUiState(
            subtitle = subtitle,
            scoreText = UiText(R.string.valuation_score, listOf(score, valuationStageName(card, displayedStage))),
            stage = displayedStage,
            gauge = ValuationGaugeUiState(
                segments = listOf(lowWidth, 1f - lowWidth - highWidth, highWidth),
                labels = ValuationStage.entries.map { valuationStageName(card, it) },
                position = (score / 100f).coerceIn(0f, 1f),
            ),
            notice = notice,
            evidence = evidence,
        )
    }

    private fun valuationStageName(card: ValuationCard, stage: ValuationStage): UiText = UiText(
        when (stage) {
            ValuationStage.MIDDLE -> R.string.valuation_middle
            ValuationStage.LOW -> if (card == ValuationCard.PRICE_POSITION) R.string.valuation_price_low else R.string.valuation_rate_low
            ValuationStage.HIGH -> if (card == ValuationCard.PRICE_POSITION) R.string.valuation_price_high else R.string.valuation_rate_high
        },
    )

    // 값의 날짜가 기준 날짜와 다를 때만 값 바로 뒤에 그 날짜를 붙인다.
    private fun datedValue(valueText: String, valueDate: LocalDate, asOfDate: LocalDate): Any =
        if (valueDate == asOfDate) {
            valueText
        } else {
            UiText(R.string.evidence_value_with_date, listOf(valueText, valueDate.monthValue, valueDate.dayOfMonth))
        }

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
            is IndicatorEvidence.RatioEvidence -> UiText(R.string.evidence_value, listOf(formatter.signedPercent(evidence.ratio)))
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
        const val YEAR_CHANGE_DECIMALS = 2

        val INITIAL_STATE = MarketMoodUiState(
            refreshButton = RefreshButtonState.DISABLED,
            banner = null,
            body = MoodBody.Undecided,
        )
    }
}
