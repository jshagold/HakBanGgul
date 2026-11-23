package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource

// 출처에 시험 요청을 보내 키를 받아들이는지 확인한다.
interface ApiKeyCheckRepository {
    suspend fun check(source: DataSource, key: String): KeyCheckResult
}
