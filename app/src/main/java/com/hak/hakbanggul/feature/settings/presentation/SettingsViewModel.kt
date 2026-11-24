package com.hak.hakbanggul.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.ui.KeyBadgeKind
import com.hak.hakbanggul.core.ui.SourceLabels
import com.hak.hakbanggul.feature.settings.domain.ApiKeyStatus
import com.hak.hakbanggul.feature.settings.domain.DeleteApiKeyUseCase
import com.hak.hakbanggul.feature.settings.domain.KeyCheckManager
import com.hak.hakbanggul.feature.settings.domain.KeyCheckState
import com.hak.hakbanggul.feature.settings.domain.ObserveApiKeysUseCase
import com.hak.hakbanggul.feature.settings.domain.SaveKeyOutcome
import com.hak.hakbanggul.feature.settings.domain.StoredApiKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeApiKeys: ObserveApiKeysUseCase,
    private val deleteApiKey: DeleteApiKeyUseCase,
    private val keyCheckManager: KeyCheckManager,
) : ViewModel() {

    // 사용자가 고친 입력 글자. 화면이 보이지 않게 되면 비운다.
    private val inputs = MutableStateFlow<Map<DataSource, String>>(emptyMap())

    // 키 보기가 켜진 출처. 메모리에만 둔다.
    private val revealed = MutableStateFlow<Set<DataSource>>(emptySet())

    // 처음에는 카드 없이 시작해, 저장된 키를 읽기 전에 입력 모양이 잠깐 보이지 않게 한다.
    val uiState: StateFlow<SettingsUiState> =
        combine(observeApiKeys(), keyCheckManager.states, inputs, revealed) { keys, checks, inputs, revealed ->
            SettingsUiState(
                cards = DataSource.entries.map { source ->
                    buildCard(source, keys[source], checks[source], inputs[source], source in revealed)
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState(emptyList()))

    fun onKeyInputChange(source: DataSource, text: String) {
        inputs.update { it + (source to text) }
    }

    fun onSaveClick(source: DataSource) {
        val text = inputTextOf(inputs.value[source], keyCheckManager.states.value[source])
        if (text.isBlank() || keyCheckManager.states.value[source] is KeyCheckState.Checking) return
        // 넣은 글자는 이제 KeyCheckManager가 갖는다. 여기 사본은 버려 둘이 어긋나지 않게 한다.
        inputs.update { it - source }
        keyCheckManager.startSave(source, text)
    }

    fun onRevealToggle(source: DataSource) {
        revealed.update { if (source in it) it - source else it + source }
    }

    fun onDeleteConfirm(source: DataSource) {
        revealed.update { it - source }
        viewModelScope.launch { deleteApiKey(source) }
    }

    fun onScreenHidden() {
        inputs.value = emptyMap()
        revealed.value = emptySet()
    }

    private fun buildCard(
        source: DataSource,
        stored: StoredApiKey?,
        check: KeyCheckState?,
        input: String?,
        isRevealed: Boolean,
    ): SourceCardUiState {
        val info = SourceCardInfo.of(source)
        val badge: KeyBadgeKind
        val mode: CardMode
        if (stored != null) {
            val invalid = stored.status == ApiKeyStatus.INVALID
            badge = if (invalid) KeyBadgeKind.INVALID else KeyBadgeKind.SAVED
            mode = CardMode.Saved(
                displayKey = if (isRevealed) stored.key else mask(stored.key),
                revealed = isRevealed,
                showRejectedNotice = invalid,
            )
        } else {
            val checking = check is KeyCheckState.Checking
            val failure = (check as? KeyCheckState.Failed)?.outcome
            val rejected = failure == SaveKeyOutcome.REJECTED
            val text = inputTextOf(input, check)
            badge = if (rejected) KeyBadgeKind.INVALID else KeyBadgeKind.NONE
            mode = CardMode.Input(
                text = text,
                inputEnabled = !checking,
                saveEnabled = !checking && text.isNotBlank(),
                checking = checking,
                errorBorder = rejected,
                messageRes = failure?.let(::messageOf),
            )
        }
        return SourceCardUiState(
            source = source,
            nameRes = SourceLabels.nameRes(source),
            descriptionRes = SourceLabels.descriptionRes(source),
            badge = badge,
            issueUrl = info.issueUrl,
            mode = mode,
        )
    }

    // 입력 칸 글자: 고친 글자, 없으면 확인 중이거나 실패한 키, 그것도 없으면 빈 글자
    private fun inputTextOf(input: String?, check: KeyCheckState?): String =
        input ?: check?.enteredKey ?: ""

    private fun messageOf(outcome: SaveKeyOutcome): Int? = when (outcome) {
        SaveKeyOutcome.REJECTED -> R.string.key_message_rejected
        SaveKeyOutcome.NO_CONNECTION -> R.string.key_message_no_connection
        SaveKeyOutcome.UNAVAILABLE -> R.string.key_message_unavailable
        SaveKeyOutcome.STORE_FAILED -> R.string.key_message_store_failed
        SaveKeyOutcome.SAVED -> null
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val MASK_CHAR = '•'
        private const val MASK_LENGTH = 12
        private const val VISIBLE_TAIL = 4

        // 키의 길이와 상관없이 점 12개, 빈칸 하나, 끝 네 글자로 가린다. 네 글자 이하면 점 12개만 둔다.
        // 키의 길이만큼 점을 찍으면 긴 키가 칸을 넘치기 때문이다.
        fun mask(key: String): String {
            val dots = MASK_CHAR.toString().repeat(MASK_LENGTH)
            return if (key.length <= VISIBLE_TAIL) dots else "$dots ${key.takeLast(VISIBLE_TAIL)}"
        }
    }
}
