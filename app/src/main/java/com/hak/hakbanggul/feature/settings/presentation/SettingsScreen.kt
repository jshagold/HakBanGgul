package com.hak.hakbanggul.feature.settings.presentation

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.ui.AppIcons
import com.hak.hakbanggul.core.ui.KeyBadgeKind
import com.hak.hakbanggul.core.ui.KeyStatusBadge
import com.hak.hakbanggul.core.ui.SourceLabels
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme

// 설정 화면. Material의 기본 모양 부품을 쓰지 않고 테마의 값으로 직접 짠다.
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    // 화면이 보이지 않게 되면 입력 글자와 키 보기를 비운다. 화면 회전 같은 구성 변경은 빼고.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.onScreenHidden()
    }

    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onKeyInputChange = viewModel::onKeyInputChange,
        onSaveClick = viewModel::onSaveClick,
        onRevealToggle = viewModel::onRevealToggle,
        onDeleteConfirm = viewModel::onDeleteConfirm,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onKeyInputChange: (DataSource, String) -> Unit,
    onSaveClick: (DataSource) -> Unit,
    onRevealToggle: (DataSource) -> Unit,
    onDeleteConfirm: (DataSource) -> Unit,
) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        TopBar(onBack)

        // 위쪽 줄은 제자리에 두고 그 아래만 위아래로 밀어 본다.
        // 아래쪽 여백에 키보드 높이를 넣어, 키보드가 올라와도 입력 칸과 저장 버튼까지 밀어 볼 수 있게 한다.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding, vertical = dimens.settingsBodyPaddingVertical)
                .windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom),
                ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            Text(
                text = stringResource(R.string.settings_intro),
                style = HakBanGgulTheme.typography.smallLoose,
                color = colors.textMuted,
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

// 위쪽 줄

@Composable
private fun TopBar(onBack: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    // 상태 표시줄 자리도 위쪽 줄의 바탕으로 칠한다.
    Column(modifier = Modifier.background(colors.card).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.topBarHeight)
                .padding(horizontal = dimens.settingsTopBarPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.settingsTopBarGap),
        ) {
            IconButtonBox(onClick = onBack) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.text,
                    modifier = Modifier.size(dimens.backIcon),
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = HakBanGgulTheme.typography.title,
                color = colors.text,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.divider)
                .background(colors.border),
        )
    }
}

// 출처 카드

@Composable
private fun SourceCard(
    card: SourceCardUiState,
    onKeyInputChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onRevealToggle: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val shape = HakBanGgulTheme.shapes.card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, shape)
            .border(dimens.cardBorder, colors.border, shape)
            .padding(dimens.sourceCardPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.sourceCardGap),
    ) {
        CardHeader(card)
        when (val mode = card.mode) {
            is CardMode.Input -> InputSection(mode, card.issueUrl, onKeyInputChange, onSaveClick)
            is CardMode.Saved -> SavedSection(mode, onRevealToggle, onDeleteConfirm)
        }
    }
}

// 카드의 머리: 출처 이름과 무엇에 쓰는 키인지, 오른쪽에 딱지. 카드의 상태와 상관없이 늘 있다.
@Composable
private fun CardHeader(card: SourceCardUiState) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.sourceCardHeaderGap),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.nameGap)) {
            Text(stringResource(card.nameRes), style = typography.groupName, color = colors.text)
            Text(stringResource(card.descriptionRes), style = typography.caption, color = colors.textMuted)
        }
        KeyStatusBadge(card.badge)
    }
}

// 입력 모드: 이름표와 입력 칸, 칸 아래 안내, 발급 안내와 저장 버튼 자리
@Composable
private fun InputSection(
    mode: CardMode.Input,
    issueUrl: String,
    onKeyInputChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    val dimens = HakBanGgulTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.inputGroupGap)) {
        FieldLabel()
        KeyInputField(mode, onKeyInputChange)
        mode.messageRes?.let { FieldNotice(it) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.issueButtonGap),
    ) {
        IssueGuide(issueUrl, Modifier.weight(1f))
        if (mode.checking) CheckingIndicator() else SaveButton(enabled = mode.saveEnabled, onClick = onSaveClick)
    }
}

@Composable
private fun FieldLabel() {
    Text(
        text = stringResource(R.string.key_input_label),
        style = HakBanGgulTheme.typography.inputLabel,
        color = HakBanGgulTheme.colors.textMuted,
    )
}

