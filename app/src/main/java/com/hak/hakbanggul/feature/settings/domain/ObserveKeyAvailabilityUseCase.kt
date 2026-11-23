package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// 출처 하나의 키를 쓸 수 있는지 여부를 값이 바뀔 때만 내보낸다.
class ObserveKeyAvailabilityUseCase @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
) {
    operator fun invoke(source: DataSource): Flow<ApiKeyAvailability> =
        apiKeyRepository.observeKeys()
            .map { keys ->
                when (keys[source]?.status) {
                    null -> ApiKeyAvailability.MISSING
                    ApiKeyStatus.SAVED -> ApiKeyAvailability.AVAILABLE
                    ApiKeyStatus.INVALID -> ApiKeyAvailability.INVALID
                }
            }
            .distinctUntilChanged()
}
