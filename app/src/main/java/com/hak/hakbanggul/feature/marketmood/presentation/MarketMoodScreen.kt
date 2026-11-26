package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
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
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.core.ui.asString
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.noticeHigh
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.noticeLow
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.pending
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.priceCard
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.rateCard
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.regimeCard
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.sectorRows

@Composable
fun MarketMoodScreen(
    onOpenSettings: () -> Unit,
    onOpenRegime: (targetId: String) -> Unit,
    onOpenPricePosition: (targetId: String) -> Unit,
    onOpenRateBurden: () -> Unit,
    onOpenSector: (targetId: String) -> Unit,
    viewModel: MarketMoodViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    // 화면이 보이게 될 때(ON_START) 알린다. 화면 회전으로 다시 만들어질 때는 알리지 않는다.
    // 회전 전 ON_STOP에서 구성 변경인지 적어 두고, 그 값은 회전을 넘어 남는다.
    var stoppedForConfigChange by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        stoppedForConfigChange = activity?.isChangingConfigurations == true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (!stoppedForConfigChange) viewModel.onScreenShown()
        stoppedForConfigChange = false
    }

    MarketMoodContent(
        uiState = uiState,
        onRefreshClick = viewModel::onRefreshClick,
        onRetryClick = viewModel::onRetryClick,
        onOpenSettings = onOpenSettings,
        onOpenRegime = onOpenRegime,
        onOpenPricePosition = onOpenPricePosition,
        onOpenRateBurden = onOpenRateBurden,
        onOpenSector = onOpenSector,
    )
}

@Composable
private fun MarketMoodContent(
    uiState: MarketMoodUiState,
    onRefreshClick: () -> Unit,
    onRetryClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRegime: (String) -> Unit = {},
    onOpenPricePosition: (String) -> Unit = {},
    onOpenRateBurden: () -> Unit = {},
    onOpenSector: (String) -> Unit = {},
) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        TopBar(uiState.refreshButton, onRefreshClick, onOpenSettings)

        // 위쪽 줄은 제자리에 두고 그 아래만 위아래로 밀어 본다.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(dimens.screenPadding)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            uiState.banner?.let { Banner(it, onRetryClick) }
            when (val body = uiState.body) {
                MoodBody.Undecided -> Unit
                is MoodBody.KeyGuide -> KeyGuideCard(body.rows, onOpenSettings)
                is MoodBody.Assessment -> AssessmentBody(body.mood, onOpenRegime, onOpenPricePosition, onOpenRateBurden, onOpenSector)
                MoodBody.Loading -> LoadingCard()
                is MoodBody.Failed -> FailedCard(body.message, onRetryClick)
            }
        }
    }
}

// 위쪽 줄

@Composable
private fun TopBar(refreshButton: RefreshButtonState, onRefreshClick: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    // 상태 표시줄 자리도 위쪽 줄의 바탕으로 칠한다.
    Column(modifier = Modifier.background(colors.card).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.topBarHeight)
                .padding(start = dimens.topBarPaddingStart, end = dimens.topBarPaddingEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = HakBanGgulTheme.typography.title,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            RefreshButton(refreshButton, onRefreshClick)
            MenuButton(onOpenSettings)
        }
        MoodDivider()
    }
}

@Composable
private fun RefreshButton(state: RefreshButtonState, onClick: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    if (state == RefreshButtonState.REFRESHING) {
        val description = stringResource(R.string.mood_loading)
        Box(
            modifier = Modifier.size(dimens.iconButton).semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Spinner(dimens.progressTopBar, dimens.progressTopBarStroke)
        }
    } else {
        val enabled = state == RefreshButtonState.ENABLED
        IconButtonBox(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = AppIcons.Refresh,
                contentDescription = stringResource(R.string.action_refresh),
                tint = if (enabled) colors.text else colors.disabled,
                modifier = Modifier.size(dimens.icon),
            )
        }
    }
}

@Composable
private fun MenuButton(onOpenSettings: () -> Unit) {
    val dimens = HakBanGgulTheme.dimens
    // 메뉴가 열렸는지는 화면 안의 상태로 둔다.
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButtonBox(onClick = { expanded = true }, enabled = true) {
            Icon(
                imageVector = AppIcons.Menu,
                contentDescription = stringResource(R.string.action_menu),
                tint = HakBanGgulTheme.colors.text,
                modifier = Modifier.size(dimens.icon),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_settings)) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
        }
    }
}

// 안내 띠

