package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow

// 출처마다의 키를 기기에 저장하고 꺼낸다.
interface ApiKeyRepository {
    // 저장된 키가 있는 출처만 담는다.
    fun observeKeys(): Flow<Map<DataSource, StoredApiKey>>

    // 상태 SAVED로 저장한다. 저장하지 못하면 IOException을 던진다.
    suspend fun save(source: DataSource, key: String)

    suspend fun delete(source: DataSource)

    // 저장된 키가 없으면 아무것도 하지 않는다.
    suspend fun updateStatus(source: DataSource, status: ApiKeyStatus)
}
