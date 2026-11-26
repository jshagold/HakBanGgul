package com.hak.hakbanggul.feature.marketmood.presentation

import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.GroupAssessment
import com.hak.hakbanggul.feature.marketmood.domain.IndicatorAssessment
import com.hak.hakbanggul.feature.marketmood.domain.IndicatorEvidence
import com.hak.hakbanggul.feature.marketmood.domain.IndicatorInput
import com.hak.hakbanggul.feature.marketmood.domain.MarketOverview
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.MoodAssessment
import com.hak.hakbanggul.feature.marketmood.domain.PeriodContent
import com.hak.hakbanggul.feature.marketmood.domain.PricePositionAssessment
import com.hak.hakbanggul.feature.marketmood.domain.RateBurdenAssessment
import com.hak.hakbanggul.feature.marketmood.domain.RegimeAssessment
import com.hak.hakbanggul.feature.marketmood.domain.RegimePeriod
import com.hak.hakbanggul.feature.marketmood.domain.ScoringRule
import com.hak.hakbanggul.feature.marketmood.domain.TargetOverview
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
import java.time.LocalDate
import javax.inject.Inject

// 한 벌(MarketOverview)로 화면 다섯의 카드 상태를 만든다. 글은 넘겨받은 한 벌과 그 안의 규칙(overview.rules)만으로 만든다.
// 문구에 끼우는 일수와 기간의 이름은 모두 규칙에서 낸다.
class MoodUiMapper @Inject constructor() {

    private val formatter = MoodTextFormatter()

    // 첫 화면의 판정. isRefreshing은 섹터 줄의 상태 글("받는 중"/"받지 못함")을 고르는 데 쓴다.
    fun mainScreen(overview: MarketOverview, isRefreshing: Boolean): MoodUiState {
        val mood = overview.mood
        return MoodUiState(
            broadMarketId = overview.rules.broadMarket.id,
            regime = regimeCard(overview, overview.broadMarket, regimeOf(mood), showLink = true),
            valuationTitle = valuationTitle(overview),
            valuationDescription = valuationDescription(overview),
            pricePosition = pricePositionCard(overview, overview.broadMarket, mood.valuation.pricePosition, withEvidence = false, showLink = true),
            rateBurden = rateBurdenCard(overview, mood.valuation.rateBurden, withEvidence = false, showLink = true),
            sectorHeader = SectorHeaderUiState(
                nameText = UiText(R.string.sectors_header_name),
                regimeText = UiText(R.string.sectors_header_regime, listOf(formatter.periodName(overview.rules.regime.mainPeriod().length))),
                pricePositionText = UiText(R.string.sectors_header_price, listOf(formatter.periodName(overview.rules.valuation.mainPeriod().length))),
            ),
            sectors = overview.sectors.map { sectorRow(overview, it, isRefreshing) },
        )
    }

    // 장세 화면. 대상을 찾지 못하거나 메인 장세를 낼 수 없으면 null이다.
    fun regimeDetail(overview: MarketOverview, targetId: String): RegimeDetailUiState? {
        val target = overview.target(targetId) ?: return null
        val regime = target.mainRegime() ?: return null
        val isBroadMarket = isBroadMarket(overview, target)
        return RegimeDetailUiState(
            title = TargetLabels.regimeName(target.target, isBroadMarket),
            symbol = if (isBroadMarket) null else TargetLabels.symbol(target.target),
            main = regimeCard(overview, target, regime, showLink = false),
            evidenceBasis = UiText(R.string.evidence_basis, listOf(formatter.periodName(overview.rules.regime.mainPeriod().length))),
            groups = regime.groups.map { groupState(overview, target, it, regime.asOfDate) },
            otherPeriods = otherRegimeCards(overview, target),
        )
    }

    // 주가 위치 화면. 대상을 찾지 못하거나 메인 주가 위치를 낼 수 없으면 null이다.
    fun pricePositionDetail(overview: MarketOverview, targetId: String): PricePositionDetailUiState? {
        val target = overview.target(targetId) ?: return null
        val price = target.mainPricePosition() ?: return null
        return PricePositionDetailUiState(
            targetText = TargetLabels.nameWithSymbol(target.target),
            valuationTitle = valuationTitle(overview),
            valuationDescription = valuationDescription(overview),
            main = pricePositionCard(overview, target, price, withEvidence = true, showLink = false),
            otherPeriods = target.pricePositions.filterNot { it.isMain }.map { content ->
                otherValuationCard(overview, ValuationCard.PRICE_POSITION, content) { it.score to (it.scoredStage to it.displayedStage) }
            },
        )
    }

