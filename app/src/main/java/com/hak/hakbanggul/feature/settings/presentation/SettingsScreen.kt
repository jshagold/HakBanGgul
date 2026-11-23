package com.hak.hakbanggul.feature.settings.presentation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.ui.theme.HakBanGgulTheme

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    // 화면이 보이지 않게 되면 입력 글자와 키 보기를 비운다. 화면 회전 같은 구성 변경은 빼고.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.onScreenHidden()
    }

    SettingsContent(
        uiState = uiState,
        onKeyInputChange = viewModel::onKeyInputChange,
        onSaveClick = viewModel::onSaveClick,
        onRevealToggle = viewModel::onRevealToggle,
        onDeleteConfirm = viewModel::onDeleteConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onKeyInputChange: (DataSource, String) -> Unit,
    onSaveClick: (DataSource) -> Unit,
    onRevealToggle: (DataSource) -> Unit,
    onDeleteConfirm: (DataSource) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            uiState.cards.forEach { card ->
                SourceCard(
                    card = card,
                    onKeyInputChange = { onKeyInputChange(card.source, it) },
                    onSaveClick = { onSaveClick(card.source) },
                    onRevealToggle = { onRevealToggle(card.source) },
                    onDeleteConfirm = { onDeleteConfirm(card.source) },
                )
            }
        }
    }
}

@Composable
private fun SourceCard(
    card: SourceCardUiState,
    onKeyInputChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onRevealToggle: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(card.nameRes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    BadgeChip(card.badge)
                }
                Text(
                    text = stringResource(card.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val mode = card.mode) {
                is CardMode.Input -> InputSection(mode, card.issueUrl, onKeyInputChange, onSaveClick)
                is CardMode.Saved -> SavedSection(mode, onRevealToggle, onDeleteConfirm)
            }
        }
    }
}

// 딱지는 글과 색을 함께 쓴다.
@Composable
private fun BadgeChip(badge: KeyBadge) {
    val (textRes, color) = when (badge) {
        KeyBadge.NONE -> R.string.key_badge_none to BadgeGray
        KeyBadge.SAVED -> R.string.key_badge_saved to BadgeBlue
        KeyBadge.INVALID -> R.string.key_badge_invalid to BadgeRed
    }
    Surface(color = color, contentColor = Color.White, shape = RoundedCornerShape(50)) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun InputSection(
    mode: CardMode.Input,
    issueUrl: String,
    onKeyInputChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = mode.text,
            onValueChange = onKeyInputChange,
            enabled = mode.inputEnabled,
            singleLine = true,
            label = { Text(stringResource(R.string.key_input_label)) },
            placeholder = { Text(stringResource(R.string.key_input_placeholder)) },
            isError = mode.errorBorder,
            colors = OutlinedTextFieldDefaults.colors(errorBorderColor = BadgeRed),
            supportingText = mode.messageRes?.let { res -> { Text(stringResource(res)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                TextButton(onClick = { uriHandler.openUri(issueUrl) }) {
                    Text(stringResource(R.string.key_issue_link))
                }
                Text(
                    text = stringResource(R.string.key_issue_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            if (mode.checking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.key_checking))
                }
            } else {
                Button(onClick = onSaveClick, enabled = mode.saveEnabled) {
                    Text(stringResource(R.string.key_save))
                }
            }
        }
    }
}

@Composable
private fun SavedSection(
    mode: CardMode.Saved,
    onRevealToggle: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    // 지우기 확인 창을 띄울지는 화면 안의 상태로 둔다.
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = mode.displayKey,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRevealToggle) {
                Icon(
                    imageVector = if (mode.revealed) KeyVisibilityIcons.Hidden else KeyVisibilityIcons.Visible,
                    contentDescription = stringResource(if (mode.revealed) R.string.key_conceal else R.string.key_reveal),
                )
            }
        }
        if (mode.showRejectedNotice) {
            Text(
                text = stringResource(R.string.key_rejected_notice),
                style = MaterialTheme.typography.bodySmall,
                color = BadgeRed,
            )
        }
        OutlinedButton(onClick = { showDeleteDialog = true }) {
            Text(stringResource(R.string.key_delete))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.key_delete_confirm_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteConfirm()
                }) { Text(stringResource(R.string.dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

private val BadgeGray = Color(0xFF757575)
private val BadgeBlue = Color(0xFF1E66D0)
private val BadgeRed = Color(0xFFC62828)

// 미리보기

private fun previewCard(badge: KeyBadge, mode: CardMode) = SourceCardUiState(
    source = DataSource.TWELVE_DATA,
    nameRes = R.string.source_twelve_data_name,
    descriptionRes = R.string.source_twelve_data_description,
    badge = badge,
    issueUrl = "https://twelvedata.com/register",
    mode = mode,
)

@Composable
private fun PreviewSourceCard(card: SourceCardUiState) {
    HakBanGgulTheme {
        SourceCard(card = card, onKeyInputChange = {}, onSaveClick = {}, onRevealToggle = {}, onDeleteConfirm = {})
    }
}

@Preview(name = "키 없음", showBackground = true)
@Composable
private fun SourceCardNoKeyPreview() = PreviewSourceCard(
    previewCard(
        KeyBadge.NONE,
        CardMode.Input("", inputEnabled = true, saveEnabled = false, checking = false, errorBorder = false, messageRes = null),
    ),
)

@Preview(name = "확인 중", showBackground = true)
@Composable
private fun SourceCardCheckingPreview() = PreviewSourceCard(
    previewCard(
        KeyBadge.NONE,
        CardMode.Input("abcd1234", inputEnabled = false, saveEnabled = false, checking = true, errorBorder = false, messageRes = null),
    ),
)

@Preview(name = "거절됨 (입력 모드 틀림)", showBackground = true)
@Composable
private fun SourceCardRejectedPreview() = PreviewSourceCard(
    previewCard(
        KeyBadge.INVALID,
        CardMode.Input(
            "wrongkey123",
            inputEnabled = true,
            saveEnabled = true,
            checking = false,
            errorBorder = true,
            messageRes = R.string.key_message_rejected,
        ),
    ),
)

@Preview(name = "저장됨", showBackground = true)
@Composable
private fun SourceCardSavedPreview() = PreviewSourceCard(
    previewCard(KeyBadge.SAVED, CardMode.Saved("••••••••••••5678", revealed = false, showRejectedNotice = false)),
)

@Preview(name = "틀림 (저장 모드)", showBackground = true)
@Composable
private fun SourceCardInvalidSavedPreview() = PreviewSourceCard(
    previewCard(KeyBadge.INVALID, CardMode.Saved("••••••••••••5678", revealed = false, showRejectedNotice = true)),
)
