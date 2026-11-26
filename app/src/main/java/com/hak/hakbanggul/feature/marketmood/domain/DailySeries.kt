package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import java.time.LocalDate

// 날짜 오름차순으로 정리한 시리즈 하나. 같은 날짜가 여럿이면 앞의 것 하나만 둔다.
// 장세와 매매 판단이 함께 쓴다.
class DailySeries private constructor(
    val dates: List<LocalDate>,
    val values: DoubleArray,
) {
    // 그날이나 그 전의 날 가운데 값이 있는 가장 가까운 날의 위치. 없으면 -1
    fun floorIndex(day: LocalDate): Int {
        val found = dates.binarySearch(day)
        return if (found >= 0) found else -(found + 1) - 1
    }

    // 그날의 종가와 최근 1년 최고 종가로 낸 낙폭(0 이하).
    // 최근 1년 최고 종가: 그날로부터 1년 전의 같은 날부터 그날까지(두 날 포함)의 종가 가운데 가장 높은 값
    fun drawdownAt(index: Int): Double {
        val value = values[index]
        val yearAgo = dates[index].minusYears(1)
        var high = value
        var i = index
        while (i >= 0 && !dates[i].isBefore(yearAgo)) {
            if (values[i] > high) high = values[i]
            i--
        }
        return (value - high) / high
    }

    companion object {
        fun of(values: List<DailyValue>): DailySeries {
            val sorted = values.sortedBy { it.date }.distinctBy { it.date }
            return DailySeries(sorted.map { it.date }, DoubleArray(sorted.size) { sorted[it].value })
        }
    }
}