    // 금리 부담 화면. 메인 금리 부담을 낼 수 없으면 null이다.
    fun rateBurdenDetail(overview: MarketOverview): RateBurdenDetailUiState? {
        val rate = overview.rateBurdens.firstOrNull { it.isMain }?.value ?: return null
        return RateBurdenDetailUiState(
            valuationTitle = valuationTitle(overview),
            valuationDescription = valuationDescription(overview),
            main = rateBurdenCard(overview, rate, withEvidence = true, showLink = false),
            otherPeriods = overview.rateBurdens.filterNot { it.isMain }.map { content ->
                otherValuationCard(overview, ValuationCard.RATE_BURDEN, content) { it.score to (it.scoredStage to it.displayedStage) }
            },
        )
    }

    // 섹터 화면. 대상을 찾지 못하거나 메인 장세와 메인 주가 위치가 모두 있지 않으면 null이다.
    fun sector(overview: MarketOverview, targetId: String): SectorUiState? {
        val target = overview.target(targetId) ?: return null
        val regime = target.mainRegime() ?: return null
        val price = target.mainPricePosition() ?: return null
        return SectorUiState(
            targetId = target.target.id,
            name = TargetLabels.name(target.target),
            symbol = TargetLabels.symbol(target.target),
            regime = regimeCard(overview, target, regime, showLink = true),
            valuationTitle = valuationTitle(overview),
            valuationDescription = valuationDescription(overview),
            pricePosition = pricePositionCard(overview, target, price, withEvidence = false, showLink = true),
        )
    }

    // 제목

    private fun valuationTitle(overview: MarketOverview) =
        UiText(R.string.valuation_title, listOf(formatter.periodName(overview.rules.valuation.mainPeriod().length)))

    private fun valuationDescription(overview: MarketOverview) =
        UiText(R.string.valuation_description, listOf(formatter.periodName(overview.rules.valuation.mainPeriod().length)))

    private fun isBroadMarket(overview: MarketOverview, target: TargetOverview) = target.target.id == overview.rules.broadMarket.id

    // 장세 카드

    private fun regimeOf(mood: MoodAssessment) = RegimeAssessment(
        asOfDate = mood.asOfDate,
        displayedStage = mood.displayedStage,
        computedStage = mood.computedStage,
        compositeScore = mood.compositeScore,
        computedStageStreak = mood.computedStageStreak,
        holdDays = mood.holdDays,
        upperBoundary = mood.upperBoundary,
        lowerBoundary = mood.lowerBoundary,
        groups = mood.groups,
    )

    private fun regimeCard(overview: MarketOverview, target: TargetOverview, regime: RegimeAssessment, showLink: Boolean) =
        RegimeCardUiState(
            title = UiText(
                R.string.regime_card_title,
                listOf(
                    TargetLabels.regimeName(target.target, isBroadMarket(overview, target)),
                    formatter.periodName(overview.rules.regime.mainPeriod().length),
                ),
            ),
            asOfText = UiText(R.string.mood_as_of, listOf(regime.asOfDate.monthValue, regime.asOfDate.dayOfMonth)),
            stage = regime.displayedStage,
            stageName = stageName(regime.displayedStage),
            compositeScoreText = formatter.groupScore(regime.compositeScore),
            compositeTone = formatter.scoreTone(regime.compositeScore, GROUP_DECIMALS),
            computedStageName = stageName(regime.computedStage),
            gaugePosition = gaugePosition(regime.compositeScore, regime.upperBoundary, regime.lowerBoundary),
            pendingNotice = pendingNotice(regime),
            showLink = showLink,
        )

    private fun pendingNotice(regime: RegimeAssessment): UiText? =
        if (regime.computedStage != regime.displayedStage) {
            UiText(
                R.string.mood_pending_notice,
                listOf(stageName(regime.computedStage), stageName(regime.displayedStage), regime.holdDays, regime.computedStageStreak),
            )
        } else {
            null
        }

    // 장세의 다른 기간 카드(메인을 뺀 짧은 기간부터)