// 한 줄짜리 입력 칸. 초점이 있어도 테두리는 그대로 두고 커서만 강조 색으로 보인다.
@Composable
private fun KeyInputField(mode: CardMode.Input, onValueChange: (String) -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    val shape = HakBanGgulTheme.shapes.button
    val borderWidth = if (mode.errorBorder) dimens.inputBorderError else dimens.inputBorder
    val borderColor = when {
        mode.errorBorder -> colors.errorBorder
        !mode.inputEnabled -> colors.inputDisabledBorder
        else -> colors.disabled
    }
    val background = if (mode.inputEnabled) colors.card else colors.inputDisabledBackground
    val textColor = if (mode.inputEnabled) colors.text else colors.textMuted
    BasicTextField(
        value = mode.text,
        onValueChange = onValueChange,
        enabled = mode.inputEnabled,
        singleLine = true,
        textStyle = typography.body.copy(color = textColor),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.inputHeight)
                    .background(background, shape)
                    .border(borderWidth, borderColor, shape)
                    // 시안처럼 안쪽 여백은 테두리 안쪽에서 잰다.
                    .padding(horizontal = dimens.inputPaddingHorizontal + borderWidth),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (mode.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.key_input_placeholder),
                        style = typography.body,
                        color = colors.disabled,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

// 칸 아래 안내: 알림 아이콘과 글. 아이콘은 글의 첫 줄에 맞춘다.
@Composable
private fun FieldNotice(@StringRes textRes: Int) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.fieldNoticeIconGap)) {
        Icon(
            imageVector = AppIcons.Alert,
            contentDescription = null,
            tint = colors.warningText,
            modifier = Modifier.padding(top = dimens.fieldNoticeIconTop).size(dimens.fieldNoticeIcon),
        )
        Text(
            text = stringResource(textRes),
            style = HakBanGgulTheme.typography.captionBanner,
            color = colors.warningText,
        )
    }
}

// 발급 안내: "키 발급받기"(누르면 발급 페이지를 브라우저로 연다)와 그 아래 "무료, 카드 등록 없음"
@Composable
private fun IssueGuide(issueUrl: String, modifier: Modifier = Modifier) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.issueNoteGap)) {
        Text(
            text = stringResource(R.string.key_issue_link),
            style = typography.issueLink,
            color = colors.accent,
            modifier = Modifier
                .clickable(role = Role.Button) { uriHandler.openUri(issueUrl) }
                .padding(vertical = dimens.issueLinkPaddingVertical),
        )
        Text(stringResource(R.string.key_issue_note), style = typography.issueNote, color = colors.textMuted)
    }
}

// 채운 저장 버튼. 비활성이면 흐린 모양이고 눌러도 반응하지 않는다.
@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val shape = HakBanGgulTheme.shapes.button
    Box(
        modifier = Modifier
            .height(dimens.cardButtonHeight)
            .clip(shape)
            .background(if (enabled) colors.accent else colors.filledButtonDisabledBackground)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.saveButtonPaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.key_save),
            style = HakBanGgulTheme.typography.bodyBold,
            color = if (enabled) colors.onAccent else colors.disabled,
        )
    }
}

// 확인 중 표시: 돌아가는 고리와 "확인 중". 누를 수 없다.
@Composable
private fun CheckingIndicator() {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    Row(
        modifier = Modifier
            .height(dimens.checkingHeight)
            .background(colors.checkingBackground, HakBanGgulTheme.shapes.button)
            .padding(horizontal = dimens.checkingPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.checkingGap),
    ) {
        // 고리의 크기는 선 굵기까지 넣은 바깥 지름이다(CircularProgressIndicator는 크기 안에 선을 그린다).
        Box(modifier = Modifier.size(dimens.checkingProgressSlot), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(dimens.checkingProgress),
                color = colors.checkingContent,
                trackColor = colors.checkingTrack,
                strokeWidth = dimens.checkingProgressStroke,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
        Text(
            text = stringResource(R.string.key_checking),
            style = HakBanGgulTheme.typography.bodyBold,
            color = colors.checkingContent,
        )
    }
}

// 저장 모드: 이름표와 저장된 키 칸, 거절 안내, 키 지우기 버튼
@Composable
private fun SavedSection(
    mode: CardMode.Saved,
    onRevealToggle: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    val dimens = HakBanGgulTheme.dimens
    // 지우기 확인 창을 띄울지는 화면 안의 상태로 둔다.
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.inputGroupGap)) {
        FieldLabel()
        SavedKeyField(mode, onRevealToggle)
        if (mode.showRejectedNotice) FieldNotice(R.string.key_rejected_notice)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        DeleteButton(onClick = { showDeleteDialog = true })
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

// 저장된 키 칸. 키 전체가 보일 때는 글자 사이 어디서든 다음 줄로 넘기고 칸의 높이를 늘린다.
@Composable
private fun SavedKeyField(mode: CardMode.Saved, onRevealToggle: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    val shape = HakBanGgulTheme.shapes.button
    val borderWidth = if (mode.showRejectedNotice) dimens.savedKeyBorderError else dimens.savedKeyBorder
    val borderColor = if (mode.showRejectedNotice) colors.errorBorder else colors.border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.savedKeyMinHeight)
            .background(colors.background, shape)
            .border(borderWidth, borderColor, shape)
            // 시안처럼 안쪽 여백은 테두리 안쪽에서 잰다.
            .padding(start = dimens.savedKeyPaddingStart + borderWidth, end = dimens.savedKeyPaddingEnd + borderWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.savedKeyTextButtonGap),
    ) {
        if (mode.revealed) {
            Text(
                text = mode.displayKey,
                style = typography.fullKey.copy(lineBreak = LineBreak.Simple),
                color = colors.text,
                modifier = Modifier.weight(1f).padding(vertical = dimens.savedKeyTextPaddingVertical),
            )
        } else {
            Text(
                text = mode.displayKey,
                style = typography.maskedKey,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f),
            )
        }
        IconButtonBox(onClick = onRevealToggle) {
            Icon(
                imageVector = if (mode.revealed) AppIcons.EyeOff else AppIcons.Eye,
                contentDescription = stringResource(if (mode.revealed) R.string.key_conceal else R.string.key_reveal),
                tint = colors.textMuted,
                modifier = Modifier.size(dimens.icon),
            )
        }
    }
}

