package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.annotation.StringRes
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.MarketTarget

// 대상 ID마다의 화면 이름. 규칙에 대상을 더할 때 여기와 strings.xml에 이름을 더한다.
// ETF 기호는 따로 두지 않고 대상의 종가 시리즈 ID를 쓴다.
object TargetLabels {

    private val names = mapOf(
        "broad_market" to R.string.target_broad_market,
        "nasdaq_100" to R.string.target_nasdaq_100,
        "semiconductors" to R.string.target_semiconductors,
        "technology" to R.string.target_technology,
        "financials" to R.string.target_financials,
        "energy" to R.string.target_energy,
        "health_care" to R.string.target_health_care,
        "consumer_discretionary" to R.string.target_consumer_discretionary,
        "consumer_staples" to R.string.target_consumer_staples,
        "industrials" to R.string.target_industrials,
        "materials" to R.string.target_materials,
        "utilities" to R.string.target_utilities,
        "real_estate" to R.string.target_real_estate,
        "communication_services" to R.string.target_communication_services,
    )

    @StringRes
    fun nameRes(targetId: String): Int = names[targetId] ?: error("대상 $targetId 의 이름 문구가 없다")

    fun name(target: MarketTarget): UiText = UiText(nameRes(target.id))

    fun symbol(target: MarketTarget): String = target.closeSeries.seriesId

    // "전체 시장 SPY", "반도체 SMH"
    fun nameWithSymbol(target: MarketTarget): UiText = UiText(R.string.target_with_symbol, listOf(name(target), symbol(target)))

    // 장세의 이름: 전체 시장은 "전체 장세", 섹터는 "{이름} 장세"
    fun regimeName(target: MarketTarget, isBroadMarket: Boolean): UiText =
        if (isBroadMarket) UiText(R.string.regime_broad_market) else UiText(R.string.regime_sector, listOf(name(target)))
}
