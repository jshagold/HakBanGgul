package com.hak.hakbanggul.feature.marketmood.presentation

import java.math.BigDecimal
import java.math.RoundingMode

// 숫자를 화면의 글로 바꾼다. Android에 기대지 않는다.
// 반올림은 값의 십진 표기를 기준으로 한 사사오입이다(0.005는 0.01). 음수 부호는 하이픈, 0에는 부호를 붙이지 않는다.
class MoodTextFormatter {

    // 항목 점수: 소수 한 자리. +1.0, 0.0, -0.5
    fun itemScore(score: Double): String = signed(round(score, 1))

    // 묶음 점수와 종합 점수: 소수 두 자리. +0.38
    fun groupScore(score: Double): String = signed(round(score, 2))

    // 그 자릿수로 반올림해 적는 값의 부호로 색 종류를 고른다.
    fun scoreTone(score: Double, decimals: Int): ScoreTone = when (round(score, decimals).signum()) {
        1 -> ScoreTone.POSITIVE
        -1 -> ScoreTone.NEGATIVE
        else -> ScoreTone.ZERO
    }

    // 비율에 100을 곱해 부호와 소수 한 자리. +4.1%
    fun signedPercent(ratio: Double): String = signed(BigDecimal.valueOf(ratio).movePointRight(2).setScale(1, RoundingMode.HALF_UP)) + "%"

    // 부호와 소수 두 자리. +0.29%p
    fun signedPercentPoint(value: Double): String = signed(round(value, 2)) + "%p"

    // 부호 없이 소수 두 자리. 3.76%
    fun percent(value: Double): String = round(value, 2).toPlainString() + "%"

    // 비중에 100을 곱해 반올림한 정수. 0.7은 70
    fun weightPercent(weight: Double): Int =
        BigDecimal.valueOf(weight).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt()

    // 비율에 100을 곱해 부호 없이 소수 한 자리. 0.037은 3.7%
    fun ratioPercent(ratio: Double): String = ratioToPercent(ratio).toPlainString() + "%"

    // 물가 상승률을 소수 한 자리 퍼센트로 반올림한 값에서 목표(퍼센트)를 뺀 차이. 부호와 소수 한 자리. (0.041, 0.02)는 +2.1%p
    fun inflationGap(rate: Double, target: Double): String {
        val gap = ratioToPercent(rate).subtract(BigDecimal.valueOf(target).movePointRight(2)).setScale(1, RoundingMode.HALF_UP)
        return signed(gap) + "%p"
    }

    // 목표에 100을 곱해 끝의 0 없이. 0.02는 2, 0.025는 2.5
    fun targetPercent(target: Double): String =
        BigDecimal.valueOf(target).movePointRight(2).stripTrailingZeros().let {
            if (it.scale() < 0) it.setScale(0) else it
        }.toPlainString()

    private fun ratioToPercent(ratio: Double): BigDecimal =
        BigDecimal.valueOf(ratio).movePointRight(2).setScale(1, RoundingMode.HALF_UP)

    private fun round(value: Double, decimals: Int): BigDecimal =
        BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)

    private fun signed(rounded: BigDecimal): String = when (rounded.signum()) {
        1 -> "+" + rounded.toPlainString()
        -1 -> rounded.toPlainString()
        else -> rounded.toPlainString()
    }
}
