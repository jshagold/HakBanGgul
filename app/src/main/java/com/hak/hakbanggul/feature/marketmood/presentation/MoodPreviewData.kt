package com.hak.hakbanggul.feature.marketmood.presentation

import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage

// 미리보기의 상태. 숫자와 날짜는 시안의 예시를 옮긴 것이다(그런 날이 아닐 때 수동 확인을 미리보기로 하기 위함이다).
internal object MoodPreviewData {

    fun months(n: Int) = UiText(R.string.period_months, listOf(n))
    fun years(n: Int) = UiText(R.string.period_years, listOf(n))

    fun stageText(stage: MarketStage) = UiText(
        when (stage) {
            MarketStage.BULL -> R.string.stage_bull
            MarketStage.NEUTRAL -> R.string.stage_neutral
            MarketStage.WEAK -> R.string.stage_weak
            MarketStage.BEAR -> R.string.stage_bear
        },
    )

    private val priceNames = listOf(R.string.valuation_price_low, R.string.valuation_middle, R.string.valuation_price_high)
    private val rateNames = listOf(R.string.valuation_rate_low, R.string.valuation_middle, R.string.valuation_rate_high)

    fun valuationStageText(price: Boolean, stage: ValuationStage) = UiText((if (price) priceNames else rateNames)[stage.ordinal])

    fun valuationScore(price: Boolean, score: Int, stage: ValuationStage) =
        UiText(R.string.valuation_score, listOf(score, valuationStageText(price, stage)))

    fun gauge(price: Boolean, score: Int) =
        ValuationGaugeUiState(listOf(0.31f, 0.38f, 0.31f), ValuationStage.entries.map { valuationStageText(price, it) }, score / 100f)

    fun withDate(value: String, month: Int, day: Int) = UiText(R.string.evidence_value_with_date, listOf(value, month, day))

    fun pending(computed: MarketStage, displayed: MarketStage, streak: Int) =
        UiText(R.string.mood_pending_notice, listOf(stageText(computed), stageText(displayed), 3, streak))

    fun noticeLow(price: Boolean, score: Int) =
        UiText(R.string.valuation_notice_low, listOf(score, valuationStageText(price, ValuationStage.LOW), 36))

    fun noticeHigh(price: Boolean, score: Int) =
        UiText(R.string.valuation_notice_high, listOf(score, valuationStageText(price, ValuationStage.HIGH), 64))

    val broadRegime = UiText(R.string.regime_broad_market)
    fun sectorRegime(nameRes: Int) = UiText(R.string.regime_sector, listOf(UiText(nameRes)))
    fun withSymbol(nameRes: Int, symbol: String) = UiText(R.string.target_with_symbol, listOf(UiText(nameRes), symbol))

    // 장세 카드
    fun regimeCard(
        name: UiText = broadRegime,
        stage: MarketStage = MarketStage.WEAK,
        computed: MarketStage = stage,
        score: String = "-0.32",
        tone: ScoreTone = ScoreTone.NEGATIVE,
        gauge: Float = 0.34f,
        pending: UiText? = null,
        showLink: Boolean = true,
    ) = RegimeCardUiState(
        title = UiText(R.string.regime_card_title, listOf(name, months(3))),
        asOfText = UiText(R.string.mood_as_of, listOf(11, 24)),
        stage = stage,
        stageName = stageText(stage),
        compositeScoreText = score,
        compositeTone = tone,
        computedStageName = stageText(computed),
        gaugePosition = gauge,
        pendingNotice = pending,
        showLink = showLink,
    )

    fun priceCard(
        subtitle: UiText = withSymbol(R.string.target_broad_market, "SPY"),
        score: Int = 14,
        stage: ValuationStage = ValuationStage.LOW,
        notice: UiText? = null,
        evidence: List<UiText> = emptyList(),
        showLink: Boolean = true,
    ) = ValuationCardUiState(
        name = UiText(R.string.price_position_name),
        subtitle = subtitle,
        scoreText = valuationScore(true, score, stage),
        stage = stage,
        gauge = gauge(true, score),
        notice = notice,
        evidence = evidence,
        showLink = showLink,
    )

    fun priceEvidence(short: String, long: String, drawdown: String, threeMonth: String) = listOf(
        UiText(R.string.price_evidence_average, listOf(200, short, years(3), long)),
        UiText(R.string.price_evidence_drawdown, listOf(drawdown, months(3), threeMonth)),
    )

    fun rateCard(
        realYield: Any = withDate("2.79%", 11, 23),
        score: Int = 96,
        stage: ValuationStage = ValuationStage.HIGH,
        notice: UiText? = null,
        evidence: List<UiText> = emptyList(),
        showLink: Boolean = true,
    ) = ValuationCardUiState(
        name = UiText(R.string.rate_burden_name),
        subtitle = UiText(R.string.rate_burden_subtitle, listOf(realYield)),
        scoreText = valuationScore(false, score, stage),
        stage = stage,
        gauge = gauge(false, score),
        notice = notice,
        evidence = evidence,
        showLink = showLink,
    )

