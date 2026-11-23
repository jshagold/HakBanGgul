package com.hak.hakbanggul.core.network

// 키 얻기의 결과
sealed interface ApiKeyResult {
    data class Available(val key: String) : ApiKeyResult
    data object Missing : ApiKeyResult
    data object Invalid : ApiKeyResult
}
