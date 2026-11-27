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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

// 규칙과 받아 둔 값으로 한 벌을 내준다. 시장 분위기 안에서만 쓴다.
// 계산이 무거워(대상 열넷, 기간마다 창을 날마다 다시 센다) 앱 범위에서 한 번만 계산해 받는 쪽 모두에 나눠 준다.
// 이 프로세스에서 한 벌을 아직 한 번도 내지 않았으면 계산을 기다리지 않고 계산 중을 먼저 낸다.
@Singleton
class ObserveMarketOverviewUseCase @Inject constructor(
    private val rulesRepository: MoodRulesRepository,
    marketDataRepository: MarketDataRepository,
    private val calculator: MarketOverviewCalculator,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) {
    // 이 프로세스에서 Ready를 한 번이라도 냈는지. 앱 전체에 하나인 이 UseCase 안에 메모리로만 둔다.
    @Volatile
    private var readyEmitted = false

    // 시리즈의 값만 본다. 받은 때만 바뀐 것이면 다시 계산하지 않는다.
    // 계산하는 동안 새 값이 여러 번 오면 쌓아 두지 않고 마지막 것만 계산한다(conflate).
    // 받는 쪽이 없어지고 5초가 지나면 멈추고, 마지막 값 하나를 들고 있어 새로 받기 시작한 쪽은 그 값을 바로 받는다.
    private val overview: Flow<MarketOverviewState.Ready> =
        combine(
            rulesRepository.observe(),
            marketDataRepository.observeStored().map { it.values }.distinctUntilChanged(),
        ) { rules, values -> rules to values }
            .distinctUntilChanged()
            .conflate()
            .map { (rules, values) -> MarketOverviewState.Ready(calculator.assess(rules, values)).also { readyEmitted = true } }
            .flowOn(dispatcher)
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    // 아직 Ready를 내지 않았으면 규칙을 받는 대로 Calculating을 먼저 낸다(받아 둔 값을 읽거나 계산을 기다리지 않는다).
    // 한 번 Ready를 낸 뒤에는 Calculating을 내지 않는다(다시 계산하는 동안에도, 멈췄다가 다시 시작해도).
    operator fun invoke(): Flow<MarketOverviewState> = flow {
        if (!readyEmitted) emit(MarketOverviewState.Calculating(rulesRepository.observe().first()))
        emitAll(overview)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
