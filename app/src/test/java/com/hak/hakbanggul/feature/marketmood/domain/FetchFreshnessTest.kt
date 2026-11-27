package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

// 지금 2026-11-24T01:00:00Z, 시험용 규칙(여유 30분), 받아 둔 값 데이터 A
class FetchFreshnessTest {

    private val freshness = FetchFreshness()
    private val now = Instant.parse("2026-11-24T01:00:00Z")
    private val rules = MoodTestData.RULES
    private val etfs = listOf("SPY", "QQQ", "SMH", "XLK")
    private val fresh = Instant.parse("2026-11-23T22:00:00Z")

    private fun t(text: String) = Instant.parse(text)

    private fun stale(
        fetchedAt: Map<String, Instant>,
        values: MoodTestData.Builder = dataA(),
        now: Instant = this.now,
        rules: MoodRules = this.rules,
    ): List<String> = freshness.staleTargets(rules, values.build(), fetchedAt, now).map { it.closeSeries.seriesId }

    private fun allAt(at: Instant) = etfs.associateWith { at }

    // R-109 staleTargets

    @Test
    fun `R-109 넷 모두 장 마감 뒤에 받았으면 빈 목록`() {
        assertEquals(emptyList<String>(), stale(allAt(fresh)))
    }

    @Test
    fun `R-109 SPY만 장 마감 1초 전이면 SPY, 꼭 장 마감이면 빈 목록`() {
        assertEquals(listOf("SPY"), stale(allAt(fresh) + ("SPY" to t("2026-11-23T21:29:59Z"))))
        assertEquals(emptyList<String>(), stale(allAt(fresh) + ("SPY" to t("2026-11-23T21:30:00Z"))))
    }

    @Test
    fun `R-109 XLK의 받은 때가 없거나 받아 둔 XLK 값이 없으면 XLK`() {
        assertEquals(listOf("XLK"), stale(allAt(fresh) - "XLK"))
        assertEquals(listOf("XLK"), stale(allAt(fresh), dataA().removeSeries("XLK")))
    }

    @Test
    fun `R-109 받은 때가 지금보다 뒤면 최신이 아니다`() {
        assertEquals(listOf("QQQ"), stale(allAt(fresh) + ("QQQ" to t("2026-11-24T01:00:01Z"))))
    }

    @Test
    fun `R-109 받은 때가 하나도 없으면 넷 모두 규칙의 순서로`() {
        assertEquals(listOf("SPY", "QQQ", "SMH", "XLK"), stale(emptyMap()))
    }

    @Test
    fun `R-109 일요일에는 금요일 장 마감으로 가른다`() {
        val sunday = t("2026-11-29T15:00:00Z")
        assertEquals(emptyList<String>(), stale(allAt(t("2026-11-27T21:31:00Z")), now = sunday))
        assertEquals(etfs, stale(allAt(t("2026-11-27T21:00:00Z")), now = sunday))
    }

    @Test
    fun `R-109 여유는 규칙의 것을 쓴다`() {
        val at = allAt(t("2026-11-23T21:10:00Z"))
        assertEquals(emptyList<String>(), stale(at, rules = rules.copy(closeGraceMinutes = 0)))
        assertEquals(etfs, stale(at))
    }

    @Test
    fun `R-109 받은 종가의 마지막 날짜로 가르지 않는다`() {
        val values = dataA().truncateAfter("SPY", MoodTestData.date("2026-11-20"))
        assertEquals(emptyList<String>(), stale(allAt(fresh), values))
    }

    // R-110, P-58 fredFetchedAt

    private val fredIds = listOf(MoodTestData.YIELD_CURVE, MoodTestData.HIGH_YIELD) + MoodTestData.VALUATION_SERIES

    @Test
    fun `R-110 FRED 시리즈의 받은 때 가운데 가장 이른 때이고 ETF는 보지 않는다`() {
        val t1 = t("2026-11-23T23:00:00Z")
        val t0 = t("2026-11-23T20:00:00Z")
        val fetchedAt = fredIds.associateWith { t1 } + (MoodTestData.POLICY_RATE to t0) + ("SPY" to t("2026-11-23T19:00:00Z"))
        assertEquals(t0, freshness.fredFetchedAt(rules, fetchedAt))
    }

    @Test
    fun `R-110 FRED 시리즈 하나라도 받은 때가 없으면 null`() {
        val fetchedAt = fredIds.associateWith { t("2026-11-23T23:00:00Z") } - MoodTestData.CONSUMER_PRICE
        assertNull(freshness.fredFetchedAt(rules, fetchedAt))
    }
}
