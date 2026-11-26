package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

// 규칙과 받아 둔 값을 계산기에 넣어 판정을 내준다. 낼 수 없으면 null이다.
// 받아 둔 값이 바뀌면 다시 계산해 내보낸다.
// 다른 기능에 내놓는 것은 ObserveMoodUseCase, RequestRefreshUseCase, ObserveRefreshStateUseCase와 그 모델뿐이다.
// MarketDataRefresher, RefreshMarketDataUseCase, MarketDataRepository는 다른 기능이 직접 쓰지 않는다.
class ObserveMoodUseCase @Inject constructor(
    private val rulesRepository: MoodRulesRepository,
    private val marketDataRepository: MarketDataRepository,
    private val calculator: MoodCalculator,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    // 계산이 무거워(매매 판단은 몇 년치 창을 날마다 다시 센다) 받는 쪽이 어느 스레드에서 모으든 계산용 디스패처에서 돌린다.
    operator fun invoke(): Flow<MoodAssessment?> =
        combine(rulesRepository.observe(), marketDataRepository.observeStored()) { rules, stored ->
            calculator.assess(rules, stored.values)
        }.flowOn(dispatcher)
}
