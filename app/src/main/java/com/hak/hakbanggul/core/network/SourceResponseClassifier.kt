package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

// 출처 응답이 받아들임 / 키 거절 / 그 밖의 실패 중 무엇인지 가른다. 이 판정은 이곳 한 곳에만 둔다.
class SourceResponseClassifier {

    fun classify(source: DataSource, httpStatus: Int, body: String?): SourceResponseKind {
        val json = parseObject(body)
        return when (source) {
            DataSource.TWELVE_DATA -> classifyTwelveData(httpStatus, json)
            DataSource.FRED -> classifyFred(httpStatus, json)
        }
    }

    // Twelve Data는 오류를 HTTP 200에 본문 code로 돌려주는 경우가 있어 본문도 본다.
    private fun classifyTwelveData(httpStatus: Int, json: JsonObject?): SourceResponseKind {
        val isErrorBody = json.string("status") == "error"
        return when {
            httpStatus == 401 -> SourceResponseKind.KEY_REJECTED
            isErrorBody && json.int("code") == 401 -> SourceResponseKind.KEY_REJECTED
            httpStatus in 200..299 && !isErrorBody -> SourceResponseKind.ACCEPTED
            else -> SourceResponseKind.OTHER_FAILURE
        }
    }

    private fun classifyFred(httpStatus: Int, json: JsonObject?): SourceResponseKind = when {
        httpStatus in 200..299 -> SourceResponseKind.ACCEPTED
        httpStatus == 401 || httpStatus == 403 -> SourceResponseKind.KEY_REJECTED
        httpStatus == 400 && json.string("error_message")?.contains("api_key") == true ->
            SourceResponseKind.KEY_REJECTED
        else -> SourceResponseKind.OTHER_FAILURE
    }

    // 본문을 JSON 객체로 읽지 못하면 null을 돌려준다. 그때는 HTTP 상태만으로 가른다.
    private fun parseObject(body: String?): JsonObject? {
        if (body.isNullOrBlank()) return null
        return try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: IllegalArgumentException) {
            // SerializationException과 jsonObject 변환 실패 모두 IllegalArgumentException이다.
            null
        }
    }

    private fun JsonObject?.string(name: String): String? = (this?.get(name) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject?.int(name: String): Int? = (this?.get(name) as? JsonPrimitive)?.intOrNull
}
