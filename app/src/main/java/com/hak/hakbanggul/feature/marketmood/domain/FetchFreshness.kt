package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DailyValue
import com.hak.hakbanggul.core.model.DataSource
import java.time.Instant
import javax.inject.Inject

// 받을지 가르는 곳. 받기(RefreshMarketDataUseCase)와 받기 관리자(MarketDataRefresher)가 같은 것을 쓴다.
class FetchFreshness @Inject constructor() {

    // 규칙의 대상(targets() 순서) 가운데 최신이 아닌 대상. 대상의 종가 시리즈 ID로 본다.
    // 최신: 받아 둔 종가가 한 날이라도 있고, 받은 때가 있고, 받은 때가 now보다 뒤가 아니고, 받은 때가 가장 최근 장 마감 이후다.
    // 받은 종가의 마지막 날짜는 보지 않는다.
    fun staleTargets(
        rules: MoodRules,
        values: Map<String, List<DailyValue>>,
        // 용어: 마지막으로 받은 때
        fetchedAt: Map<String, Instant>,
        now: Instant,
    ): List<MarketTarget> {
        val latestClose = MarketCloseTimes.latestClose(now, rules.closeGraceMinutes)
        return rules.targets().filterNot { target ->
            val id = target.closeSeries.seriesId
            val at = fetchedAt[id]
            // 용어: 최신
            val fresh = !values[id].isNullOrEmpty() && at != null && !at.isAfter(now) && !at.isBefore(latestClose)
            fresh
        }
    }

    // 규칙의 FRED 시리즈마다의 받은 때 가운데 가장 이른 때. 하나라도 없으면 null이다.
    fun fredFetchedAt(rules: MoodRules, fetchedAt: Map<String, Instant>): Instant? {
        val ids = rules.fetchYearsBySeries().keys.filter { it.source == DataSource.FRED }.map { it.seriesId }
        val times = ids.map { fetchedAt[it] ?: return null }
        return times.minOrNull()
    }
}