    private fun otherRegimeCards(overview: MarketOverview, target: TargetOverview): List<OtherPeriodCardUiState> {
        val name = TargetLabels.regimeName(target.target, isBroadMarket(overview, target))
        return target.regimes.filterNot { it.isMain }.map { content ->
            val periodName = formatter.periodName(content.length)
            val periodText = UiText(R.string.regime_period_label, listOf(periodName))
            val regime = content.value
            val period = overview.rules.regime.periods.firstOrNull { it.id == content.periodId }
            if (regime == null || period == null) {
                OtherPeriodCardUiState.Unavailable(name, periodText, UiText(R.string.regime_period_unavailable, listOf(periodName)))
            } else {
                OtherPeriodCardUiState.Regime(
                    name = name,
                    periodText = periodText,
                    compositeScoreText = formatter.groupScore(regime.compositeScore),
                    compositeTone = formatter.scoreTone(regime.compositeScore, GROUP_DECIMALS),
                    stage = regime.displayedStage,
                    stageName = stageName(regime.displayedStage),
                    gaugePosition = gaugePosition(regime.compositeScore, regime.upperBoundary, regime.lowerBoundary),
                    pendingNotice = pendingNotice(regime),
                    evidence = listOf(
                        UiText(
                            R.string.regime_period_evidence,
                            listOf(
                                period.averageDays,
                                formatter.signedPercent(ratioOf(overview, regime) { it is ScoringRule.AboveAverage }),
                                periodName,
                                formatter.signedPercent(ratioOf(overview, regime) { it == ScoringRule.FromHigh }),
                            ),
                        ),
                        UiText(
                            R.string.evidence_average_slope,
                            listOf(
                                period.averageDays,
                                period.slopeDays,
                                formatter.signedPercent(ratioOf(overview, regime) { it == ScoringRule.AverageSlope }),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    // 그 점수 기준을 쓰는 첫 항목의 비율 근거. 없으면 0이다.
    private fun ratioOf(overview: MarketOverview, regime: RegimeAssessment, scoring: (ScoringRule) -> Boolean): Double {
        val id = overview.rules.regime.groups.flatMap { it.indicators }.firstOrNull { scoring(it.scoring) }?.id ?: return 0.0
        val evidence = regime.groups.flatMap { it.indicators }.firstOrNull { it.id == id }?.evidence
        return (evidence as? IndicatorEvidence.RatioEvidence)?.ratio ?: 0.0
    }

    // 묶음과 항목 줄

    private fun groupState(overview: MarketOverview, target: TargetOverview, group: GroupAssessment, asOfDate: LocalDate): GroupUiState {
        val rule = overview.rules.regime.groups.firstOrNull { it.id == group.id }
        val weight = formatter.weightPercent(group.weight)
        // 대상의 종가를 쓰는 묶음(주식)은 대상 이름과 ETF 기호를 덧붙인다.
        val usesTargetClose = rule?.indicators?.any { it.input == IndicatorInput.TargetClose } == true
        return GroupUiState(
            name = UiText(IndicatorLabels.groupNameRes(group.id)),
            weightText = if (usesTargetClose) {
                UiText(R.string.stock_group_suffix, listOf(TargetLabels.nameWithSymbol(target.target), weight))
            } else {
                UiText(R.string.group_weight, listOf(weight))
            },
            scoreText = formatter.groupScore(group.score),
            scoreTone = formatter.scoreTone(group.score, GROUP_DECIMALS),
            indicators = group.indicators.map { indicatorState(overview.rules.regime.mainPeriod(), it, asOfDate) },
        )
    }

    private fun indicatorState(period: RegimePeriod, indicator: IndicatorAssessment, asOfDate: LocalDate): IndicatorUiState {
        val label = IndicatorLabels.indicator(indicator.id)
        return IndicatorUiState(
            name = UiText(label.nameRes),
            suffix = label.suffixRes?.let { UiText(it) },
            evidence = evidence(period, indicator.evidence, label, asOfDate),
            scoreText = formatter.itemScore(indicator.score),
            scoreTone = formatter.scoreTone(indicator.score, ITEM_DECIMALS),
        )
    }

    private fun evidence(period: RegimePeriod, evidence: IndicatorEvidence, label: IndicatorLabels.Label, asOfDate: LocalDate): UiText =
        when (evidence) {
            is IndicatorEvidence.RatioEvidence -> {
                val ratio = formatter.signedPercent(evidence.ratio)
                when (label.ratioFormat) {
                    IndicatorLabels.RatioFormat.AVERAGE_DEVIATION ->
                        UiText(R.string.evidence_above_average, listOf(period.averageDays, ratio))
                    IndicatorLabels.RatioFormat.DROP_FROM_HIGH ->
                        UiText(R.string.evidence_from_high, listOf(formatter.periodName(period.length), ratio))
                    IndicatorLabels.RatioFormat.AVERAGE_SLOPE ->
                        UiText(R.string.evidence_average_slope, listOf(period.averageDays, period.slopeDays, ratio))
                    null -> UiText(R.string.evidence_value, listOf(ratio))
                }
            }
            is IndicatorEvidence.ValueEvidence -> {
                val valueText = when (label.valueFormat) {
                    IndicatorLabels.ValueFormat.SIGNED_PERCENT_POINT -> formatter.signedPercentPoint(evidence.value)
                    IndicatorLabels.ValueFormat.PERCENT, null -> formatter.percent(evidence.value)
                }
                // 값의 날짜가 그 대상의 기준 날짜와 다를 때만 그 날짜를 붙인다.
                if (evidence.valueDate == asOfDate) {
                    UiText(R.string.evidence_value, listOf(valueText))
                } else {
                    UiText(R.string.evidence_value_with_date, listOf(valueText, evidence.valueDate.monthValue, evidence.valueDate.dayOfMonth))
                }
            }
        }

    // 매매 판단 카드

    private enum class ValuationCard { PRICE_POSITION, RATE_BURDEN }

    private fun pricePositionCard(
        overview: MarketOverview,
        target: TargetOverview,
        price: PricePositionAssessment,
        withEvidence: Boolean,
        showLink: Boolean,
    ): ValuationCardUiState {
        val rules = overview.rules.valuation
        return valuationCard(
            overview = overview,
            card = ValuationCard.PRICE_POSITION,
            subtitle = TargetLabels.nameWithSymbol(target.target),
            score = price.score,
            scoredStage = price.scoredStage,
            displayedStage = price.displayedStage,
            evidence = if (!withEvidence) {
                emptyList()
            } else {
                listOf(
                    UiText(
                        R.string.price_evidence_average,
                        listOf(
                            rules.shortAverageDays,
                            formatter.signedPercent(price.shortDeviation),
                            formatter.periodName(rules.longAverageLength),
                            formatter.signedPercent(price.longDeviation),
                        ),
                    ),
                    UiText(
                        R.string.price_evidence_drawdown,
                        listOf(
                            formatter.signedPercent(price.drawdown),
                            formatter.periodName(rules.threeMonthLength),
                            formatter.signedPercent(price.threeMonthReturn),
                        ),
                    ),
                )
            },
            showLink = showLink,
        )
    }

    // 날짜를 붙이는 기준은 판정의 기준 날짜(전체 시장의 가장 최근 거래일)다.
    private fun rateBurdenCard(overview: MarketOverview, rate: RateBurdenAssessment, withEvidence: Boolean, showLink: Boolean): ValuationCardUiState {
        val asOfDate = overview.mood.asOfDate
        val inflationTarget = overview.rules.valuation.inflationTarget
        return valuationCard(
            overview = overview,
            card = ValuationCard.RATE_BURDEN,
            subtitle = UiText(R.string.rate_burden_subtitle, listOf(datedValue(formatter.percent(rate.realYield), rate.realYieldDate, asOfDate))),
            score = rate.score,
            scoredStage = rate.scoredStage,
            displayedStage = rate.displayedStage,
            evidence = if (!withEvidence) {
                emptyList()
            } else {
                listOf(
                    yearChangeLine(rate),
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
                            formatter.targetPercent(inflationTarget),
                            formatter.inflationGap(rate.inflationRate, inflationTarget),
                            rate.inflationMonth.monthValue,
                        ),
                    ),
                )
            },
            showLink = showLink,
        )
    }

    // 0인지는 적는 값(소수 두 자리로 반올림한 값)으로 가른다.
    private fun yearChangeLine(rate: RateBurdenAssessment): UiText =
        when (formatter.scoreTone(rate.yearChange, YEAR_CHANGE_DECIMALS)) {
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

    private fun valuationCard(
        overview: MarketOverview,
        card: ValuationCard,
        subtitle: UiText,
        score: Int,
        scoredStage: ValuationStage,
        displayedStage: ValuationStage,
        evidence: List<UiText>,
        showLink: Boolean,
    ) = ValuationCardUiState(
        name = UiText(if (card == ValuationCard.PRICE_POSITION) R.string.price_position_name else R.string.rate_burden_name),
        subtitle = subtitle,
        scoreText = valuationScoreText(card, score, displayedStage),
        stage = displayedStage,
        gauge = valuationGauge(overview, card, score),
        notice = valuationNotice(overview, card, score, scoredStage, displayedStage),
        evidence = evidence,
        showLink = showLink,
    )

    // 매매 판단의 다른 기간 카드. values는 콘텐츠에서 점수와 (점수로 정한 단계, 표시 단계)를 꺼낸다.
    private fun <T> otherValuationCard(
        overview: MarketOverview,
        card: ValuationCard,
        content: PeriodContent<T>,
        values: (T) -> Pair<Int, Pair<ValuationStage, ValuationStage>>,
    ): OtherPeriodCardUiState {
        val name = UiText(if (card == ValuationCard.PRICE_POSITION) R.string.price_position_name else R.string.rate_burden_name)
        val periodName = formatter.periodName(content.length)
        val periodText = UiText(R.string.valuation_period_label, listOf(periodName))
        val value = content.value
            ?: return OtherPeriodCardUiState.Unavailable(name, periodText, UiText(R.string.valuation_period_unavailable, listOf(periodName)))
        val (score, stages) = values(value)
        val (scored, displayed) = stages
        return OtherPeriodCardUiState.Valuation(
            name = name,
            periodText = periodText,
            scoreText = valuationScoreText(card, score, displayed),
            stage = displayed,
            gauge = valuationGauge(overview, card, score),
            notice = valuationNotice(overview, card, score, scored, displayed),
        )
    }

    private fun valuationScoreText(card: ValuationCard, score: Int, displayedStage: ValuationStage) =
        UiText(R.string.valuation_score, listOf(score, valuationStageName(card, displayedStage)))

    private fun valuationGauge(overview: MarketOverview, card: ValuationCard, score: Int): ValuationGaugeUiState {
        val rules = overview.rules.valuation
        val lowWidth = (rules.lowBoundary + 1) / 100f
        val highWidth = (100 - rules.highBoundary + 1) / 100f
        return ValuationGaugeUiState(
            segments = listOf(lowWidth, 1f - lowWidth - highWidth, highWidth),
            labels = ValuationStage.entries.map { valuationStageName(card, it) },
            position = (score / 100f).coerceIn(0f, 1f),
        )
    }

    private fun valuationNotice(
        overview: MarketOverview,
        card: ValuationCard,
        score: Int,
        scoredStage: ValuationStage,
        displayedStage: ValuationStage,
    ): UiText? {
        val rules = overview.rules.valuation
        return when {
            scoredStage == displayedStage -> null
            displayedStage == ValuationStage.LOW -> UiText(
                R.string.valuation_notice_low,
                listOf(score, valuationStageName(card, displayedStage), rules.lowBoundary + rules.buffer + 1),
            )
            displayedStage == ValuationStage.HIGH -> UiText(
                R.string.valuation_notice_high,
                listOf(score, valuationStageName(card, displayedStage), rules.highBoundary - rules.buffer - 1),
            )
            else -> null
        }
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

    // 섹터 줄

    private fun sectorRow(overview: MarketOverview, target: TargetOverview, isRefreshing: Boolean): SectorRowUiState {
        val regime = target.mainRegime()
        val price = target.mainPricePosition()
        val values = if (regime != null && price != null) {
            SectorRowContent.Values(
                regimeStage = regime.displayedStage,
                regimeStageName = stageName(regime.displayedStage),
                valuationStage = price.displayedStage,
                valuationText = valuationScoreText(ValuationCard.PRICE_POSITION, price.score, price.displayedStage),
            )
        } else {
            null
        }
        val asOfDate = target.asOfDate
        return SectorRowUiState(
            targetId = target.target.id,
            name = TargetLabels.name(target.target),
            symbol = TargetLabels.symbol(target.target),
            asOfText = if (values != null && asOfDate != null && asOfDate != overview.broadMarket.asOfDate) {
                UiText(R.string.sector_as_of, listOf(asOfDate.monthValue, asOfDate.dayOfMonth))
            } else {
                null
            },
            content = values ?: SectorRowContent.Status(UiText(if (isRefreshing) R.string.sector_loading else R.string.sector_failed)),
            clickable = values != null,
        )
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
    }
}
