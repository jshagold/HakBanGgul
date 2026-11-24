package com.hak.hakbanggul.feature.settings.presentation

import androidx.annotation.StringRes
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.ui.KeyBadgeKind

data class SettingsUiState(
    val cards: List<SourceCardUiState>,
)

data class SourceCardUiState(
    val source: DataSource,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val badge: KeyBadgeKind,
    val issueUrl: String,
    val mode: CardMode,
)

sealed interface CardMode {
    // 저장된 키가 없을 때: 입력 칸과 저장 버튼
    data class Input(
        val text: String,
        val inputEnabled: Boolean,
        val saveEnabled: Boolean,
        val checking: Boolean,
        val errorBorder: Boolean,
        @param:StringRes val messageRes: Int?,
    ) : CardMode

    // 저장된 키가 있을 때: 가린 키와 지우기 버튼
    data class Saved(
        val displayKey: String,
        val revealed: Boolean,
        val showRejectedNotice: Boolean,
    ) : CardMode
}
