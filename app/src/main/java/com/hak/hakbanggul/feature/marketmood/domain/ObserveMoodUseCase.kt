package com.hak.hakbanggul.feature.marketmood.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

// 규칙과 받아 둔 값을 계산기에 넣어 판정을 내준다. 낼 수 없으면 null이다.
// 받아 둔 값이 바뀌면 다시 계산해 내보낸다.
class ObserveMoodUseCase @Inject constructor(
    private val rulesRepository: MoodRulesRepository,
    private val marketDataRepository: MarketDataRepository,
    private val calculator: MoodCalculator,
) {
    operator fun invoke(): Flow<MoodAssessment?> =
        combine(rulesRepository.observe(), marketDataRepository.observeStored()) { rules, stored ->
            calculator.assess(rules, stored.values)
        }
}