// 테두리만 있는 "키 지우기" 버튼
@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val shape = HakBanGgulTheme.shapes.button
    Box(
        modifier = Modifier
            .height(dimens.cardButtonHeight)
            .clip(shape)
            .background(colors.card)
            .border(dimens.deleteButtonBorder, colors.disabled, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.deleteButtonPaddingHorizontal + dimens.deleteButtonBorder),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.key_delete),
            style = HakBanGgulTheme.typography.bodyMedium,
            color = colors.warningText,
        )
    }
}

// 누르는 영역 44, 둥근 물결이 퍼지는 아이콘 버튼
@Composable
private fun IconButtonBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(HakBanGgulTheme.dimens.iconButton)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// 미리보기: 시안의 네 화면과 같은 상태

private fun previewCard(source: DataSource, badge: KeyBadgeKind, mode: CardMode) = SourceCardUiState(
    source = source,
    nameRes = SourceLabels.nameRes(source),
    descriptionRes = SourceLabels.descriptionRes(source),
    badge = badge,
    issueUrl = SourceCardInfo.of(source).issueUrl,
    mode = mode,
)

private fun emptyInput() =
    CardMode.Input("", inputEnabled = true, saveEnabled = false, checking = false, errorBorder = false, messageRes = null)

@Composable
private fun PreviewSettings(vararg cards: SourceCardUiState) {
    HakBanGgulTheme {
        SettingsContent(
            uiState = SettingsUiState(cards.toList()),
            onBack = {},
            onKeyInputChange = { _, _ -> },
            onSaveClick = {},
            onRevealToggle = {},
            onDeleteConfirm = {},
        )
    }
}

@Preview(name = "키를 넣기 전", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsMainPreview() = PreviewSettings(
    previewCard(DataSource.TWELVE_DATA, KeyBadgeKind.NONE, emptyInput()),
    previewCard(DataSource.FRED, KeyBadgeKind.NONE, emptyInput()),
)

@Preview(name = "저장됨과 틀림", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsStatesPreview() = PreviewSettings(
    previewCard(
        DataSource.TWELVE_DATA,
        KeyBadgeKind.SAVED,
        CardMode.Saved(SettingsViewModel.mask("9c41d7e2ab5f4c08b3d16e7a20f83f9a"), revealed = false, showRejectedNotice = false),
    ),
    previewCard(
        DataSource.FRED,
        KeyBadgeKind.INVALID,
        CardMode.Input(
            "abcd1234efgh5678ijkl",
            inputEnabled = true,
            saveEnabled = true,
            checking = false,
            errorBorder = true,
            messageRes = R.string.key_message_rejected,
        ),
    ),
)

@Preview(name = "확인 중과 거절된 키", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsCheckingPreview() = PreviewSettings(
    previewCard(
        DataSource.TWELVE_DATA,
        KeyBadgeKind.NONE,
        CardMode.Input(
            "9c41d7e2ab5f4c08b3d1",
            inputEnabled = false,
            saveEnabled = false,
            checking = true,
            errorBorder = false,
            messageRes = null,
        ),
    ),
    previewCard(
        DataSource.FRED,
        KeyBadgeKind.INVALID,
        CardMode.Saved(SettingsViewModel.mask("fredkey000000000000000000007b2e"), revealed = false, showRejectedNotice = true),
    ),
)

@Preview(name = "키 보기와 확인하지 못한 키", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsRevealPreview() = PreviewSettings(
    previewCard(
        DataSource.TWELVE_DATA,
        KeyBadgeKind.SAVED,
        CardMode.Saved("9c41d7e2ab5f4c08b3d16e7a20f83f9a", revealed = true, showRejectedNotice = false),
    ),
    previewCard(
        DataSource.FRED,
        KeyBadgeKind.NONE,
        CardMode.Input(
            "abcd1234efgh5678ijkl",
            inputEnabled = true,
            saveEnabled = true,
            checking = false,
            errorBorder = false,
            messageRes = R.string.key_message_no_connection,
        ),
    ),
)
