package com.hak.hakbanggul.feature.settings.data

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import com.hak.hakbanggul.core.network.ApiKeyProvider
import com.hak.hakbanggul.core.network.ApiKeyResult
import com.hak.hakbanggul.feature.settings.domain.GetApiKeyUseCase
import com.hak.hakbanggul.feature.settings.domain.ObserveKeyAvailabilityUseCase
import com.hak.hakbanggul.feature.settings.domain.ReportKeyRejectedUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// 기기에 저장된 키를 다른 기능에 내준다. 판정은 설정의 UseCase가 한다.
@Singleton
class ApiKeyProviderImpl @Inject constructor(
    private val getApiKey: GetApiKeyUseCase,
    private val observeKeyAvailability: ObserveKeyAvailabilityUseCase,
    private val reportKeyRejected: ReportKeyRejectedUseCase,
) : ApiKeyProvider {

    override suspend fun getKey(source: DataSource): ApiKeyResult = getApiKey(source)

    override fun observeAvailability(source: DataSource): Flow<ApiKeyAvailability> = observeKeyAvailability(source)

    override suspend fun reportRejected(source: DataSource, rejectedKey: String) {
        reportKeyRejected(source, rejectedKey)
    }
}
