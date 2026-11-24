package com.hak.hakbanggul.core.network

import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

// 출처에 보내는 요청 하나를 돌려 결과로 바꾼다. 응답과 예외를 오류 종류로 가르는 곳은 여기 한 곳이다.
class SourceCallExecutor(
    private val classifier: SourceResponseClassifier,
) {

    suspend fun execute(source: DataSource, call: suspend () -> Response<ResponseBody>): SourceCallResult = try {
        withContext(Dispatchers.IO) {
            val response = call()
            val body = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
            classify(source, response.code(), body)
        }
    } catch (e: CancellationException) {
        // 코루틴 취소는 삼키지 않는다. 받기의 시간 제한이 동작해야 한다.
        throw e
    } catch (e: ApiKeyUnavailableException) {
        when (e.availability) {
            ApiKeyAvailability.INVALID -> SourceCallResult.Failure(DataFetchError.KEY_INVALID)
            else -> SourceCallResult.Failure(DataFetchError.KEY_MISSING)
        }
    } catch (e: IOException) {
        SourceCallResult.Failure(DataFetchError.NO_CONNECTION)
    } catch (e: Exception) {
        SourceCallResult.Failure(DataFetchError.OTHER)
    }

    private fun classify(source: DataSource, httpStatus: Int, body: String?): SourceCallResult =
        when (classifier.classify(source, httpStatus, body)) {
            SourceResponseKind.ACCEPTED -> SourceCallResult.Success(body.orEmpty())
            SourceResponseKind.KEY_REJECTED -> SourceCallResult.Failure(DataFetchError.KEY_REJECTED)
            SourceResponseKind.OTHER_FAILURE ->
                if (classifier.isRateLimited(source, httpStatus, body)) {
                    SourceCallResult.Failure(DataFetchError.RATE_LIMITED)
                } else {
                    SourceCallResult.Failure(DataFetchError.OTHER)
                }
        }
}
