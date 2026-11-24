package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiKeyInterceptorTest {

    private val server = MockWebServer()
    private val provider = FakeApiKeyProvider()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun send(source: DataSource): Response {
        val client = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(source, provider, SourceResponseClassifier()))
            .build()
        return client.newCall(Request.Builder().url(server.url("/path?x=1")).build()).execute()
    }

    private fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private fun assertKeyUnavailable(source: DataSource, expected: ApiKeyAvailability) {
        try {
            send(source)
            fail("ApiKeyUnavailableException이 나야 한다")
        } catch (e: ApiKeyUnavailableException) {
            assertEquals(source, e.source)
            assertEquals(expected, e.availability)
        }
        assertEquals(0, server.requestCount)
    }

    // R-51 키를 붙임, 키가 없으면 보내지 않음

    @Test
    fun `R-51 Twelve Data는 Authorization 헤더에 키를 붙인다`() {
        provider.keys[TWELVE_DATA] = "tdkey"
        enqueue(200, """{"status":"ok"}""")

        send(TWELVE_DATA).close()

        assertEquals("apikey tdkey", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `R-51 FRED는 api_key 쿼리에 키를 붙인다`() {
        provider.keys[FRED] = "fredkey"
        enqueue(200, "{}")

        send(FRED).close()

        assertEquals("fredkey", server.takeRequest().url.queryParameter("api_key"))
    }

    @Test
    fun `R-51 키가 Missing이면 MISSING 예외가 나고 요청은 나가지 않는다`() {
        provider.set(FRED, ApiKeyAvailability.MISSING)
        assertKeyUnavailable(FRED, ApiKeyAvailability.MISSING)
    }

    @Test
    fun `R-51 키가 Invalid이면 INVALID 예외가 나고 요청은 나가지 않는다`() {
        provider.set(TWELVE_DATA, ApiKeyAvailability.INVALID)
        assertKeyUnavailable(TWELVE_DATA, ApiKeyAvailability.INVALID)
    }

    // R-50 거절된 키와 함께 알림, 그 밖은 알리지 않음

    @Test
    fun `R-50 Twelve Data 본문 code 401이면 거절을 알리고 본문은 그대로 읽힌다`() {
        provider.keys[TWELVE_DATA] = "tdkey"
        val body = """{"code":401,"message":"**apikey** parameter is incorrect or not specified.","status":"error"}"""
        enqueue(200, body)

        val read = send(TWELVE_DATA).use { it.body.string() }

        assertEquals(listOf(TWELVE_DATA to "tdkey"), provider.rejectedReports)
        assertEquals(body, read)
    }

    @Test
    fun `R-50 FRED HTTP 400 api_key 미등록이면 거절을 알린다`() {
        provider.keys[FRED] = "fredkey"
        enqueue(400, """{"error_code":400,"error_message":"Bad Request.  The value for variable api_key is not registered."}""")

        send(FRED).close()

        assertEquals(listOf(FRED to "fredkey"), provider.rejectedReports)
    }

    @Test
    fun `R-50 Twelve Data 한도 넘음은 알리지 않는다`() {
        enqueue(200, """{"code":429,"message":"You have run out of API credits for the current minute.","status":"error"}""")

        send(TWELVE_DATA).close()

        assertTrue(provider.rejectedReports.isEmpty())
    }

    @Test
    fun `R-50 FRED HTTP 500은 알리지 않는다`() {
        enqueue(500)

        send(FRED).close()

        assertTrue(provider.rejectedReports.isEmpty())
    }

    // R-44 거절을 설정에 알림

    @Test
    fun `R-44 Twelve Data HTTP 401이면 거절을 알린다`() {
        provider.keys[TWELVE_DATA] = "tdkey"
        enqueue(401)

        send(TWELVE_DATA).close()

        assertEquals(listOf(TWELVE_DATA to "tdkey"), provider.rejectedReports)
    }
}
