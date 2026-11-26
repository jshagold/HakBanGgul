package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.AppIcons
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme

// 상세 화면과 섹터 화면의 위쪽 줄: 뒤로 가기 단추, 이름(20 Bold)과 같은 줄에 대상(15 textMuted, 있을 때만).
// 새로 고침과 메뉴는 두지 않는다. 상태 표시줄 자리도 위쪽 줄 바탕으로 칠한다.
@Composable
internal fun DetailTopBar(title: String, target: String?, onBack: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Column(modifier = Modifier.background(colors.card).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.topBarHeight)
                .padding(horizontal = dimens.detailTopBarPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.detailTopBarGap),
        ) {
            IconButtonBox(onClick = onBack, enabled = true) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.text,
                    modifier = Modifier.size(dimens.icon),
                )
            }
            Text(
                text = buildAnnotatedString {
                    append(title)
                    if (target != null) {
                        append(" ")
                        withStyle(typography.body.toSpanStyle().copy(color = colors.textMuted)) { append(target) }
                    }
                },
                style = typography.title,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        MoodDivider()
    }
}

// 상세 화면과 섹터 화면의 틀: 위쪽 줄을 제자리에 두고 본문만 위아래로 민다. content가 null이면(Undecided) 위쪽 줄만 그린다.
@Composable
internal fun DetailScaffold(
    title: String,
    target: String?,
    onBack: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        DetailTopBar(title, target, onBack)
        if (content != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(dimens.screenPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
                verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
                content = content,
            )
        }
    }
}

// 상태가 BackToMain이 되면 onReturnToMain을 한 번 부른다.
@Composable
internal fun ReturnToMainEffect(state: DetailUiState<*>, onReturnToMain: () -> Unit) {
    if (state is DetailUiState.BackToMain) {
        LaunchedEffect(Unit) { onReturnToMain() }
    }
}
