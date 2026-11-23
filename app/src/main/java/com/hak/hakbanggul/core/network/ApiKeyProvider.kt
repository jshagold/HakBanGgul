package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.Flow

// 다른 기능이 출처의 API 키를 얻는 유일한 통로. 키를 어디서 얻는지는 구현만 안다.
interface ApiKeyProvider {
    suspend fun getKey(source: DataSource): ApiKeyResult

    fun observeAvailability(source: DataSource): Flow<ApiKeyAvailability>

    // 출처가 이 키를 거절했다고 알린다.
    suspend fun reportRejected(source: DataSource, rejectedKey: String)
}
