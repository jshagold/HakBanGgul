package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

// 출처 하나를 맡아 요청에 키를 붙인다. 키를 쓸 수 없으면 요청을 보내지 않는다.
// 출처가 키를 거절하면 ApiKeyProvider에 알린다. 기능 코드는 키를 읽거나 넘겨받지 않는다.
class ApiKeyInterceptor(
    private val source: DataSource,
    private val apiKeyProvider: ApiKeyProvider,
    private val classifier: SourceResponseClassifier,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // getKey와 reportRejected는 suspend 함수라 OkHttp의 요청 스레드에서 막는 호출로 부른다.
        val key = when (val result = runBlocking { apiKeyProvider.getKey(source) }) {
            is ApiKeyResult.Available -> result.key
            ApiKeyResult.Missing -> throw ApiKeyUnavailableException(source, ApiKeyAvailability.MISSING)
            ApiKeyResult.Invalid -> throw ApiKeyUnavailableException(source, ApiKeyAvailability.INVALID)
        }

        val response = chain.proceed(chain.request().withKey(key))

        // 본문은 peekBody로 읽어, 부른 쪽이 응답 본문을 그대로 다시 읽을 수 있게 한다.
        val body = response.peekBody(MAX_PEEK_BYTES).string()
        if (classifier.classify(source, response.code, body) == SourceResponseKind.KEY_REJECTED) {
            runBlocking { apiKeyProvider.reportRejected(source, key) }
        }
        return response
    }

    private fun Request.withKey(key: String): Request = when (source) {
        DataSource.TWELVE_DATA -> newBuilder().header("Authorization", "apikey $key").build()
        DataSource.FRED -> newBuilder().url(url.newBuilder().addQueryParameter("api_key", key).build()).build()
    }

    private companion object {
        // 키 거절 판정에는 본문 앞부분이면 충분하다. 2년치 시세 응답 전체를 두 번 담지 않게 한다.
        const val MAX_PEEK_BYTES = 64L * 1024
    }
}
