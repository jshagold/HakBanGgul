package com.hak.hakbanggul.feature.settings.data

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.SourceResponseClassifier
import com.hak.hakbanggul.core.network.SourceResponseKind
import com.hak.hakbanggul.feature.settings.domain.ApiKeyCheckRepository
import com.hak.hakbanggul.feature.settings.domain.KeyCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// 아직 저장되지 않은 키를 출처에 직접 보내 확인한다. 그래서 ApiKeyProvider를 거치지 않는다.
@Singleton
class ApiKeyCheckRepositoryImpl @Inject constructor(
    private val twelveDataApi: TwelveDataKeyCheckApi,
    private val fredApi: FredKeyCheckApi,
    private val classifier: SourceResponseClassifier,
) : ApiKeyCheckRepository {

    override suspend fun check(source: DataSource, key: String): KeyCheckResult = try {
        withContext(Dispatchers.IO) {
            val response = when (source) {
                DataSource.TWELVE_DATA -> twelveDataApi.apiUsage("apikey $key")
                DataSource.FRED -> fredApi.series(FRED_CHECK_SERIES, FRED_FILE_TYPE, key)
            }
            val body = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
            when (classifier.classify(source, response.code(), body)) {
                SourceResponseKind.ACCEPTED -> KeyCheckResult.ACCEPTED
                SourceResponseKind.KEY_REJECTED -> KeyCheckResult.REJECTED
                SourceResponseKind.OTHER_FAILURE -> KeyCheckResult.UNAVAILABLE
            }
        }
    } catch (e: CancellationException) {
        // 시간 제한이 동작하도록 취소는 그대로 넘긴다.
        throw e
    } catch (e: IOException) {
        // 취소 때문에 끊긴 요청이 IOException으로 오면 취소로 넘긴다.
        currentCoroutineContext().ensureActive()
        KeyCheckResult.NO_CONNECTION
    } catch (e: Exception) {
        KeyCheckResult.UNAVAILABLE
    }

    private companion object {
        const val FRED_CHECK_SERIES = "DGS10"
        const val FRED_FILE_TYPE = "json"
    }
}