    fun rateEvidence(
        yearLine: UiText = UiText(R.string.rate_evidence_year_change, listOf("+0.97%p", UiText(R.string.rate_evidence_rising))),
        treasury: Any = "4.92%",
        policy: Any = withDate("3.88%", 11, 23),
        inflation: String = "3.7%",
        gap: String = "+1.7%p",
        month: Int = 9,
    ) = listOf(
        yearLine,
        UiText(R.string.rate_evidence_rates, listOf(treasury, policy)),
        UiText(R.string.rate_evidence_inflation, listOf(inflation, "2", gap, month)),
    )

    // 섹터 줄: 이름, ETF 기호, 장세, 주가 위치 점수와 단계
    private data class Sector(val id: String, val nameRes: Int, val symbol: String, val stage: MarketStage, val score: Int, val valuation: ValuationStage)

    private val sectors = listOf(
        Sector("nasdaq_100", R.string.target_nasdaq_100, "QQQ", MarketStage.BEAR, 9, ValuationStage.LOW),
        Sector("semiconductors", R.string.target_semiconductors, "SMH", MarketStage.BEAR, 15, ValuationStage.LOW),
        Sector("technology", R.string.target_technology, "XLK", MarketStage.BEAR, 20, ValuationStage.LOW),
        Sector("financials", R.string.target_financials, "XLF", MarketStage.WEAK, 48, ValuationStage.MIDDLE),
        Sector("energy", R.string.target_energy, "XLE", MarketStage.WEAK, 67, ValuationStage.HIGH),
        Sector("health_care", R.string.target_health_care, "XLV", MarketStage.NEUTRAL, 97, ValuationStage.HIGH),
        Sector("consumer_discretionary", R.string.target_consumer_discretionary, "XLY", MarketStage.BULL, 35, ValuationStage.LOW),
        Sector("consumer_staples", R.string.target_consumer_staples, "XLP", MarketStage.BULL, 94, ValuationStage.HIGH),
        Sector("industrials", R.string.target_industrials, "XLI", MarketStage.WEAK, 5, ValuationStage.LOW),
        Sector("materials", R.string.target_materials, "XLB", MarketStage.WEAK, 52, ValuationStage.MIDDLE),
        Sector("utilities", R.string.target_utilities, "XLU", MarketStage.BULL, 12, ValuationStage.LOW),
        Sector("real_estate", R.string.target_real_estate, "XLRE", MarketStage.NEUTRAL, 49, ValuationStage.MIDDLE),
        Sector("communication_services", R.string.target_communication_services, "XLC", MarketStage.WEAK, 3, ValuationStage.LOW),
    )

    // statusFrom부터의 줄은 상태 글(statusRes)이다. datedLast면 마지막 줄(XLC)에 값과 "11월 21일 종가 기준"을 둔다.
    fun sectorRows(statusFrom: Int = sectors.size, statusRes: Int = R.string.sector_loading, datedLast: Boolean = false) =
        sectors.mapIndexed { index, s ->
            val isStatus = index >= statusFrom && !(datedLast && index == sectors.lastIndex)
            SectorRowUiState(
                targetId = s.id,
                name = UiText(s.nameRes),
                symbol = s.symbol,
                asOfText = if (datedLast && index == sectors.lastIndex) UiText(R.string.sector_as_of, listOf(11, 21)) else null,
                refreshingText = null,
                content = if (isStatus) {
                    SectorRowContent.Status(UiText(statusRes))
                } else {
                    SectorRowContent.Values(s.stage, stageText(s.stage), s.valuation, valuationScore(true, s.score, s.valuation))
                },
                clickable = !isStatus,
            )
        }

    // 시안 "새 값을 받는 중, 섹터 7개는 1분쯤 뒤": refreshingFrom부터의 줄은 받아 둔 값을 보이며 받는 중이다.
    // 받는 중인 줄은 기준 날짜가 다른 날이라 "받는 중 · 11월 21일 값"이고, 마지막 줄(XLC)만 기준 날짜가 같은 "받는 중"이다.
    fun refreshingSectorRows(refreshingFrom: Int = 6) =
        sectors.mapIndexed { index, s ->
            val refreshing = index >= refreshingFrom
            SectorRowUiState(
                targetId = s.id,
                name = UiText(s.nameRes),
                symbol = s.symbol,
                asOfText = null,
                refreshingText = when {
                    !refreshing -> null
                    index == sectors.lastIndex -> UiText(R.string.sector_loading)
                    else -> UiText(R.string.sector_refreshing_with_date, listOf(11, 21))
                },
                content = SectorRowContent.Values(s.stage, stageText(s.stage), s.valuation, valuationScore(true, s.score, s.valuation)),
                clickable = true,
            )
        }

