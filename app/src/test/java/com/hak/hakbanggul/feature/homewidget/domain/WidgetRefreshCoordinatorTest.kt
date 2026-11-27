package com.hak.hakbanggul.feature.homewidget.domain

import com.hak.hakbanggul.feature.marketmood.domain.RefreshStamp
import com.hak.hakbanggul.core.model.DataFetchError
import com.hak.hakbanggul.core.network.ApiKeyAvailability.AVAILABLE
import com.hak.hakbanggul.core.network.ApiKeyAvailability.MISSING
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshTrigger.FIRST_PLACED
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshTrigger.MANUAL
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshTrigger.PERIODIC
import com.hak.hakbanggul.feature.marketmood.domain.FetchResult
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData
import com.hak.hakbanggul.feature.marketmood.domain.MoodTestData.dataA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetRefreshCoordinatorTest {

    private fun TestScope.fixture() = WidgetTestFixture(this)

    private suspend fun TestScope.requestAndWait(f: WidgetTestFixture, trigger: WidgetRefreshTrigger) {
        val job = launch { f.coordinator.request(trigger) }
        advanceUntilIdle()
        job.join()
    }

    // 다른 범위에서 청한다(취소해 볼 때 쓴다).
    private fun TestScope.launchCaller(f: WidgetTestFixture, trigger: WidgetRefreshTrigger): Pair<CoroutineScope, Job> {
        val caller = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        return caller to caller.launch { f.coordinator.request(trigger) }
    }

    // R-20 FIRST_PLACED의 조건

    @Test
    fun `R-20 성공한 지 1시간이면 FIRST_PLACED로 받지 않는다`() = runTest {
        val f = fixture().apply { start() }
        requestAndWait(f, FIRST_PLACED)
        assertEquals(0, f.fetchCount)
    }

    @Test
    fun `R-20 성공한 지 6시간 1분이면 FIRST_PLACED로 받는다`() = runTest {
        val f = fixture()
        f.repository.setStored(dataA(), f.now.minus(Duration.ofHours(6).plusMinutes(1)))
        f.start()
        requestAndWait(f, FIRST_PLACED)
        assertEquals(2, f.fetchCount)
    }

    @Test
    fun `R-20 받아 둔 값이 없으면 FIRST_PLACED로 받는다`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        requestAndWait(f, FIRST_PLACED)
        assertEquals(2, f.fetchCount)
    }

    // R-21 PERIODIC의 조건

    @Test
    fun `R-21 최근에 성공하고 시작했어도 PERIODIC으로 받는다`() = runTest {
        val f = fixture()
        f.repository.setStored(dataA(), f.now.minus(Duration.ofMinutes(1)))
        f.repository.setFetchedAt(MoodTestData.STOCKS, null) // 받기가 ETF를 받게 받은 때를 비운다
        f.repository.lastStartedAt = f.now.minusSeconds(10)
        f.start()
        requestAndWait(f, PERIODIC)
        assertEquals(2, f.fetchCount)
    }

    @Test
    fun `R-21 FRED의 키가 없으면 PERIODIC으로 받지 않는다`() = runTest {
        val f = fixture().apply { setKeys(AVAILABLE, MISSING); start() }
        requestAndWait(f, PERIODIC)
        assertEquals(0, f.fetchCount)
    }

    // R-35, R-38 MANUAL의 조건

    @Test
    fun `R-35 시작한 지 1분 1초면 MANUAL로 받는다`() = runTest {
        val f = fixture()
        f.repository.setFetchedAt(MoodTestData.STOCKS, null) // 받기가 ETF를 받게 받은 때를 비운다
        f.repository.lastStartedAt = f.now.minusSeconds(61)
        f.start()
        requestAndWait(f, MANUAL)
        assertEquals(2, f.fetchCount)
    }

    @Test
    fun `R-35 시작한 지 꼭 1분이면 MANUAL로 받지 않는다`() = runTest {
        val f = fixture()
        f.repository.lastStartedAt = f.now.minusSeconds(60)
        f.start()
        requestAndWait(f, MANUAL)
        assertEquals(0, f.fetchCount)
    }

    @Test
    fun `R-38 시작한 때가 없으면 MANUAL로 받는다`() = runTest {
        val f = fixture().apply { start() }
        f.repository.setFetchedAt(MoodTestData.STOCKS, null) // 받기가 ETF를 받게 받은 때를 비운다
        requestAndWait(f, MANUAL)
        assertEquals(2, f.fetchCount)
    }

    // R-34 청하기만 하고, 앱에서 받는 중이면 겹치지 않음

    @Test
    fun `R-34 앱에서 받는 중이면 PERIODIC은 겹쳐 받지 않고 그 받기가 끝난 뒤 돌아온다`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        val wait = f.holdFetch()
        f.refresher.refresh()
        runCurrent()
        assertEquals(2, f.fetchCount)

        val job = launch { f.coordinator.request(PERIODIC) }
        runCurrent()
        assertFalse(job.isCompleted)

        wait.complete(Unit)
        advanceUntilIdle()
        assertTrue(job.isCompleted)
        assertEquals(2, f.fetchCount)
        // FRED와 묶음 하나를 따로 저장한다.
        assertEquals(2, f.repository.saveCalls.size)
        assertEquals(10, f.repository.stored.value.values.size)
    }

    // R-39 눌러 받는 중

    @Test
    fun `R-39 MANUAL로 받는 동안 눌러 받는 중이고 끝나면 풀린다`() = runTest {
        val f = fixture().apply { start() }
        val wait = f.holdFetch()
        val (_, job) = launchCaller(f, MANUAL)
        runCurrent()
        assertTrue(f.coordinator.isManualRefreshing.value)
        assertFalse(job.isCompleted)

        wait.complete(Unit)
        advanceUntilIdle()
        assertTrue(job.isCompleted)
        assertFalse(f.coordinator.isManualRefreshing.value)
    }

    @Test
    fun `R-39 눌러 받는 중에 다시 누르면 곧바로 돌아오고 겹쳐 받지 않는다`() = runTest {
        val f = fixture().apply { start() }
        f.repository.setFetchedAt(MoodTestData.STOCKS, null) // 받기가 ETF를 받게 받은 때를 비운다
        val wait = f.holdFetch()
        launchCaller(f, MANUAL)
        runCurrent()

        val second = launch { f.coordinator.request(MANUAL) }
        runCurrent()

        assertTrue(second.isCompleted)
        assertEquals(2, f.fetchCount)
        assertTrue(f.coordinator.isManualRefreshing.value)
        wait.complete(Unit)
        advanceUntilIdle()
        assertFalse(f.coordinator.isManualRefreshing.value)
    }

    @Test
    fun `R-39 앱에서 받는 중에 누르면 그 받기가 끝날 때까지 눌러 받는 중이다`() = runTest {
        val f = fixture().apply { start() }
        f.repository.setFetchedAt(MoodTestData.STOCKS, null) // 받기가 ETF를 받게 받은 때를 비운다
        val wait = f.holdFetch()
        f.refresher.refresh()
        runCurrent()

        val job = launch { f.coordinator.request(MANUAL) }
        runCurrent()
        assertTrue(f.coordinator.isManualRefreshing.value)
        assertEquals(2, f.fetchCount)

        wait.complete(Unit)
        advanceUntilIdle()
        assertTrue(job.isCompleted)
        assertFalse(f.coordinator.isManualRefreshing.value)
    }

    @Test
    fun `R-39 PERIODIC과 FIRST_PLACED는 눌러 받는 중을 바꾸지 않는다`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.coordinator.isManualRefreshing.toList(seen) }

        var wait = f.holdFetch()
        launch { f.coordinator.request(PERIODIC) }
        runCurrent()
        wait.complete(Unit)
        advanceUntilIdle()

        f.setStoredNone()
        // 두 번째 받기가 첫 묶음을 보내기 전에 65초를 기다리지 않게 한다.
        f.repository.stamps.remove(RefreshStamp.TWELVE_DATA_REQUESTED)
        wait = f.holdFetch()
        launch { f.coordinator.request(FIRST_PLACED) }
        runCurrent()
        assertEquals(4, f.fetchCount)
        wait.complete(Unit)
        advanceUntilIdle()

        assertTrue(seen.none { it })
    }

    // R-40

    @Test
    fun `R-40 MANUAL로 받기를 마치면 돌아온 때 눌러 받는 중이 풀려 있다`() = runTest {
        val f = fixture().apply { start() }
        var stateAtReturn: Boolean? = null
        launch {
            f.coordinator.request(MANUAL)
            stateAtReturn = f.coordinator.isManualRefreshing.value
        }
        advanceUntilIdle()
        assertEquals(false, stateAtReturn)
    }

    // R-41 받지 않았으면 1초 뒤에 풂

    private fun TestScope.assertReleasedAfterOneSecond(f: WidgetTestFixture) {
        val job = launch { f.coordinator.request(MANUAL) }
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertTrue(f.coordinator.isManualRefreshing.value)
        assertFalse(job.isCompleted)

        advanceTimeBy(1)
        runCurrent()
        assertFalse(f.coordinator.isManualRefreshing.value)
        assertTrue(job.isCompleted)
        assertEquals(0, f.fetchCount)
    }

    @Test
    fun `R-41 시작한 지 30초면 받지 않고 1초 뒤에 풀린다`() = runTest {
        val f = fixture()
        f.repository.lastStartedAt = f.now.minusSeconds(30)
        f.start()
        assertReleasedAfterOneSecond(f)
    }

    @Test
    fun `R-41 Twelve Data의 키가 없으면 받지 않고 1초 뒤에 풀린다`() = runTest {
        val f = fixture().apply { setKeys(MISSING, AVAILABLE); start() }
        assertReleasedAfterOneSecond(f)
    }

    // R-42 실패해도 풀림

    @Test
    fun `R-42 받기가 실패해도 예외 없이 돌아오고 바로 풀려 있다`() = runTest {
        val f = fixture().apply { answerAlways(FetchResult.Failure(DataFetchError.NO_CONNECTION)); start() }
        var stateAtReturn: Boolean? = null
        var returnedAt = -1L
        launch {
            f.coordinator.request(MANUAL)
            stateAtReturn = f.coordinator.isManualRefreshing.value
            returnedAt = testScheduler.currentTime
        }
        advanceUntilIdle()
        assertEquals(false, stateAtReturn)
        assertEquals(0L, returnedAt)
    }

    // R-43 예외나 취소로 끝나도 풀림

    @Test
    fun `R-43 청이 예외로 끝나도 밖으로 나오지 않고 풀린다`() = runTest {
        val f = fixture()
        f.refresherRepository = ThrowingStartedAtRepository(f.repository)
        f.start()
        val job = launch { f.coordinator.request(MANUAL) }
        advanceUntilIdle()
        assertTrue(job.isCompleted && !job.isCancelled)
        assertFalse(f.coordinator.isManualRefreshing.value)
    }

    @Test
    fun `R-43 기다리던 코루틴이 취소되면 풀리고 받기는 끝까지 이어져 저장된다`() = runTest {
        val f = fixture().apply { start() }
        f.repository.setFetchedAt(MoodTestData.STOCKS, null) // 받기가 ETF를 받게 받은 때를 비운다
        val wait = f.holdFetch()
        val (caller, _) = launchCaller(f, MANUAL)
        runCurrent()
        assertTrue(f.coordinator.isManualRefreshing.value)

        caller.cancel()
        runCurrent()
        assertFalse(f.coordinator.isManualRefreshing.value)

        wait.complete(Unit)
        advanceUntilIdle()
        // FRED와 묶음 하나를 따로 저장한다.
        assertEquals(2, f.repository.saveCalls.size)
    }

    @Test
    fun `R-43 1초를 기다리는 중에 취소되어도 풀린다`() = runTest {
        val f = fixture()
        f.repository.lastStartedAt = f.now.minusSeconds(30)
        f.start()
        val (caller, _) = launchCaller(f, MANUAL)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertTrue(f.coordinator.isManualRefreshing.value)

        caller.cancel()
        runCurrent()
        assertFalse(f.coordinator.isManualRefreshing.value)
    }

    // R-35, R-47 APP_UPDATED의 조건

    @Test
    fun `R-47 성공한 지 1시간이면 APP_UPDATED로 받지 않는다`() = runTest {
        val f = fixture().apply { start() }
        requestAndWait(f, WidgetRefreshTrigger.APP_UPDATED)
        assertEquals(0, f.fetchCount)
    }

    @Test
    fun `R-47 성공한 지 6시간 1분이면 APP_UPDATED로 받는다`() = runTest {
        val f = fixture()
        f.repository.setStored(dataA(), f.now.minus(Duration.ofHours(6).plusMinutes(1)))
        f.start()
        requestAndWait(f, WidgetRefreshTrigger.APP_UPDATED)
        assertEquals(2, f.fetchCount)
    }

    @Test
    fun `R-47 받아 둔 값이 없으면 APP_UPDATED로 받는다`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        requestAndWait(f, WidgetRefreshTrigger.APP_UPDATED)
        assertEquals(2, f.fetchCount)
    }

    @Test
    fun `R-35 FRED의 키가 없으면 APP_UPDATED로 받지 않는다`() = runTest {
        val f = fixture().apply {
            setKeys(AVAILABLE, MISSING)
            setStoredNone()
            start()
        }
        requestAndWait(f, WidgetRefreshTrigger.APP_UPDATED)
        assertEquals(0, f.fetchCount)
    }

    // R-47 눌러 받는 중을 바꾸지 않음

    @Test
    fun `R-47 APP_UPDATED는 눌러 받는 중을 바꾸지 않고 받기가 끝난 뒤 돌아온다`() = runTest {
        val f = fixture().apply { setStoredNone(); start() }
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.coordinator.isManualRefreshing.toList(seen) }

        val wait = f.holdFetch()
        val job = launch { f.coordinator.request(WidgetRefreshTrigger.APP_UPDATED) }
        runCurrent()
        assertFalse(job.isCompleted)

        wait.complete(Unit)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertTrue(seen.none { it })
    }
}
