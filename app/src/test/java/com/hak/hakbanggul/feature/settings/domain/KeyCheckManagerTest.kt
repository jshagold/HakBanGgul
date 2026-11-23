package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyCheckManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    // 앱 범위를 흉내 낸다. 시간은 테스트가 움직인다.
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val keyRepository = FakeApiKeyRepository()
    private val checkRepository = FakeApiKeyCheckRepository()

    private fun newManager() = KeyCheckManager(SaveApiKeyUseCase(checkRepository, keyRepository), appScope)

    // 출처마다 테스트가 답을 줄 때까지 기다리게 한다.
    private val pending = mutableMapOf<DataSource, CompletableDeferred<KeyCheckResult>>()

    private fun answerWhenReleased() {
        checkRepository.answer = { source, _ ->
            pending.getOrPut(source) { CompletableDeferred() }.await()
        }
    }

    @Test
    fun `R-11 한 출처가 확인 중일 때 다른 출처도 따로 확인한다`() = testScope.runTest {
        answerWhenReleased()
        val manager = newManager()
        manager.startSave(TWELVE_DATA, "tdkey")
        runCurrent()

        manager.startSave(FRED, "fredkey")
        runCurrent()
        assertEquals(KeyCheckState.Checking("fredkey"), manager.states.value[FRED])

        pending.getValue(FRED).complete(KeyCheckResult.ACCEPTED)
        runCurrent()
        assertFalse(manager.states.value.containsKey(FRED))
        assertEquals(KeyCheckState.Checking("tdkey"), manager.states.value[TWELVE_DATA])
    }

    @Test
    fun `R-32 확인 중이면 넣은 그대로의 키로 Checking이다`() = testScope.runTest {
        answerWhenReleased()
        val manager = newManager()

        manager.startSave(TWELVE_DATA, "abc")
        runCurrent()

        assertEquals(KeyCheckState.Checking("abc"), manager.states.value[TWELVE_DATA])
    }

    @Test
    fun `R-32 확인 중에 다시 눌러도 시험 요청은 한 번만 간다`() = testScope.runTest {
        answerWhenReleased()
        val manager = newManager()

        manager.startSave(TWELVE_DATA, "abc")
        runCurrent()
        manager.startSave(TWELVE_DATA, "abc")
        runCurrent()

        assertEquals(1, checkRepository.calls.size)
    }

    @Test
    fun `R-36 부른 쪽의 범위가 취소되어도 확인은 끝까지 가서 저장된다`() = testScope.runTest {
        checkRepository.answer = { _, _ ->
            delay(1_000)
            KeyCheckResult.ACCEPTED
        }
        val manager = newManager()
        val callerScope = CoroutineScope(Job() + dispatcher)
        callerScope.launch { manager.startSave(TWELVE_DATA, "goodkey") }
        runCurrent()
        callerScope.cancel()

        advanceTimeBy(1_001)

        assertEquals(StoredApiKey(TWELVE_DATA, "goodkey", ApiKeyStatus.SAVED), keyRepository.keys.value[TWELVE_DATA])
        assertFalse(manager.states.value.containsKey(TWELVE_DATA))
    }

    @Test
    fun `R-20 거절되면 Failed로 남고 저장하지 않는다`() = testScope.runTest {
        checkRepository.answer = { _, _ -> KeyCheckResult.REJECTED }
        val manager = newManager()

        manager.startSave(TWELVE_DATA, " badkey ")
        advanceUntilIdle()

        assertEquals(KeyCheckState.Failed(" badkey ", SaveKeyOutcome.REJECTED), manager.states.value[TWELVE_DATA])
        assertTrue(keyRepository.saveCalls.isEmpty())
    }

    @Test
    fun `R-20 새로 만든 관리자는 확인 상태가 비어 있다`() = testScope.runTest {
        checkRepository.answer = { _, _ -> KeyCheckResult.REJECTED }
        val manager = newManager()
        manager.startSave(TWELVE_DATA, "badkey")
        advanceUntilIdle()
        assertTrue(manager.states.value.isNotEmpty())

        val restarted = newManager()

        assertTrue(restarted.states.value.isEmpty())
    }
}
