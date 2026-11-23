package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveApiKeysUseCase @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
) {
    operator fun invoke(): Flow<Map<DataSource, StoredApiKey>> = apiKeyRepository.observeKeys()
}
