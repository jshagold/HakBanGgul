package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// 다른 기능에 줄 키를 고른다. 틀림인 키는 내주지 않는다.
class GetApiKeyUseCase @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
) {
    suspend operator fun invoke(source: DataSource): ApiKeyResult {
        val stored = apiKeyRepository.observeKeys().first()[source] ?: return ApiKeyResult.Missing
        return when (stored.status) {
            ApiKeyStatus.SAVED -> ApiKeyResult.Available(stored.key)
            ApiKeyStatus.INVALID -> ApiKeyResult.Invalid
        }
    }
}
