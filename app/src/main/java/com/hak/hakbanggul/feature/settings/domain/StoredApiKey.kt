package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource

// 기기에 저장된 출처 하나의 키
data class StoredApiKey(
    val source: DataSource,
    val key: String,
    val status: ApiKeyStatus,
)
