package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SourceCallExecutorTest {

    private val classifier = SourceResponseClassifier()
    private val executor = SourceCallExecutor(classifier)

    private val rateLimitBody = """{"code":429,"message":"You have run out of API credits for the current minute.","status":"error"}"""
    private val notFoundBody = """{"code":400,"message":"symbol not found","status":"error"}"""

    private fun body(text: String): ResponseBody = text.toResponseBody("application/json".toMediaType())

    private fun response(code: Int, text: String = ""): Response<ResponseBody> =
        if (code in 200..299) Response.success(code, body(text)) else Response.error(code, body(text))

    private suspend fun execute(source: DataSource, code: Int, text: String = ""): SourceCallResult =
        executor.execute(source) { response(code, text) }

    private suspend fun executeThrowing(source: DataSource, error: Throwable): SourceCallResult =
        executor.execute(source) { throw error }

    private fun failure(error: DataFetchError) = SourceCallResult.Failure(error)

    // R-42 응답과 예외를 오류 종류로

    @Test
    fun `R-42 Twelve Data 정상 본문은 그 본문이 든 Success`() = runTest {
        val text = """{"meta":{"symbol":"SPY"},"values":[],"status":"ok"}"""
        assertEquals(SourceCallResult.Success(text), execute(TWELVE_DATA, 200, text))
    }

    @Test
    fun `R-42 HTTP 429는 두 출처 모두 한도 넘음`() {
        assertTrue(classifier.isRateLimited(TWELVE_DATA, 429, null))
        assertTrue(classifier.isRateLimited(FRED, 429, null))
    }

    @Test
    fun `R-42 Twelve Data HTTP 200 본문 code 429는 한도 넘음이고 RATE_LIMITED`() = runTest {
        assertTrue(classifier.isRateLimited(TWELVE_DATA, 200, rateLimitBody))
        assertEquals(failure(DataFetchError.RATE_LIMITED), execute(TWELVE_DATA, 200, rateLimitBody))
    }

    @Test
    fun `R-42 Twelve Data HTTP 200 본문 code 400은 한도 넘음이 아니고 OTHER`() = runTest {
        assertFalse(classifier.isRateLimited(TWELVE_DATA, 200, notFoundBody))
        assertEquals(failure(DataFetchError.OTHER), execute(TWELVE_DATA, 200, notFoundBody))
    }

    @Test
    fun `R-42 FRED HTTP 429는 RATE_LIMITED`() = runTest {
        assertEquals(failure(DataFetchError.RATE_LIMITED), execute(FRED, 429))
    }

    @Test
    fun `R-42 Twelve Data HTTP 401은 KEY_REJECTED`() = runTest {
        assertEquals(failure(DataFetchError.KEY_REJECTED), execute(TWELVE_DATA, 401))
    }

    @Test
    fun `R-42 FRED HTTP 500은 OTHER`() = runTest {
        assertEquals(failure(DataFetchError.OTHER), execute(FRED, 500))
    }

    @Test
    fun `R-42 키 없음 예외는 KEY_MISSING, 틀림 예외는 KEY_INVALID`() = runTest {
        assertEquals(
            failure(DataFetchError.KEY_MISSING),
            executeThrowing(FRED, ApiKeyUnavailableException(FRED, ApiKeyAvailability.MISSING)),
        )
        assertEquals(
            failure(DataFetchError.KEY_INVALID),
            executeThrowing(FRED, ApiKeyUnavailableException(FRED, ApiKeyAvailability.INVALID)),
        )
    }

    @Test
    fun `R-42 UnknownHostException과 SocketTimeoutException은 NO_CONNECTION`() = runTest {
        assertEquals(failure(DataFetchError.NO_CONNECTION), executeThrowing(TWELVE_DATA, UnknownHostException()))
        assertEquals(failure(DataFetchError.NO_CONNECTION), executeThrowing(TWELVE_DATA, SocketTimeoutException()))
    }

    @Test
    fun `R-42 IllegalStateException은 OTHER`() = runTest {
        assertEquals(failure(DataFetchError.OTHER), executeThrowing(TWELVE_DATA, IllegalStateException()))
    }

    @Test
    fun `R-42 요청이 끝나기 전에 코루틴이 취소되면 결과 없이 취소가 넘어간다`() = runTest {
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Response<ResponseBody>>()
        var returned: SourceCallResult? = null
        var cancellationSeen = false
        val job = launch {
            try {
                returned = executor.execute(TWELVE_DATA) {
                    started.complete(Unit)
                    never.await()
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                cancellationSeen = true
                throw e
            }
        }
        started.await()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(cancellationSeen)
        assertNull(returned)
    }
}
