package com.hak.hakbanggul.feature.marketmood.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MarketCloseTimesTest {

    private fun t(text: String) = Instant.parse(text)

    private fun latest(now: String, grace: Int = 30) = MarketCloseTimes.latestClose(t(now), grace)

    private fun next(now: String, grace: Int = 30) = MarketCloseTimes.nextDailyClose(t(now), grace)

    // R-109, P-57 가장 최근 장 마감

    @Test
    fun `R-109 월요일 오후 8시면 그날 오후 4시 30분`() {
        assertEquals(t("2026-11-23T21:30:00Z"), latest("2026-11-24T01:00:00Z"))
    }

    @Test
    fun `R-109 꼭 장 마감이면 그때이고 1초 전이면 금요일`() {
        assertEquals(t("2026-11-23T21:30:00Z"), latest("2026-11-23T21:30:00Z"))
        assertEquals(t("2026-11-20T21:30:00Z"), latest("2026-11-23T21:29:59Z"))
    }

    @Test
    fun `R-109 월요일 아침, 일요일, 토요일이면 금요일`() {
        assertEquals(t("2026-11-20T21:30:00Z"), latest("2026-11-23T14:00:00Z"))
        assertEquals(t("2026-11-27T21:30:00Z"), latest("2026-11-29T15:00:00Z"))
        assertEquals(t("2026-11-27T21:30:00Z"), latest("2026-11-28T12:00:00Z"))
    }

    @Test
    fun `R-109 여름 시간`() {
        assertEquals(t("2026-07-15T20:30:00Z"), latest("2026-07-15T21:00:00Z"))
        assertEquals(t("2026-07-14T20:30:00Z"), latest("2026-07-15T20:29:00Z"))
    }

    @Test
    fun `R-109 서머타임이 시작된 다음 날 월요일과 그 앞 금요일의 겨울 시간`() {
        assertEquals(t("2027-03-15T20:30:00Z"), latest("2027-03-15T21:00:00Z"))
        assertEquals(t("2027-03-12T21:30:00Z"), latest("2027-03-15T20:00:00Z"))
    }

    @Test
    fun `R-109 여유 0분`() {
        assertEquals(t("2026-11-23T21:00:00Z"), latest("2026-11-23T21:10:00Z", grace = 0))
    }

    // R-115, P-62 다음 날마다의 장 마감

    @Test
    fun `R-115 다음 장 마감은 다음 날 오후 4시 30분이고 꼭 같으면 다음 날`() {
        assertEquals(t("2026-11-24T21:30:00Z"), next("2026-11-24T01:00:00Z"))
        assertEquals(t("2026-11-24T21:30:00Z"), next("2026-11-24T21:29:00Z"))
        assertEquals(t("2026-11-25T21:30:00Z"), next("2026-11-24T21:30:00Z"))
    }

    @Test
    fun `R-115 주말도 날마다`() {
        assertEquals(t("2026-11-28T21:30:00Z"), next("2026-11-28T12:00:00Z"))
    }

    @Test
    fun `R-115 서머타임 시작과 끝`() {
        assertEquals(t("2027-03-14T20:30:00Z"), next("2027-03-13T22:00:00Z"))
        assertEquals(t("2026-11-01T21:30:00Z"), next("2026-10-31T21:00:00Z"))
    }
}
