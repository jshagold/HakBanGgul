package com.hak.hakbanggul.feature.marketmood.domain

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import com.hak.hakbanggul.core.network.ApiKeyProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

// 규칙이 쓰는 출처마다 키를 쓸 수 있는지를 묶어 내준다. 출처의 순서는 DataSource의 선언 순서다.
class ObserveSourceKeysUseCase @Inject constructor(
    private val rulesRepository: MoodRulesRepository,
    private val apiKeyProvider: ApiKeyProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Map<DataSource, ApiKeyAvailability>> =
        rulesRepository.observe()
            .flatMapLatest { rules ->
                // 장세의 항목과 매매 판단의 시리즈가 쓰는 출처
                val sources = rules.fetchYearsBySeries().keys
                    .map { it.source }
                    .distinct()
                    .sortedBy { it.ordinal }
                if (sources.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(sources.map { apiKeyProvider.observeAvailability(it) }) { values ->
                        // LinkedHashMap이라 출처의 순서가 그대로 남는다.
                        sources.zip(values.toList()).toMap()
                    }
                }
            }
            .distinctUntilChanged()
}
