package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataFetchError

// 출처에 보낸 요청 하나의 결과
sealed interface SourceCallResult {
    data class Success(val body: String) : SourceCallResult
    data class Failure(val error: DataFetchError) : SourceCallResult
}