@Composable
private fun Banner(banner: BannerUiState, onRetryClick: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warningBackground, HakBanGgulTheme.shapes.banner)
            .padding(
                start = dimens.bannerPaddingStart,
                end = dimens.bannerPaddingEnd,
                top = dimens.bannerPaddingVertical,
                bottom = dimens.bannerPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.bannerGap),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.nameGap)) {
            Text(banner.title.asString(), style = typography.smallBold, color = colors.warningText)
            Text(banner.message.asString(), style = typography.captionBanner, color = colors.warningText)
        }
        Box(
            modifier = Modifier
                .heightIn(min = dimens.textButtonHeight)
                .clip(HakBanGgulTheme.shapes.button)
                .clickable(role = Role.Button, onClick = onRetryClick)
                .padding(horizontal = dimens.textButtonPaddingHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.action_retry), style = typography.smallBold, color = colors.warningText)
        }
    }
}

// 판정: 전체 장세 카드, 매매 판단 제목과 두 카드, 섹터별 제목과 섹터 목록, 맨 아래 안내

@Composable
private fun AssessmentBody(
    mood: MoodUiState,
    onOpenRegime: (String) -> Unit,
    onOpenPricePosition: (String) -> Unit,
    onOpenRateBurden: () -> Unit,
    onOpenSector: (String) -> Unit,
) {
    RegimeCard(mood.regime) { onOpenRegime(mood.broadMarketId) }
    SectionTitle(mood.valuationTitle.asString(), mood.valuationDescription.asString())
    ValuationCard(mood.pricePosition) { onOpenPricePosition(mood.broadMarketId) }
    ValuationCard(mood.rateBurden, onOpenRateBurden)
    SectionTitle(stringResource(R.string.sectors_title), stringResource(R.string.sectors_description))
    SectorListCard(mood.sectorHeader, mood.sectors, onOpenSector)
    MoodFooter()
}

// 키 안내, 받는 중, 받지 못함

@Composable
private fun KeyGuideCard(rows: List<KeyGuideRow>, onOpenSettings: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    MoodCard(padding = PaddingValues(dimens.cardPadding), gap = dimens.guideGap) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.guideTitleGap)) {
            Text(stringResource(R.string.key_guide_title), style = typography.title, color = colors.text)
            Text(stringResource(R.string.key_guide_description), style = typography.smallLoose, color = colors.textMuted)
        }
        Column {
            rows.forEachIndexed { index, row ->
                LineRow(
                    start = {
                        Text(stringResource(row.nameRes), style = typography.bodyMedium, color = colors.text)
                        Text(stringResource(row.descriptionRes), style = typography.caption, color = colors.textMuted)
                    },
                    end = { KeyStatusBadge(row.badge) },
                    dividerBelow = index == rows.lastIndex,
                    gap = dimens.nameGap,
                )
            }
        }
        FilledButton(stringResource(R.string.key_guide_open_settings), onOpenSettings)
    }
}

@Composable
private fun LoadingCard() {
    val dimens = HakBanGgulTheme.dimens
    Row(
        modifier = Modifier.fillMaxWidth().cardBackground().padding(dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.progressTextGap),
    ) {
        Box(modifier = Modifier.size(dimens.progressCardSlot), contentAlignment = Alignment.Center) {
            Spinner(dimens.progressCard, dimens.progressCardStroke)
        }
        Text(stringResource(R.string.mood_loading), style = HakBanGgulTheme.typography.title, color = HakBanGgulTheme.colors.text)
    }
}

@Composable
private fun FailedCard(message: UiText, onRetryClick: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    MoodCard(padding = PaddingValues(dimens.cardPadding), gap = dimens.guideGap) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.guideTitleGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.iconTextGap)) {
                Icon(
                    imageVector = AppIcons.Alert,
                    contentDescription = null,
                    tint = colors.warningText,
                    modifier = Modifier.size(dimens.icon),
                )
                Text(stringResource(R.string.mood_failed_title), style = typography.title, color = colors.text)
            }
            Text(message.asString(), style = typography.smallLoose, color = colors.textMuted)
        }
        FilledButton(stringResource(R.string.action_retry), onRetryClick)
    }
}

@Composable
private fun FilledButton(text: String, onClick: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(HakBanGgulTheme.dimens.filledButtonHeight),
        shape = HakBanGgulTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
    ) {
        Text(text, style = HakBanGgulTheme.typography.bodyBold)
    }
}

// 미리보기. 숫자와 날짜는 시안의 예시를 옮긴 것이다.

@Composable
private fun Preview(state: MarketMoodUiState) {
    HakBanGgulTheme {
        MarketMoodContent(uiState = state, onRefreshClick = {}, onRetryClick = {}, onOpenSettings = {})
    }
}

private fun assessmentState(
    mood: MoodUiState,
    banner: BannerUiState? = null,
    refreshButton: RefreshButtonState = RefreshButtonState.ENABLED,
) = MarketMoodUiState(refreshButton, banner, MoodBody.Assessment(mood))

@Preview(name = "시장 분위기 · 둘째 묶음: 첫 화면 (전체 장세, 섹터별)", widthDp = 390, heightDp = 1900)
@Composable
private fun SectorsPreview() = Preview(assessmentState(MoodPreviewData.mood()))

