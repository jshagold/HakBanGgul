package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.TreeMap

// 단위 4의 테스트 지시에 적힌 데이터 A, B를 만든다. 단위 5, 7의 테스트도 쓴다.
object MoodTestData {
    val STOCKS = listOf("SPY", "QQQ", "XLK", "SMH")
    const val YIELD_CURVE = "T10Y2Y"
    const val HIGH_YIELD = "BAMLH0A0HYM2"

    val START: LocalDate = LocalDate.of(2024, 11, 25)
    val END: LocalDate = LocalDate.of(2026, 11, 23)

    fun date(text: String): LocalDate = LocalDate.parse(text)

    fun weekdays(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .toList()

    // 데이터 A: 주식 넷은 평일마다 종가 100, T10Y2Y 0.60, BAMLH0A0HYM2 3.00
    fun dataA(): Builder {
        val days = weekdays(START, END)
        val builder = Builder()
        STOCKS.forEach { id -> days.forEach { builder.set(id, it, 100.0) } }
        days.forEach {
            builder.set(YIELD_CURVE, it, 0.60)
            builder.set(HIGH_YIELD, it, 3.00)
        }
        return builder
    }

    // 데이터 B: 데이터 A에서 XLK 2026-01-05 120, SMH 2026-11-23 80, T10Y2Y 2026-11-23 0.29,
    // BAMLH0A0HYM2는 2026-11-20까지만 있고 그날 값 3.76
    fun dataB(): Builder = dataA()
        .set("XLK", "2026-01-05", 120.0)
        .set("SMH", "2026-11-23", 80.0)
        .set(YIELD_CURVE, "2026-11-23", 0.29)
        .truncateAfter(HIGH_YIELD, date("2026-11-20"))
        .set(HIGH_YIELD, "2026-11-20", 3.76)

    class Builder {
        private val series = LinkedHashMap<String, TreeMap<LocalDate, Double>>()

        fun set(id: String, day: LocalDate, value: Double) = apply {
            series.getOrPut(id) { TreeMap() }[day] = value
        }

        fun set(id: String, day: String, value: Double) = set(id, date(day), value)

        fun setStocks(day: String, value: Double) = apply { STOCKS.forEach { set(it, day, value) } }

        fun remove(id: String, day: String) = apply { series[id]?.remove(date(day)) }

        fun removeSeries(id: String) = apply { series.remove(id) }

        // 그 시리즈를 day까지만 둔다.
        fun truncateAfter(id: String, day: LocalDate) = apply { series[id]?.tailMap(day, false)?.clear() }

        // 모든 시리즈를 day까지만 둔다.
        fun truncateAllAfter(day: String) = apply { series.keys.forEach { truncateAfter(it, date(day)) } }

        // 나쁜 날: T10Y2Y -0.10, BAMLH0A0HYM2 7.00. 데이터 A에서는 그날 현재 상태가 0.4(중립)가 된다.
        fun badDay(vararg days: String) = apply {
            days.forEach {
                set(YIELD_CURVE, it, -0.10)
                set(HIGH_YIELD, it, 7.00)
            }
        }

        // 그 시리즈를 넘긴 날들의 값만 남긴다.
        fun keepOnly(id: String, keep: (LocalDate) -> Boolean) = apply {
            series[id]?.keys?.retainAll(keep)
        }

        fun build(): Map<String, List<DailyValue>> =
            series.mapValues { (_, values) -> values.map { (day, value) -> DailyValue(day, value) } }

        // 일부 시리즈만 꺼낸다.
        fun build(ids: Collection<String>): Map<String, List<DailyValue>> = build().filterKeys { it in ids }
    }
}
