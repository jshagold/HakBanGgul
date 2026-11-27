package com.hak.hakbanggul.feature.marketmood.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// 용어: 장 마감
// 미국 동부 시간(서머타임 포함) 오후 4시에 여유 시간을 더한 때. 휴장일 달력은 두지 않는다(휴장일도 평일처럼 본다).
object MarketCloseTimes {
    private val MARKET_ZONE: ZoneId = ZoneId.of("America/New_York")
    private val MARKET_CLOSE: LocalTime = LocalTime.of(16, 0)

    // 평일(월~금)의 장 마감 가운데 now보다 앞이거나 같은 가장 늦은 때
    fun latestClose(now: Instant, closeGraceMinutes: Int): Instant {
        var day = now.atZone(MARKET_ZONE).toLocalDate()
        while (true) {
            if (day.isWeekday()) {
                val close = closeOf(day, closeGraceMinutes)
                if (!close.isAfter(now)) return close
            }
            day = day.minusDays(1)
        }
    }

    // 요일과 상관없이 날마다의 장 마감 가운데 now보다 뒤인 가장 이른 때(꼭 같으면 다음 날)
    fun nextDailyClose(now: Instant, closeGraceMinutes: Int): Instant {
        // 여유가 길면 미국 동부 날짜의 전날 것이 아직 오지 않았을 수 있어 하루 앞부터 본다.
        var day = now.atZone(MARKET_ZONE).toLocalDate().minusDays(1)
        while (true) {
            val close = closeOf(day, closeGraceMinutes)
            if (close.isAfter(now)) return close
            day = day.plusDays(1)
        }
    }

    private fun closeOf(day: LocalDate, closeGraceMinutes: Int): Instant =
        day.atTime(MARKET_CLOSE).atZone(MARKET_ZONE).toInstant().plusSeconds(closeGraceMinutes * 60L)

    private fun LocalDate.isWeekday(): Boolean = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
}
