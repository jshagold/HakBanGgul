package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// 거절된 키가 지금 저장된 키와 같을 때만 틀림으로 바꾼다. 그사이 새 키로 바뀌었으면 그대로 둔다.
class ReportKeyRejectedUseCase @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
) {
    suspend operator fun invoke(source: DataSource, rejectedKey: String) {
        val stored = apiKeyRepository.observeKeys().first()[source] ?: return
        if (stored.key == rejectedKey) {
            apiKeyRepository.updateStatus(source, ApiKeyStatus.INVALID)
        }
    }
}
