package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.core.network.SourceResponseKind.ACCEPTED
import com.hak.hakbanggul.core.network.SourceResponseKind.KEY_REJECTED
import com.hak.hakbanggul.core.network.SourceResponseKind.OTHER_FAILURE
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceResponseClassifierTest {

    private val classifier = SourceResponseClassifier()

    // R-18 거절 판정

    @Test
    fun `R-18 Twelve Data HTTP 401은 KEY_REJECTED`() {
        assertEquals(KEY_REJECTED, classifier.classify(TWELVE_DATA, 401, null))
    }

    @Test
    fun `R-18 Twelve Data HTTP 200에 본문 code 401 오류는 KEY_REJECTED`() {
        val body = """{"code":401,"message":"**apikey** parameter is incorrect or not specified.","status":"error"}"""
        assertEquals(KEY_REJECTED, classifier.classify(TWELVE_DATA, 200, body))
    }

    @Test
    fun `R-18 FRED HTTP 400에 api_key 미등록 본문은 KEY_REJECTED`() {
        val body = """{"error_code":400,"error_message":"Bad Request.  The value for variable api_key is not registered."}"""
        assertEquals(KEY_REJECTED, classifier.classify(FRED, 400, body))
    }

    @Test
    fun `R-18 FRED HTTP 400에 api_key 형식 오류 본문은 KEY_REJECTED`() {
        val body = """{"error_code":400,"error_message":"Bad Request.  The value for variable api_key is not a 32 character alpha-numeric lower-case string."}"""
        assertEquals(KEY_REJECTED, classifier.classify(FRED, 400, body))
    }

    @Test
    fun `R-18 FRED HTTP 401은 KEY_REJECTED`() {
        assertEquals(KEY_REJECTED, classifier.classify(FRED, 401, null))
    }

    @Test
    fun `R-18 Twelve Data HTTP 200 정상 본문은 ACCEPTED`() {
        val body = """{"timestamp":"2026-11-23 10:00:00","current_usage":1,"plan_limit":8}"""
        assertEquals(ACCEPTED, classifier.classify(TWELVE_DATA, 200, body))
    }

    @Test
    fun `R-18 FRED HTTP 200 정상 본문은 ACCEPTED`() {
        val body = """{"seriess":[{"id":"DGS10"}]}"""
        assertEquals(ACCEPTED, classifier.classify(FRED, 200, body))
    }

    // R-30 그 밖의 실패 판정

    @Test
    fun `R-30 Twelve Data HTTP 429는 OTHER_FAILURE`() {
        assertEquals(OTHER_FAILURE, classifier.classify(TWELVE_DATA, 429, null))
    }

    @Test
    fun `R-30 Twelve Data HTTP 200에 본문 code 429 오류는 OTHER_FAILURE`() {
        val body = """{"code":429,"message":"You have run out of API credits for the current minute.","status":"error"}"""
        assertEquals(OTHER_FAILURE, classifier.classify(TWELVE_DATA, 200, body))
    }

    @Test
    fun `R-30 Twelve Data HTTP 403은 OTHER_FAILURE`() {
        assertEquals(OTHER_FAILURE, classifier.classify(TWELVE_DATA, 403, null))
    }

    @Test
    fun `R-30 FRED HTTP 400에 api_key가 없는 본문은 OTHER_FAILURE`() {
        val body = """{"error_code":400,"error_message":"Bad Request.  The series does not exist."}"""
        assertEquals(OTHER_FAILURE, classifier.classify(FRED, 400, body))
    }

    @Test
    fun `R-30 FRED HTTP 500 본문 없음은 OTHER_FAILURE`() {
        assertEquals(OTHER_FAILURE, classifier.classify(FRED, 500, null))
    }

    // R-31 한도 넘음·서버 오류는 거절이 아님

    @Test
    fun `R-31 FRED HTTP 429는 OTHER_FAILURE`() {
        assertEquals(OTHER_FAILURE, classifier.classify(FRED, 429, null))
    }

    @Test
    fun `R-31 Twelve Data HTTP 500에 JSON이 아닌 본문은 OTHER_FAILURE`() {
        assertEquals(OTHER_FAILURE, classifier.classify(TWELVE_DATA, 500, "<html>Internal Server Error</html>"))
    }
}
