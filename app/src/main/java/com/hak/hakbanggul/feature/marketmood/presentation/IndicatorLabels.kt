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

    // 주식 항목의 비율 근거를 적는 법. 일수와 기간의 이름은 그 기간의 규칙에서 끼운다.
    enum class RatioFormat {
        // 평균 괴리: "50일 평균보다 +1.2%"
        AVERAGE_DEVIATION,

        // 고점 대비 낙폭: "최근 3개월 고점보다 -3.0%"
        DROP_FROM_HIGH,

        // 평균의 기울기: "50일 평균이 20일 전보다 +0.4%"
        AVERAGE_SLOPE,
    }

    data class Label(
        @param:StringRes val nameRes: Int,
        // 항목 이름 옆의 덧붙이는 글. 없으면 null
        @param:StringRes val suffixRes: Int? = null,
        // 날마다의 값을 쓰는 항목만 있다.
        val valueFormat: ValueFormat? = null,
        // 비율 하나를 근거로 쓰는 항목만 있다.
        val ratioFormat: RatioFormat? = null,
    )

    private val groups = mapOf(
        "stocks" to R.string.group_stocks,
        "bond_signals" to R.string.group_bond_signals,
    )

    private val indicators = mapOf(
        "above_average" to Label(R.string.indicator_above_average, ratioFormat = RatioFormat.AVERAGE_DEVIATION),
        "from_high" to Label(R.string.indicator_from_high, ratioFormat = RatioFormat.DROP_FROM_HIGH),
        "average_slope" to Label(R.string.indicator_average_slope, ratioFormat = RatioFormat.AVERAGE_SLOPE),
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