    val sectorHeader = SectorHeaderUiState(
        UiText(R.string.sectors_header_name),
        UiText(R.string.sectors_header_regime, listOf(months(3))),
        UiText(R.string.sectors_header_price, listOf(years(2))),
    )

    val valuationTitle = UiText(R.string.valuation_title, listOf(years(2)))
    val valuationDescription = UiText(R.string.valuation_description, listOf(years(2)))

    fun mood(
        regime: RegimeCardUiState = regimeCard(),
        price: ValuationCardUiState = priceCard(),
        rate: ValuationCardUiState = rateCard(),
        sectors: List<SectorRowUiState> = sectorRows(),
    ) = MoodUiState(
        broadMarketId = "broad_market",
        regime = regime,
        valuationTitle = valuationTitle,
        valuationDescription = valuationDescription,
        pricePosition = price,
        rateBurden = rate,
        sectorHeader = sectorHeader,
        sectors = sectors,
    )

    // 묶음 카드
    fun indicator(nameRes: Int, evidence: UiText, score: String, tone: ScoreTone, suffixRes: Int? = null) =
        IndicatorUiState(UiText(nameRes), suffixRes?.let { UiText(it) }, evidence, score, tone)

    fun groups(
        target: UiText = withSymbol(R.string.target_broad_market, "SPY"),
        stockScore: String = "-0.67",
        stockTone: ScoreTone = ScoreTone.NEGATIVE,
        average: Pair<String, String> = "-5.3%" to "-1.0",
        high: Pair<String, String> = "-7.7%" to "0.0",
        slope: Pair<String, String> = "-1.2%" to "-1.0",
    ): List<GroupUiState> {
        fun tone(score: String) = when {
            score.startsWith("+") -> ScoreTone.POSITIVE
            score.startsWith("-") -> ScoreTone.NEGATIVE
            else -> ScoreTone.ZERO
        }
        return listOf(
            GroupUiState(
                name = UiText(R.string.group_stocks),
                weightText = UiText(R.string.stock_group_suffix, listOf(target, 70)),
                scoreText = stockScore,
                scoreTone = stockTone,
                indicators = listOf(
                    indicator(R.string.indicator_above_average, UiText(R.string.evidence_above_average, listOf(50, average.first)), average.second, tone(average.second)),
                    indicator(R.string.indicator_from_high, UiText(R.string.evidence_from_high, listOf(months(3), high.first)), high.second, tone(high.second)),
                    indicator(R.string.indicator_average_slope, UiText(R.string.evidence_average_slope, listOf(50, 20, slope.first)), slope.second, tone(slope.second)),
                ),
            ),
            GroupUiState(
                name = UiText(R.string.group_bond_signals),
                weightText = UiText(R.string.group_weight, listOf(30)),
                scoreText = "+0.50",
                scoreTone = ScoreTone.POSITIVE,
                indicators = listOf(
                    indicator(
                        R.string.indicator_yield_curve,
                        UiText(R.string.evidence_value, listOf("+0.28%p")),
                        "0.0",
                        ScoreTone.ZERO,
                        R.string.indicator_yield_curve_suffix,
                    ),
                    indicator(R.string.indicator_high_yield_spread, withDate("3.63%", 11, 23), "+1.0", ScoreTone.POSITIVE),
                ),
            ),
        )
    }

    // 장세의 다른 기간 카드
    fun otherRegime(
        name: UiText = broadRegime,
        period: UiText,
        stage: MarketStage,
        score: String,
        tone: ScoreTone,
        gauge: Float,
        averageDays: Int,
        slopeDays: Int,
        average: String,
        drop: String,
        slope: String,
        pending: UiText? = null,
    ) = OtherPeriodCardUiState.Regime(
        name = name,
        periodText = UiText(R.string.regime_period_label, listOf(period)),
        compositeScoreText = score,
        compositeTone = tone,
        stage = stage,
        stageName = stageText(stage),
        gaugePosition = gauge,
        pendingNotice = pending,
        evidence = listOf(
            UiText(R.string.regime_period_evidence, listOf(averageDays, average, period, drop)),
            UiText(R.string.evidence_average_slope, listOf(averageDays, slopeDays, slope)),
        ),
    )

    // 매매 판단의 다른 기간 카드
    fun otherValuation(price: Boolean, period: UiText, score: Int, stage: ValuationStage, notice: UiText? = null) =
        OtherPeriodCardUiState.Valuation(
            name = UiText(if (price) R.string.price_position_name else R.string.rate_burden_name),
            periodText = UiText(R.string.valuation_period_label, listOf(period)),
            scoreText = valuationScore(price, score, stage),
            stage = stage,
            gauge = gauge(price, score),
            notice = notice,
        )

    fun unavailableValuation(price: Boolean, period: UiText) = OtherPeriodCardUiState.Unavailable(
        name = UiText(if (price) R.string.price_position_name else R.string.rate_burden_name),
        periodText = UiText(R.string.valuation_period_label, listOf(period)),
        message = UiText(R.string.valuation_period_unavailable, listOf(period)),
    )
}
