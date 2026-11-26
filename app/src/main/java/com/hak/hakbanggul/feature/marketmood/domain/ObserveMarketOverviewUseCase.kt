package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.di.ApplicationScope
import com.hak.hakbanggul.core.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

// 규칙과 받아 둔 값으로 한 벌을 내준다. 판정을 낼 수 없으면 null이다. 시장 분위기 안에서만 쓴다.
// 계산이 무거워(대상 열넷, 기간마다 창을 날마다 다시 센다) 앱 범위에서 한 번만 계산해 받는 쪽 모두에 나눠 준다.
@Singleton
class ObserveMarketOverviewUseCase @Inject constructor(
    rulesRepository: MoodRulesRepository,
    marketDataRepository: MarketDataRepository,
    private val calculator: MarketOverviewCalculator,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) {
    // 시리즈의 값만 본다. 성공한 때만 바뀐 것이면 다시 계산하지 않는다.
    // 계산하는 동안 새 값이 여러 번 오면 쌓아 두지 않고 마지막 것만 계산한다(conflate).
    // 받는 쪽이 없어지고 5초가 지나면 멈추고, 마지막 값 하나를 들고 있어 새로 받기 시작한 쪽은 그 값을 바로 받는다.
    private val overview: Flow<MarketOverview?> =
        combine(
            rulesRepository.observe(),
            marketDataRepository.observeStored().map { it.values }.distinctUntilChanged(),
        ) { rules, values -> rules to values }
            .distinctUntilChanged()
            .conflate()
            .map { (rules, values) -> calculator.assess(rules, values) }
            .flowOn(dispatcher)
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    operator fun invoke(): Flow<MarketOverview?> = overview

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
