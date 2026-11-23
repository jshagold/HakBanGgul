package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveApiKeyUseCaseTest {

    private val keyRepository = FakeApiKeyRepository()
    private val checkRepository = FakeApiKeyCheckRepository()
    private val useCase = SaveApiKeyUseCase(checkRepository, keyRepository)

    @Test
    fun `R-11 시험 요청은 그 출처에 한 번만 가고 다른 출처의 키는 그대로다`() = runTest {
        val fredKey = "fredkey00000000000000000000000001"
        keyRepository.keys.value = mapOf(saved(FRED, fredKey))

        useCase(TWELVE_DATA, "tdkey000000000000000000000000001")

        assertEquals(listOf(TWELVE_DATA), checkRepository.calls.map { it.first })
        assertEquals(StoredApiKey(FRED, fredKey, ApiKeyStatus.SAVED), keyRepository.keys.value[FRED])
    }

    @Test
    fun `R-12 받아들여지면 SAVED를 돌려주고 키를 SAVED로 저장한다`() = runTest {
        val outcome = useCase(TWELVE_DATA, "goodkey")

        assertEquals(SaveKeyOutcome.SAVED, outcome)
        assertEquals(StoredApiKey(TWELVE_DATA, "goodkey", ApiKeyStatus.SAVED), keyRepository.keys.value[TWELVE_DATA])
    }

    @Test
    fun `R-16 한 출처를 저장해도 다른 출처의 키와 상태는 그대로다`() = runTest {
        keyRepository.keys.value = mapOf(saved(FRED, "fredkey"))

        val outcome = useCase(TWELVE_DATA, "tdkey")

        assertEquals(SaveKeyOutcome.SAVED, outcome)
        assertEquals(StoredApiKey(FRED, "fredkey", ApiKeyStatus.SAVED), keyRepository.keys.value[FRED])
    }

    @Test
    fun `R-29 받아들여지지 않은 세 경우 모두 저장하지 않는다`() = runTest {
        listOf(KeyCheckResult.REJECTED, KeyCheckResult.NO_CONNECTION, KeyCheckResult.UNAVAILABLE).forEach { result ->
            checkRepository.answer = { _, _ -> result }
            useCase(TWELVE_DATA, "somekey")
            assertTrue("$result 에서 save가 불렸다", keyRepository.saveCalls.isEmpty())
        }
    }

    @Test
    fun `R-18 거절되면 REJECTED를 돌려주고 저장하지 않는다`() = runTest {
        checkRepository.answer = { _, _ -> KeyCheckResult.REJECTED }

        val outcome = useCase(TWELVE_DATA, "badkey")

        assertEquals(SaveKeyOutcome.REJECTED, outcome)
        assertFalse(keyRepository.keys.value.containsKey(TWELVE_DATA))
    }

    @Test
    fun `R-40 11초 뒤의 답은 기다리지 않고 10초에 NO_CONNECTION을 돌려준다`() = runTest {
        checkRepository.answer = { _, _ ->
            delay(11_000)
            KeyCheckResult.ACCEPTED
        }

        val outcome = useCase(TWELVE_DATA, "slowkey")

        assertEquals(SaveKeyOutcome.NO_CONNECTION, outcome)
        assertEquals(10_000L, currentTime)
        assertTrue(keyRepository.saveCalls.isEmpty())
    }

    @Test
    fun `R-40 9_5초 뒤의 답은 받아 SAVED를 돌려준다`() = runTest {
        checkRepository.answer = { _, _ ->
            delay(9_500)
            KeyCheckResult.ACCEPTED
        }

        val outcome = useCase(TWELVE_DATA, "slowkey")

        assertEquals(SaveKeyOutcome.SAVED, outcome)
    }

    @Test
    fun `R-19 연결이 안 되면 NO_CONNECTION을 돌려주고 저장하지 않는다`() = runTest {
        checkRepository.answer = { _, _ -> KeyCheckResult.NO_CONNECTION }

        val outcome = useCase(TWELVE_DATA, "key")

        assertEquals(SaveKeyOutcome.NO_CONNECTION, outcome)
        assertTrue(keyRepository.saveCalls.isEmpty())
    }

    @Test
    fun `R-30 확인할 수 없으면 UNAVAILABLE을 돌려주고 저장하지 않는다`() = runTest {
        checkRepository.answer = { _, _ -> KeyCheckResult.UNAVAILABLE }

        val outcome = useCase(TWELVE_DATA, "key")

        assertEquals(SaveKeyOutcome.UNAVAILABLE, outcome)
        assertTrue(keyRepository.saveCalls.isEmpty())
    }

    @Test
    fun `R-43 저장이 IOException으로 실패하면 STORE_FAILED를 돌려주고 키가 없다`() = runTest {
        keyRepository.failOnSave = true

        val outcome = useCase(TWELVE_DATA, "goodkey")

        assertEquals(SaveKeyOutcome.STORE_FAILED, outcome)
        assertFalse(keyRepository.observeKeys().first().containsKey(TWELVE_DATA))
    }

    @Test
    fun `R-27 키의 앞뒤 빈칸을 빼고 확인하고 저장한다`() = runTest {
        useCase(TWELVE_DATA, "  abcd1234  ")

        assertEquals(listOf(TWELVE_DATA to "abcd1234"), checkRepository.calls)
        assertEquals("abcd1234", keyRepository.keys.value[TWELVE_DATA]?.key)
    }
}
