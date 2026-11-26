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
    const val REAL_YIELD = "DFII10"
    const val TREASURY_10Y = "DGS10"
    const val POLICY_RATE = "DFF"
    const val CONSUMER_PRICE = "CPIAUCSL"

    // 매매 판단의 시리즈 넷
    val VALUATION_SERIES = listOf(REAL_YIELD, TREASURY_10Y, POLICY_RATE, CONSUMER_PRICE)

    // 시험용 규칙: 기본 규칙에서 매매 판단의 짧은 평균 5, 긴 평균 10, 위치 기간 20, 평균 일수 3, 3개월 5로 줄인 것.
    // 데이터 A(2년)로 매매 판단을 낼 수 있게 하려는 것이다.
    val RULES: MoodRules = DefaultMoodRules.rules.let { rules ->
        rules.copy(
            valuation = rules.valuation.copy(
                shortAverageDays = 5,
                longAverageDays = 10,
                positionWindow = 20,
                smoothingDays = 3,
                threeMonthDays = 5,
            ),
        )
    }

    val START: LocalDate = LocalDate.of(2024, 11, 25)
    val END: LocalDate = LocalDate.of(2026, 11, 23)

    fun date(text: String): LocalDate = LocalDate.parse(text)

    // end로 끝나는 평일 count개, 날짜 오름차순
    fun weekdaysEndingAt(end: LocalDate, count: Int): List<LocalDate> =
        generateSequence(end) { it.minusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(count)
            .toList()
            .reversed()

    // 위치 기간 101, 평균 일수 1에서 날마다 바라는 점수가 나오는 값들.
    // 앞 100개는 서로 다른 값이고, 그 뒤 날마다 앞 100개 가운데 그날의 값보다 작은 값의 수가 점수다(같은 값 없음).
    fun valuesForScores(scores: List<Int>): List<Double> {
        val values = MutableList(100) { it.toDouble() }
        for (k in scores) {
            val others = values.takeLast(100).sorted()
            values += when (k) {
                0 -> others.first() - 1
                100 -> others.last() + 1
                else -> (others[k - 1] + others[k]) / 2
            }
        }
        return values
    }

    // 짧은 평균과 긴 평균이 2일 때 날마다의 괴리가 바라는 점수열을 만드는 종가들(위치 기간 101, 평균 일수 1).
    // 괴리 d = (c - 전날) ÷ (c + 전날)이라 c = 전날 × (1 + d) ÷ (1 - d)다.
    fun closesForScores(scores: List<Int>): List<Double> {
        val deviations = valuesForScores(scores).map { it / 1000 - 0.05 }
        val closes = mutableListOf(100.0)
        deviations.forEach { d -> closes += closes.last() * (1 + d) / (1 - d) }
        return closes
    }

    fun weekdays(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .toList()

    // 데이터 A: 주식 넷은 평일마다 종가 100, T10Y2Y 0.60, BAMLH0A0HYM2 3.00,
    // DFII10 2.00, DGS10 4.00, DFF 3.50 (모두 주식과 같은 날들),
    // CPIAUCSL 2024-11-01부터 2025-10-01까지 매달 1일 300.0, 2025-11-01부터 2026-10-01까지 매달 1일 309.0
    fun dataA(): Builder {
        val days = weekdays(START, END)
        val builder = Builder()
        STOCKS.forEach { id -> days.forEach { builder.set(id, it, 100.0) } }
        days.forEach {
            builder.set(YIELD_CURVE, it, 0.60)
            builder.set(HIGH_YIELD, it, 3.00)
            builder.set(REAL_YIELD, it, 2.00)
            builder.set(TREASURY_10Y, it, 4.00)
            builder.set(POLICY_RATE, it, 3.50)
        }
        generateSequence(date("2024-11-01")) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(date("2026-10-01")) }
            .forEach { builder.set(CONSUMER_PRICE, it, if (it.isBefore(date("2025-11-01"))) 300.0 else 309.0) }
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

        // 그 시리즈를 end로 끝나는 평일마다의 값들로 통째로 바꾼다.
        fun replaceSeries(id: String, values: List<Double>, end: LocalDate = END) = apply {
            series.remove(id)
            weekdaysEndingAt(end, values.size).zip(values).forEach { (day, value) -> set(id, day, value) }
        }

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
