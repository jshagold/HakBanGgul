package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.annotation.StringRes
import com.hak.hakbanggul.R

// 묶음 ID와 항목 ID마다의 문구. 규칙에 항목을 더할 때 여기와 strings.xml에 이름을 더한다.
object IndicatorLabels {

    // 날마다의 값을 적는 법
    enum class ValueFormat {
        // +0.29%p
        SIGNED_PERCENT_POINT,

        // 3.76%
        PERCENT,
    }

    data class Label(
        @param:StringRes val nameRes: Int,
        // 항목 이름 옆의 덧붙이는 글. 없으면 null
        @param:StringRes val suffixRes: Int? = null,
        // 날마다의 값을 쓰는 항목만 있다.
        val valueFormat: ValueFormat? = null,
    )

    private val groups = mapOf(
        "stocks" to R.string.group_stocks,
        "bond_signals" to R.string.group_bond_signals,
    )

    private val indicators = mapOf(
        "broad_market" to Label(R.string.indicator_broad_market, R.string.indicator_broad_market_suffix),
        "nasdaq_100" to Label(R.string.indicator_nasdaq_100, R.string.indicator_nasdaq_100_suffix),
        "technology" to Label(R.string.indicator_technology, R.string.indicator_technology_suffix),
        "semiconductors" to Label(R.string.indicator_semiconductors, R.string.indicator_semiconductors_suffix),
        "yield_curve" to Label(
            R.string.indicator_yield_curve,
            R.string.indicator_yield_curve_suffix,
            ValueFormat.SIGNED_PERCENT_POINT,
        ),
        "high_yield_spread" to Label(R.string.indicator_high_yield_spread, null, ValueFormat.PERCENT),
    )

    @StringRes
    fun groupNameRes(groupId: String): Int = groups[groupId] ?: error("묶음 $groupId 의 이름 문구가 없다")

    fun indicator(indicatorId: String): Label = indicators[indicatorId] ?: error("항목 $indicatorId 의 이름 문구가 없다")
}
