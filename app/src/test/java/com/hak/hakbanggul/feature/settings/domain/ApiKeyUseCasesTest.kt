package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import com.hak.hakbanggul.core.network.ApiKeyAvailability.AVAILABLE
import com.hak.hakbanggul.core.network.ApiKeyAvailability.INVALID
import com.hak.hakbanggul.core.network.ApiKeyAvailability.MISSING
import com.hak.hakbanggul.core.network.ApiKeyResult
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ApiKeyUseCasesTest {

    private val keyRepository = FakeApiKeyRepository()
    private val delete = DeleteApiKeyUseCase(keyRepository)
    private val getKey = GetApiKeyUseCase(keyRepository)
    private val observeAvailability = ObserveKeyAvailabilityUseCase(keyRepository)
    private val reportRejected = ReportKeyRejectedUseCase(keyRepository)

    // 받는 중에 일어난 일을 모은다.
    private fun TestScope.collectAvailability(source: DataSource): MutableList<ApiKeyAvailability> {
        val received = mutableListOf<ApiKeyAvailability>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeAvailability(source).toList(received)
        }
        return received
    }

    @Test
    fun `R-16 한 출처를 지워도 다른 출처는 그대로 SAVED다`() = runTest {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "td"), saved(FRED, "fred"))

        delete(FRED)

        assertEquals(ApiKeyStatus.SAVED, keyRepository.keys.value[TWELVE_DATA]?.status)
    }

    @Test
    fun `R-34 지운 출처는 observeKeys에 없다`() = runTest {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "td"))

        delete(TWELVE_DATA)

        assertFalse(ObserveApiKeysUseCase(keyRepository)().first().containsKey(TWELVE_DATA))
    }

    @Test
    fun `R-23 SAVED 키는 Available로 내준다`() = runTest {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "goodkey"))

        assertEquals(ApiKeyResult.Available("goodkey"), getKey(TWELVE_DATA))
    }

    @Test
    fun `R-41 저장된 키가 없으면 Missing이다`() = runTest {
        assertEquals(ApiKeyResult.Missing, getKey(FRED))
    }

    @Test
    fun `R-41 INVALID 키는 내주지 않고 Invalid다`() = runTest {
        keyRepository.keys.value = mapOf(invalid(FRED, "fredkey"))

        assertEquals(ApiKeyResult.Invalid, getKey(FRED))
    }

    @Test
    fun `R-24 같은 키가 거절되면 상태만 INVALID가 된다`() = runTest {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "k1"))

        reportRejected(TWELVE_DATA, "k1")

        assertEquals(StoredApiKey(TWELVE_DATA, "k1", ApiKeyStatus.INVALID), keyRepository.keys.value[TWELVE_DATA])
    }

    @Test
    fun `R-39 다른 키가 거절되면 상태는 SAVED 그대로다`() = runTest {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "k2"))

        reportRejected(TWELVE_DATA, "k1")

        assertEquals(ApiKeyStatus.SAVED, keyRepository.keys.value[TWELVE_DATA]?.status)
    }

    @Test
    fun `R-39 저장된 키가 없으면 거절이 알려져도 여전히 없다`() = runTest {
        reportRejected(TWELVE_DATA, "k1")

        assertNull(keyRepository.keys.value[TWELVE_DATA])
    }

    @Test
    fun `R-25 저장하고 지우면 MISSING, AVAILABLE, MISSING 순서로 받는다`() = runTest {
        val received = collectAvailability(TWELVE_DATA)

        keyRepository.save(TWELVE_DATA, "key")
        delete(TWELVE_DATA)

        assertEquals(listOf(MISSING, AVAILABLE, MISSING), received)
    }

    @Test
    fun `R-25 같은 키로 거절이 알려지면 AVAILABLE 다음에 INVALID를 받는다`() = runTest {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "k1"))
        val received = collectAvailability(TWELVE_DATA)

        reportRejected(TWELVE_DATA, "k1")

        assertEquals(listOf(AVAILABLE, INVALID), received)
    }

    @Test
    fun `R-25 다른 출처의 키를 저장해도 새 값이 오지 않는다`() = runTest {
        val received = collectAvailability(TWELVE_DATA)

        keyRepository.save(FRED, "fredkey")

        assertEquals(listOf(MISSING), received)
    }
}