@Preview(name = "첫 화면 · 장세와 종합 점수가 다른 날, 주가 위치의 숫자와 단계가 다른 날", widthDp = 390, heightDp = 2000)
@Composable
private fun SectorsPendingPreview() = Preview(
    assessmentState(
        MoodPreviewData.mood(
            regime = regimeCard(
                computed = MarketStage.NEUTRAL,
                score = "+0.15",
                tone = ScoreTone.POSITIVE,
                gauge = 0.575f,
                pending = pending(MarketStage.NEUTRAL, MarketStage.WEAK, 1),
            ),
            price = priceCard(score = 33, notice = noticeLow(true, 33)),
        ),
    ),
)

@Preview(name = "첫 화면 · 처음 받는 중 (섹터 일부를 아직 받는 중)", widthDp = 390, heightDp = 1820)
@Composable
private fun SectorsLoadingPreview() = Preview(
    assessmentState(
        MoodPreviewData.mood(sectors = sectorRows(statusFrom = 6, statusRes = R.string.sector_loading)),
        refreshButton = RefreshButtonState.REFRESHING,
    ),
)

@Preview(name = "첫 화면 · 섹터 일부를 받지 못했을 때 (받은 적 없는 섹터, 전에 받은 값이 남은 섹터)", widthDp = 390, heightDp = 1900)
@Composable
private fun SectorsFailedPreview() = Preview(
    assessmentState(
        MoodPreviewData.mood(sectors = sectorRows(statusFrom = 6, statusRes = R.string.sector_failed, datedLast = true)),
        banner = BannerUiState(UiText(R.string.banner_rate_limited_title), UiText(R.string.banner_rate_limited_message)),
    ),
)

@Preview(name = "시장 분위기 · 중립인 날", widthDp = 390, heightDp = 1900)
@Composable
private fun NeutralPreview() = Preview(
    assessmentState(
        MoodPreviewData.mood(
            regime = regimeCard(stage = MarketStage.NEUTRAL, score = "+0.33", tone = ScoreTone.POSITIVE, gauge = 0.6625f),
            price = priceCard(score = 58, stage = ValuationStage.MIDDLE),
            rate = rateCard(realYield = "1.64%", score = 52, stage = ValuationStage.MIDDLE),
        ),
    ),
)

@Preview(name = "시장 분위기 · 물장인 날", widthDp = 390, heightDp = 1900)
@Composable
private fun BearPreview() = Preview(
    assessmentState(
        MoodPreviewData.mood(
            regime = regimeCard(stage = MarketStage.BEAR, score = "-0.65", gauge = 0.175f),
            price = priceCard(score = 6),
            rate = rateCard(realYield = "0.85%", score = 22, stage = ValuationStage.LOW),
        ),
    ),
)

@Preview(name = "시장 분위기 · 금리 값의 날짜가 다르고 1년 전과 같은 날", widthDp = 390, heightDp = 1900)
@Composable
private fun RateDatePreview() = Preview(
    assessmentState(MoodPreviewData.mood(rate = rateCard(realYield = MoodPreviewData.withDate("2.79%", 11, 20)))),
)

@Preview(name = "시장 분위기 · 매매 판단의 숫자와 단계가 다른 날", widthDp = 390, heightDp = 1960)
@Composable
private fun BufferPreview() = Preview(
    assessmentState(
        MoodPreviewData.mood(
            price = priceCard(score = 33, notice = noticeLow(true, 33)),
            rate = rateCard(realYield = "2.05%", score = 67, notice = noticeHigh(false, 67)),
        ),
    ),
)

@Preview(name = "시장 분위기 · 키가 없을 때", widthDp = 390, heightDp = 844)
@Composable
private fun KeyGuidePreview() = Preview(
    MarketMoodUiState(
        RefreshButtonState.DISABLED,
        null,
        MoodBody.KeyGuide(
            listOf(
                KeyGuideRow(
                    DataSource.TWELVE_DATA,
                    SourceLabels.nameRes(DataSource.TWELVE_DATA),
                    SourceLabels.descriptionRes(DataSource.TWELVE_DATA),
                    KeyBadgeKind.NONE,
                ),
                KeyGuideRow(
                    DataSource.FRED,
                    SourceLabels.nameRes(DataSource.FRED),
                    SourceLabels.descriptionRes(DataSource.FRED),
                    KeyBadgeKind.SAVED,
                ),
            ),
        ),
    ),
)

@Preview(name = "시장 분위기 · 받는 중", widthDp = 390, heightDp = 844)
@Composable
private fun LoadingPreview() = Preview(MarketMoodUiState(RefreshButtonState.REFRESHING, null, MoodBody.Loading))

@Preview(name = "시장 분위기 · 한 번도 받지 못했을 때", widthDp = 390, heightDp = 844)
@Composable
private fun FailedPreview() = Preview(
    MarketMoodUiState(RefreshButtonState.ENABLED, null, MoodBody.Failed(UiText(R.string.mood_failed_no_connection))),
)
