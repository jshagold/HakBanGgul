package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException

// 메모리에 키를 두는 가짜 저장소
class FakeApiKeyRepository(
    initial: Map<DataSource, StoredApiKey> = emptyMap(),
) : ApiKeyRepository {
    val keys = MutableStateFlow(initial)
    val saveCalls = mutableListOf<Pair<DataSource, String>>()
    var failOnSave = false

    override fun observeKeys(): Flow<Map<DataSource, StoredApiKey>> = keys

    override suspend fun save(source: DataSource, key: String) {
        saveCalls += source to key
        if (failOnSave) throw IOException("저장 실패")
        keys.update { it + (source to StoredApiKey(source, key, ApiKeyStatus.SAVED)) }
    }

    override suspend fun delete(source: DataSource) {
        keys.update { it - source }
    }

    override suspend fun updateStatus(source: DataSource, status: ApiKeyStatus) {
        keys.update { current ->
            val stored = current[source] ?: return@update current
            current + (source to stored.copy(status = status))
        }
    }
}

// 시험 요청의 답을 테스트가 정하는 가짜. answer 안에서 delay나 대기를 걸 수 있다.
class FakeApiKeyCheckRepository(
    var answer: suspend (DataSource, String) -> KeyCheckResult = { _, _ -> KeyCheckResult.ACCEPTED },
) : ApiKeyCheckRepository {
    val calls = mutableListOf<Pair<DataSource, String>>()

    override suspend fun check(source: DataSource, key: String): KeyCheckResult {
        calls += source to key
        return answer(source, key)
    }
}

fun saved(source: DataSource, key: String) = source to StoredApiKey(source, key, ApiKeyStatus.SAVED)

fun invalid(source: DataSource, key: String) = source to StoredApiKey(source, key, ApiKeyStatus.INVALID)
