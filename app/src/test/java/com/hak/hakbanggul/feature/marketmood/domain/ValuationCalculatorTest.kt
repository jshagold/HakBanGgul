package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.date
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.closesForScores
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.valuesForScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ValuationCalculatorTest {

    private val calculator = ValuationCalculator()
    private val end: LocalDate = date("2026-11-23")

    private val spy = "SPY"
    private val realYield = "DFII10"
    private val treasury = "DGS10"
    private val policy = "DFF"
    private val cpi = "CPIAUCSL"

    // 작은 규칙: 숫자를 줄여 확인하기 쉽게 한 규칙. 1년 전은 1일로 줄여 적은 값으로도 금리 부담을 낼 수 있게 한다.
    private val small: ValuationRules = DefaultMoodRules.rules.valuation.copy(
        shortAverageDays = 2,
        longAverageDays = 3,
        shortWeight = 2.0,
        longWeight = 1.0,
        positionWindow = 3,
        smoothingDays = 1,
        threeMonthDays = 2,
        yearAgoDays = 1,
    )

    // 점수로 정한 단계와 표시 단계를 볼 때의 규칙: 위치 기간 101, 평균 일수 1이면 점수가 "앞 100개 값 가운데 그날의 값보다 작은 값의 수"다.
    // 주가 위치는 짧은 평균과 긴 평균을 둘 다 2로 두어 두 괴리가 같게 한다.
    private val stageRules: ValuationRules = small.copy(
        shortAverageDays = 2,
        longAverageDays = 2,
        positionWindow = 101,
        smoothingDays = 1,
        lowBoundary = 30,
        highBoundary = 70,
        buffer = 5,
    )

    private fun series(values: List<Double>, endDay: LocalDate = end): List<DailyValue> =
        MoodTestData.weekdaysEndingAt(endDay, values.size).zip(values) { day, value -> DailyValue(day, value) }

    private fun monthly(vararg values: Pair<String, Double>): List<DailyValue> =
        values.map { (month, value) -> DailyValue(YearMonth.parse(month).atDay(1), value) }

    // 모든 쪽을 낼 수 있는 기본 값: 평일 count개씩 SPY 100, 실질금리 2.0, 10년물 4.0, 기준금리 3.5, 소비자물가 2년 동안 매달 300
    private fun base(endDay: LocalDate = end, count: Int = 300): Map<String, List<DailyValue>> {
        val lastMonth = YearMonth.from(endDay)
        return mapOf(
            spy to series(List(count) { 100.0 }, endDay),
            realYield to series(List(count) { 2.0 }, endDay),
            treasury to series(List(count) { 4.0 }, endDay),
            policy to series(List(count) { 3.5 }, endDay),
            cpi to (24 downTo 0).map { DailyValue(lastMonth.minusMonths(it.toLong()).atDay(1), 300.0) },
        )
    }

    private fun assess(rules: ValuationRules, values: Map<String, List<DailyValue>>, asOf: LocalDate = end): ValuationAssessment =
        requireNotNull(calculator.assess(rules, values, asOf)) { "매매 판단을 낼 수 있어야 한다" }

    // R-72, R-73, R-74 두 괴리, 3개월 수익률, 낼 수 있는 날

    @Test
    fun `R-72 다섯 날의 두 괴리, 주가 위치 점수 42, 3개월 수익률`() {
        val result = assess(small, base() + (spy to series(listOf(10.0, 12.0, 11.0, 13.0, 12.0)))).pricePosition
        assertEquals(-0.04, result.shortDeviation, 1e-9)
        assertEquals(0.0, result.longDeviation, 1e-9)
        assertEquals(42, result.score)
        assertEquals(12.0 / 11.0 - 1, result.threeMonthReturn, 1e-9)
    }

    @Test
    fun `R-72 여섯째 날 15를 더하면 점수 100`() {
        val result = assess(small, base() + (spy to series(listOf(10.0, 12.0, 11.0, 13.0, 12.0, 15.0)))).pricePosition
        assertEquals(15.0 / 13.5 - 1, result.shortDeviation, 1e-9)
        assertEquals(0.125, result.longDeviation, 1e-9)
        assertEquals(100, result.score)
        // 기준 날짜(여섯째 날)보다 threeMonthDays(2)개 앞의 날은 넷째 날(13)이다. (SPEC-ISSUE: 지시의 15 ÷ 11 - 1과 다름)
        assertEquals(15.0 / 13.0 - 1, result.threeMonthReturn, 1e-9)
    }

    @Test
    fun `R-74 평균 일수 2면 점수 71`() {
        val rules = small.copy(smoothingDays = 2)
        val result = assess(rules, base() + (spy to series(listOf(10.0, 12.0, 11.0, 13.0, 12.0, 15.0)))).pricePosition
        assertEquals(71, result.score)
    }

    @Test
    fun `R-72 넷째 날에는 긴 괴리가 위치 기간만큼 없어 null`() {
        val closes = series(listOf(10.0, 12.0, 11.0, 13.0, 12.0))
        assertNull(calculator.assess(small, base() + (spy to closes), closes[3].date))
    }

    @Test
    fun `R-72 기본 규칙에서 SPY 종가 2034개면 매매 판단이 있고 2033개면 null`() {
        val rules = DefaultMoodRules.rules.valuation
        val others = base(count = 1300)
        assertNotNull(calculator.assess(rules, others + (spy to series(List(2034) { 100.0 + it % 7 })), end))
        assertNull(calculator.assess(rules, others + (spy to series(List(2033) { 100.0 + it % 7 })), end))
    }

    // R-73 같은 값

    @Test
    fun `R-73 같은 값은 반으로 센다`() {
        val rules = small.copy(positionWindow = 5, smoothingDays = 1)
        val result = assess(rules, base() + (realYield to series(listOf(1.0, 2.0, 2.0, 3.0, 2.0)))).rateBurden
        assertEquals(50, result.score)
    }

    // R-75 점수, 반올림

    @Test
    fun `R-75 12_5는 13으로 올린다`() {
        val rules = small.copy(positionWindow = 5, smoothingDays = 2)
        val result = assess(rules, base() + (realYield to series(listOf(5.0, 4.0, 3.0, 2.0, 1.0, 1.5)))).rateBurden
        assertEquals(13, result.score)
    }

    @Test
    fun `R-75 실질금리 값이 위치 기간보다 적으면 null`() {
        val rules = small.copy(positionWindow = 5, smoothingDays = 1)
        assertNull(calculator.assess(rules, base() + (realYield to series(listOf(1.0, 2.0, 3.0, 4.0))), end))
    }

    @Test
    fun `R-75 기본 규칙에서 실질금리 값 1279개면 금리 부담이 있고 1278개면 null`() {
        val rules = DefaultMoodRules.rules.valuation
        val others = base(count = 2100)
        assertNotNull(calculator.assess(rules, others + (realYield to series(List(1279) { 2.0 + it % 5 * 0.1 })), end))
        assertNull(calculator.assess(rules, others + (realYield to series(List(1278) { 2.0 + it % 5 * 0.1 })), end))
    }

    // R-78

    @Test
    fun `R-78 실질금리가 기준 날짜에 없으면 그 전의 가장 가까운 날의 것`() {
        val values = base() + (realYield to series(List(30) { it + 1.0 }, date("2026-11-20")))
        val result = assess(small, values).rateBurden
        assertEquals(date("2026-11-20"), result.realYieldDate)
        assertEquals(30.0, result.realYield, 0.0)
        assertEquals(100, result.score)
    }

    @Test
    fun `R-78 10년물과 기준금리는 각각 그 전의 가장 가까운 날의 값과 날짜`() {
        val values = base() +
            (treasury to series(List(30) { 4.0 } + 4.92, date("2026-11-20"))) +
            (policy to series(List(30) { 3.5 } + 3.88))
        val result = assess(small, values).rateBurden
        assertEquals(DatedValue(4.92, date("2026-11-20")), result.treasury10y)
        assertEquals(DatedValue(3.88, date("2026-11-23")), result.policyRate)
    }

    @Test
    fun `R-78 10년물의 값이 모두 기준 날짜 뒤면 null, 기준금리도 같다`() {
        val later = listOf(DailyValue(date("2026-11-24"), 4.0), DailyValue(date("2026-11-25"), 4.1))
        assertNull(calculator.assess(small, base() + (treasury to later), end))
        assertNull(calculator.assess(small, base() + (policy to later), end))
    }

    @Test
    fun `R-78 주가 위치는 기준 날짜 뒤에 종가가 더 있어도 기준 날짜의 것`() {
        val closes = listOf(10.0, 12.0, 11.0, 13.0, 12.0, 15.0, 9.0, 20.0)
        val untilEnd = base() + (spy to series(closes))
        val withLater = base() + (spy to series(closes + listOf(30.0, 5.0), date("2026-11-25")))
        assertEquals(assess(small, untilEnd).pricePosition, assess(small, withLater).pricePosition)
    }

    // R-79

    @Test
    fun `R-79 1년 전과의 차이는 365일 앞이나 그 전의 가장 가까운 날의 값과 비교한다`() {
        val rules = small.copy(yearAgoDays = 365)
        val days = MoodTestData.weekdays(date("2025-11-10"), end)
        val yields = days.map { day ->
            val value = when (day) {
                date("2025-11-21") -> 1.82
                date("2025-11-24") -> 1.90
                end -> 2.79
                else -> 2.0
            }
            DailyValue(day, value)
        }
        val result = assess(rules, base() + (realYield to yields)).rateBurden
        assertEquals(0.97, result.yearChange, 1e-9)
    }

    @Test
    fun `R-79 실질금리가 365일 앞보다 뒤부터만 있으면 null`() {
        val rules = small.copy(yearAgoDays = 365)
        val yields = MoodTestData.weekdays(date("2025-11-24"), end).map { DailyValue(it, 2.0) }
        assertNull(calculator.assess(rules, base() + (realYield to yields), end))
    }

    // R-80

    @Test
    fun `R-80 12달 전 값이 있는 가장 최근 달로 상승률을 낸다`() {
        val prices = monthly(
            "2025-09" to 300.0,
            "2025-11" to 302.0, "2025-12" to 303.0, "2026-01" to 304.0, "2026-02" to 305.0, "2026-03" to 306.0,
            "2026-04" to 307.0, "2026-05" to 308.0, "2026-06" to 309.0, "2026-07" to 310.0, "2026-08" to 310.5,
            "2026-09" to 311.1, "2026-10" to 312.0,
        )
        val result = assess(small, base() + (cpi to prices)).rateBurden
        assertEquals(0.037, result.inflationRate, 1e-9)
        assertEquals(YearMonth.of(2026, 9), result.inflationMonth)
    }

    @Test
    fun `R-80 12달 전 값이 있는 달이 없으면 null`() {
        val prices = (0 until 12).map { DailyValue(YearMonth.of(2025, 11).plusMonths(it.toLong()).atDay(1), 300.0 + it) }
        assertNull(calculator.assess(small, base() + (cpi to prices), end))
    }

    @Test
    fun `R-80 기준 날짜 뒤의 달은 쓰지 않는다`() {
        val asOf = date("2026-10-30")
        val prices = monthly("2025-10" to 300.0, "2026-10" to 312.0, "2026-11" to 313.0)
        val result = assess(small, base(asOf) + (cpi to prices), asOf).rateBurden
        assertEquals(YearMonth.of(2026, 10), result.inflationMonth)
        assertEquals(0.04, result.inflationRate, 1e-9)
    }

    // R-76 점수로 정한 단계

    private fun rateStages(scores: List<Int>, rules: ValuationRules = stageRules, endDay: LocalDate = end): RateBurdenAssessment =
        assess(rules, base() + (realYield to series(valuesForScores(scores), endDay))).rateBurden

    private fun priceStages(scores: List<Int>, rules: ValuationRules = stageRules): PricePositionAssessment =
        assess(rules, base() + (spy to series(closesForScores(scores)))).pricePosition

    @Test
    fun `R-76 첫날의 점수 30, 31, 69, 70은 LOW, MIDDLE, MIDDLE, HIGH`() {
        val expected = mapOf(30 to ValuationStage.LOW, 31 to ValuationStage.MIDDLE, 69 to ValuationStage.MIDDLE, 70 to ValuationStage.HIGH)
        expected.forEach { (score, stage) ->
            val result = rateStages(listOf(score))
            assertEquals(score, result.score)
            assertEquals("점수로 정한 단계 $score", stage, result.scoredStage)
            assertEquals("표시 단계 $score", stage, result.displayedStage)
        }
    }

    // R-77 완충

    private fun assertRateStages(scores: List<Int>, displayed: ValuationStage, scored: ValuationStage? = null) {
        val result = rateStages(scores)
        assertEquals(scores.last(), result.score)
        assertEquals("표시 단계 $scores", displayed, result.displayedStage)
        if (scored != null) assertEquals("점수로 정한 단계 $scores", scored, result.scoredStage)
    }

    @Test
    fun `R-77 30, 35는 표시 LOW이고 점수로 정한 단계 MIDDLE`() = assertRateStages(listOf(30, 35), ValuationStage.LOW, ValuationStage.MIDDLE)

    @Test
    fun `R-77 30, 36은 표시 MIDDLE`() = assertRateStages(listOf(30, 36), ValuationStage.MIDDLE)

    @Test
    fun `R-77 30, 70은 표시 HIGH`() = assertRateStages(listOf(30, 70), ValuationStage.HIGH)

    @Test
    fun `R-77 70, 65는 표시 HIGH이고 점수로 정한 단계 MIDDLE`() = assertRateStages(listOf(70, 65), ValuationStage.HIGH, ValuationStage.MIDDLE)

    @Test
    fun `R-77 70, 64는 표시 MIDDLE`() = assertRateStages(listOf(70, 64), ValuationStage.MIDDLE)

    @Test
    fun `R-77 70, 30은 표시 LOW`() = assertRateStages(listOf(70, 30), ValuationStage.LOW)

    @Test
    fun `R-77 30, 35, 33, 36, 34는 표시 MIDDLE`() = assertRateStages(listOf(30, 35, 33, 36, 34), ValuationStage.MIDDLE)

    @Test
    fun `R-77 50, 33은 표시 MIDDLE`() = assertRateStages(listOf(50, 33), ValuationStage.MIDDLE)

    @Test
    fun `R-77 주가 위치도 30, 35는 표시 LOW이고 점수로 정한 단계 MIDDLE`() {
        val result = priceStages(listOf(30, 35))
        assertEquals(35, result.score)
        assertEquals(ValuationStage.LOW, result.displayedStage)
        assertEquals(ValuationStage.MIDDLE, result.scoredStage)
    }

    @Test
    fun `R-77 주가 위치 70, 64는 표시 MIDDLE`() {
        val result = priceStages(listOf(70, 64))
        assertEquals(64, result.score)
        assertEquals(ValuationStage.MIDDLE, result.displayedStage)
    }

    @Test
    fun `R-77 금리 부담의 표시 단계는 실질금리의 날까지만 따진다`() {
        val result = rateStages(listOf(30, 35), endDay = date("2026-11-20"))
        assertEquals(date("2026-11-20"), result.realYieldDate)
        assertEquals(35, result.score)
        assertEquals(ValuationStage.LOW, result.displayedStage)
    }

    // R-68 낙폭, R-11, R-32

    private fun drawdownCloses(from: String): List<DailyValue> =
        MoodTestData.weekdays(date(from), end).map { day ->
            DailyValue(
                day,
                when (day) {
                    date("2025-11-21") -> 250.0
                    date("2025-11-24") -> 200.0
                    end -> 184.0
                    else -> 190.0 - (day.dayOfMonth % 3)
                },
            )
        }

    @Test
    fun `R-68 낙폭은 최근 1년 최고 종가에서의 하락률`() {
        val result = assess(small, base() + (spy to drawdownCloses("2025-11-24"))).pricePosition
        assertEquals(-0.08, result.drawdown, 1e-9)
    }

    @Test
    fun `R-68 1년 전 같은 날보다 앞의 고점은 쓰지 않는다`() {
        val result = assess(small, base() + (spy to drawdownCloses("2025-11-21"))).pricePosition
        assertEquals(-0.08, result.drawdown, 1e-9)
    }

    // R-40 매매 판단의 값

    private val expectedForDataA = ValuationAssessment(
        pricePosition = PricePositionAssessment(
            score = 50,
            scoredStage = ValuationStage.MIDDLE,
            displayedStage = ValuationStage.MIDDLE,
            shortDeviation = 0.0,
            longDeviation = 0.0,
            drawdown = 0.0,
            threeMonthReturn = 0.0,
        ),
        rateBurden = RateBurdenAssessment(
            realYieldDate = end,
            score = 50,
            scoredStage = ValuationStage.MIDDLE,
            displayedStage = ValuationStage.MIDDLE,
            realYield = 2.0,
            yearChange = 0.0,
            treasury10y = DatedValue(4.0, end),
            policyRate = DatedValue(3.5, end),
            inflationRate = 309.0 / 300.0 - 1,
            inflationMonth = YearMonth.of(2026, 10),
        ),
        lowBoundary = 30,
        highBoundary = 70,
        buffer = 5,
        inflationTarget = 0.02,
    )

    @Test
    fun `R-40 데이터 A와 시험용 규칙의 매매 판단`() {
        val result = assess(MoodTestData.RULES.valuation, dataA().build())
        assertEquals(expectedForDataA, result)
        assertEquals(0.03, result.rateBurden.inflationRate, 1e-9)
    }

    @Test
    fun `R-40 데이터 A에서 시리즈 하나를 빼면 null`() {
        listOf(realYield, treasury, policy, cpi, spy).forEach { id ->
            assertNull(id, calculator.assess(MoodTestData.RULES.valuation, dataA().removeSeries(id).build(), end))
        }
    }

    companion object {
        // 다른 테스트가 데이터 A의 매매 판단과 견줄 때 쓴다.
        fun dataAValuation(): ValuationAssessment =
            requireNotNull(ValuationCalculator().assess(MoodTestData.RULES.valuation, dataA().build(), MoodTestData.END))
    }
}
