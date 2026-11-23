package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource
import javax.inject.Inject

class DeleteApiKeyUseCase @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
) {
    suspend operator fun invoke(source: DataSource) {
        apiKeyRepository.delete(source)
    }
}
