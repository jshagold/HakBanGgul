package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// 출처마다의 키와 쓸 수 있음 여부를 테스트가 정하는 가짜. 다른 기능의 테스트도 쓴다.
class FakeApiKeyProvider(
    initial: Map<DataSource, ApiKeyAvailability> = DataSource.entries.associateWith { ApiKeyAvailability.AVAILABLE },
) : ApiKeyProvider {
    val availability = MutableStateFlow(initial)
    val keys = mutableMapOf<DataSource, String>()
    val rejectedReports = mutableListOf<Pair<DataSource, String>>()

    fun set(source: DataSource, value: ApiKeyAvailability) {
        availability.value = availability.value + (source to value)
    }

    override suspend fun getKey(source: DataSource): ApiKeyResult =
        when (availability.value[source] ?: ApiKeyAvailability.MISSING) {
            ApiKeyAvailability.AVAILABLE -> ApiKeyResult.Available(keys[source] ?: "key-$source")
            ApiKeyAvailability.MISSING -> ApiKeyResult.Missing
            ApiKeyAvailability.INVALID -> ApiKeyResult.Invalid
        }

    override fun observeAvailability(source: DataSource): Flow<ApiKeyAvailability> =
        availability.map { it[source] ?: ApiKeyAvailability.MISSING }.distinctUntilChanged()

    override suspend fun reportRejected(source: DataSource, rejectedKey: String) {
        rejectedReports += source to rejectedKey
    }
}
