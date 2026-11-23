package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject

// 키를 출처에 확인하고, 받아들여졌을 때만 저장한다.
class SaveApiKeyUseCase @Inject constructor(
    private val apiKeyCheckRepository: ApiKeyCheckRepository,
    private val apiKeyRepository: ApiKeyRepository,
) {
    suspend operator fun invoke(source: DataSource, rawKey: String): SaveKeyOutcome {
        val key = rawKey.trim()
        // 10초 안에 답이 없으면 연결이 안 된 것으로 다룬다.
        val result = withTimeoutOrNull(CHECK_TIMEOUT_MILLIS) {
            apiKeyCheckRepository.check(source, key)
        } ?: KeyCheckResult.NO_CONNECTION

        return when (result) {
            KeyCheckResult.ACCEPTED -> try {
                apiKeyRepository.save(source, key)
                SaveKeyOutcome.SAVED
            } catch (e: IOException) {
                SaveKeyOutcome.STORE_FAILED
            }
            KeyCheckResult.REJECTED -> SaveKeyOutcome.REJECTED
            KeyCheckResult.NO_CONNECTION -> SaveKeyOutcome.NO_CONNECTION
            KeyCheckResult.UNAVAILABLE -> SaveKeyOutcome.UNAVAILABLE
        }
    }

    companion object {
        const val CHECK_TIMEOUT_MILLIS = 10_000L
    }
}
