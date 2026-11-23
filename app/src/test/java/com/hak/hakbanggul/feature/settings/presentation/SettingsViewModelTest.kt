package com.hak.hakbanggul.feature.settings.presentation

import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.model.DataSource.FRED
import com.hak.hakbanggul.core.model.DataSource.TWELVE_DATA
import com.hak.hakbanggul.feature.settings.domain.DeleteApiKeyUseCase
import com.hak.hakbanggul.feature.settings.domain.FakeApiKeyCheckRepository
import com.hak.hakbanggul.feature.settings.domain.FakeApiKeyRepository
import com.hak.hakbanggul.feature.settings.domain.KeyCheckManager
import com.hak.hakbanggul.feature.settings.domain.KeyCheckResult
import com.hak.hakbanggul.feature.settings.domain.ObserveApiKeysUseCase
import com.hak.hakbanggul.feature.settings.domain.SaveApiKeyUseCase
import com.hak.hakbanggul.feature.settings.domain.invalid
import com.hak.hakbanggul.feature.settings.domain.saved
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val keyRepository = FakeApiKeyRepository()
    private val checkRepository = FakeApiKeyCheckRepository()
    private val keyCheckManager = KeyCheckManager(SaveApiKeyUseCase(checkRepository, keyRepository), appScope)

    // 테스트가 답을 줄 때까지 시험 요청을 붙잡아 둔다.
    private val pendingAnswer = CompletableDeferred<KeyCheckResult>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(
            ObserveApiKeysUseCase(keyRepository),
            DeleteApiKeyUseCase(keyRepository),
            keyCheckManager,
        )
        // uiState는 받는 쪽이 있을 때만 돈다.
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun SettingsViewModel.card(source: DataSource) = uiState.value.cards.first { it.source == source }

    private fun SettingsViewModel.input(source: DataSource) = card(source).mode as CardMode.Input

    private fun SettingsViewModel.savedMode(source: DataSource) = card(source).mode as CardMode.Saved

    // 키를 넣고 저장을 눌러 확인이 result로 끝나게 한다.
    private fun SettingsViewModel.saveWithResult(source: DataSource, key: String, result: KeyCheckResult) {
        checkRepository.answer = { _, _ -> result }
        onKeyInputChange(source, key)
        onSaveClick(source)
    }

    private fun SettingsViewModel.startCheckingAndHold(source: DataSource, key: String) {
        checkRepository.answer = { _, _ -> pendingAnswer.await() }
        onKeyInputChange(source, key)
        onSaveClick(source)
    }

    @Test
    fun `R-03 카드는 TWELVE_DATA, FRED 순서이고 이름과 설명이 맞다`() = runTest(dispatcher) {
        val vm = newViewModel()

        val cards = vm.uiState.value.cards
        assertEquals(listOf(TWELVE_DATA, FRED), cards.map { it.source })
        assertEquals(R.string.source_twelve_data_name, cards[0].nameRes)
        assertEquals(R.string.source_twelve_data_description, cards[0].descriptionRes)
        assertEquals(R.string.source_fred_name, cards[1].nameRes)
        assertEquals(R.string.source_fred_description, cards[1].descriptionRes)
    }

    @Test
    fun `R-05 저장된 상태에 맞는 딱지를 고른다`() = runTest(dispatcher) {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "tdkey"), invalid(FRED, "fredkey"))
        val vm = newViewModel()

        assertEquals(KeyBadge.SAVED, vm.card(TWELVE_DATA).badge)
        assertEquals(KeyBadge.INVALID, vm.card(FRED).badge)
    }

    @Test
    fun `R-17 처음 설치하면 두 카드 모두 키 없음이다`() = runTest(dispatcher) {
        val vm = newViewModel()

        assertEquals(listOf(KeyBadge.NONE, KeyBadge.NONE), vm.uiState.value.cards.map { it.badge })
    }

    @Test
    fun `R-06 저장된 키가 없으면 빈 입력 모드다`() = runTest(dispatcher) {
        val vm = newViewModel()

        val input = vm.input(TWELVE_DATA)
        assertEquals("", input.text)
        assertNull(input.messageRes)
        assertFalse(input.errorBorder)
    }

    @Test
    fun `R-07 저장된 키는 끝 네 글자만 두고 가린다`() = runTest(dispatcher) {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "abcdefgh12345678"))
        val vm = newViewModel()

        val mode = vm.savedMode(TWELVE_DATA)
        assertEquals("••••••••••••5678", mode.displayKey)
        assertFalse(mode.revealed)
    }

    @Test
    fun `R-07 네 글자 이하인 키는 모두 가린다`() = runTest(dispatcher) {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "abcd"))
        val vm = newViewModel()

        assertEquals("••••", vm.savedMode(TWELVE_DATA).displayKey)
    }

    @Test
    fun `R-13 키 보기를 켜면 키 전체를 보인다`() = runTest(dispatcher) {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "abcdefgh12345678"))
        val vm = newViewModel()

        vm.onRevealToggle(TWELVE_DATA)

        val mode = vm.savedMode(TWELVE_DATA)
        assertEquals("abcdefgh12345678", mode.displayKey)
        assertTrue(mode.revealed)
    }

    @Test
    fun `R-33 키 보기를 한 번 더 누르면 다시 가린다`() = runTest(dispatcher) {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "abcdefgh12345678"))
        val vm = newViewModel()
        vm.onRevealToggle(TWELVE_DATA)

        vm.onRevealToggle(TWELVE_DATA)

        assertEquals("••••••••••••5678", vm.savedMode(TWELVE_DATA).displayKey)
    }

    @Test
    fun `R-33 화면이 보이지 않게 되면 다시 가린다`() = runTest(dispatcher) {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "abcdefgh12345678"))
        val vm = newViewModel()
        vm.onRevealToggle(TWELVE_DATA)

        vm.onScreenHidden()

        assertEquals("••••••••••••5678", vm.savedMode(TWELVE_DATA).displayKey)
    }

    @Test
    fun `R-08 거절되면 넣은 키가 남고 테두리가 빨갛다`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.saveWithResult(TWELVE_DATA, "badkey123", KeyCheckResult.REJECTED)

        val input = vm.input(TWELVE_DATA)
        assertEquals("badkey123", input.text)
        assertTrue(input.errorBorder)
    }

    @Test
    fun `R-18 거절되면 틀림 딱지와 안내를 보이고 저장된 상태는 그대로다`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.saveWithResult(TWELVE_DATA, "badkey123", KeyCheckResult.REJECTED)

        assertEquals(KeyBadge.INVALID, vm.card(TWELVE_DATA).badge)
        assertEquals(R.string.key_message_rejected, vm.input(TWELVE_DATA).messageRes)
        assertFalse(ObserveApiKeysUseCase(keyRepository)().first().containsKey(TWELVE_DATA))
    }

    @Test
    fun `R-28 저장된 키가 틀림이면 가린 채로 거절 안내를 보인다`() = runTest(dispatcher) {
        keyRepository.keys.value = mapOf(invalid(TWELVE_DATA, "abcdefgh12345678"))
        val vm = newViewModel()

        val card = vm.card(TWELVE_DATA)
        val mode = card.mode as CardMode.Saved
        assertEquals(KeyBadge.INVALID, card.badge)
        assertTrue(mode.showRejectedNotice)
        assertEquals("••••••••••••5678", mode.displayKey)
    }

    @Test
    fun `R-32 확인 중인 카드만 입력과 저장을 막는다`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.startCheckingAndHold(TWELVE_DATA, "abc")

        val twelve = vm.input(TWELVE_DATA)
        assertTrue(twelve.checking)
        assertFalse(twelve.inputEnabled)
        assertFalse(twelve.saveEnabled)
        assertTrue(vm.input(FRED).inputEnabled)
    }

    @Test
    fun `R-37 확인 중에 화면을 벗어나도 확인 중과 넣은 키가 남는다`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.startCheckingAndHold(TWELVE_DATA, "newkey123")

        vm.onScreenHidden()

        val input = vm.input(TWELVE_DATA)
        assertTrue(input.checking)
        assertEquals("newkey123", input.text)
    }

    @Test
    fun `R-37 벗어난 사이 거절되면 넣은 키와 거절 안내가 보인다`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.startCheckingAndHold(TWELVE_DATA, "newkey123")
        vm.onScreenHidden()

        pendingAnswer.complete(KeyCheckResult.REJECTED)

        val input = vm.input(TWELVE_DATA)
        assertEquals("newkey123", input.text)
        assertEquals(KeyBadge.INVALID, vm.card(TWELVE_DATA).badge)
        assertEquals(R.string.key_message_rejected, input.messageRes)
    }

    @Test
    fun `R-37 벗어난 사이 저장되면 가린 저장 모드가 보인다`() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.startCheckingAndHold(TWELVE_DATA, "newkey123")
        vm.onScreenHidden()

        pendingAnswer.complete(KeyCheckResult.ACCEPTED)

        assertFalse(vm.savedMode(TWELVE_DATA).revealed)
    }

    @Test
    fun `R-19 연결이 안 되면 넣은 키와 연결 안내가 보이고 딱지는 키 없음이다`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.saveWithResult(TWELVE_DATA, "key1", KeyCheckResult.NO_CONNECTION)

        val input = vm.input(TWELVE_DATA)
        assertEquals("key1", input.text)
        assertEquals(R.string.key_message_no_connection, input.messageRes)
        assertEquals(KeyBadge.NONE, vm.card(TWELVE_DATA).badge)
        assertFalse(input.errorBorder)
    }

    @Test
    fun `R-30 확인할 수 없으면 잠시 뒤 안내가 보이고 딱지는 키 없음이다`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.saveWithResult(TWELVE_DATA, "key1", KeyCheckResult.UNAVAILABLE)

        assertEquals(R.string.key_message_unavailable, vm.input(TWELVE_DATA).messageRes)
        assertEquals(KeyBadge.NONE, vm.card(TWELVE_DATA).badge)
    }

    @Test
    fun `R-43 저장하지 못하면 넣은 키와 저장 실패 안내가 보이고 딱지는 키 없음이다`() = runTest(dispatcher) {
        keyRepository.failOnSave = true
        val vm = newViewModel()

        vm.saveWithResult(TWELVE_DATA, "key1", KeyCheckResult.ACCEPTED)

        val input = vm.input(TWELVE_DATA)
        assertEquals("key1", input.text)
        assertEquals(R.string.key_message_store_failed, input.messageRes)
        assertEquals(KeyBadge.NONE, vm.card(TWELVE_DATA).badge)
    }

    @Test
    fun `R-26 빈 입력이면 저장할 수 없다`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.onKeyInputChange(TWELVE_DATA, "")

        assertFalse(vm.input(TWELVE_DATA).saveEnabled)
    }

    @Test
    fun `R-26 빈칸만 있으면 저장할 수 없다`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.onKeyInputChange(TWELVE_DATA, "   ")

        assertFalse(vm.input(TWELVE_DATA).saveEnabled)
    }

    @Test
    fun `R-26 글자가 있으면 저장할 수 있다`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.onKeyInputChange(TWELVE_DATA, "a")

        assertTrue(vm.input(TWELVE_DATA).saveEnabled)
    }

    @Test
    fun `R-34 지우면 그 카드는 키 없음 입력 모드다`() = runTest(dispatcher) {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "tdkey"), saved(FRED, "fredkey"))
        val vm = newViewModel()

        vm.onDeleteConfirm(TWELVE_DATA)

        assertTrue(vm.card(TWELVE_DATA).mode is CardMode.Input)
        assertEquals(KeyBadge.NONE, vm.card(TWELVE_DATA).badge)
    }

    @Test
    fun `R-16 한 카드를 지워도 다른 카드는 저장됨 그대로다`() = runTest(dispatcher) {
        keyRepository.keys.value = mapOf(saved(TWELVE_DATA, "tdkey"), saved(FRED, "fredkey"))
        val vm = newViewModel()

        vm.onDeleteConfirm(TWELVE_DATA)

        assertTrue(vm.card(FRED).mode is CardMode.Saved)
        assertEquals(KeyBadge.SAVED, vm.card(FRED).badge)
    }

    @Test
    fun `R-15 카드마다 키 발급 페이지 주소가 맞다`() = runTest(dispatcher) {
        val vm = newViewModel()

        assertEquals("https://twelvedata.com/register", vm.card(TWELVE_DATA).issueUrl)
        assertEquals("https://fredaccount.stlouisfed.org/apikeys", vm.card(FRED).issueUrl)
    }
}
