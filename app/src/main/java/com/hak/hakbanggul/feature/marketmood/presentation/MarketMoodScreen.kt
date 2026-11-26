package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.core.ui.asString
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage

// FRED API 약관이 앱에 두라고 요구하는 약관 링크
private const val FRED_TERMS_URL = "https://fred.stlouisfed.org/docs/api/terms_of_use.html"

@Composable
fun MarketMoodScreen(onOpenSettings: () -> Unit, viewModel: MarketMoodViewModel = hiltViewModel()) {
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
    )
}

@Composable
private fun MarketMoodContent(
    uiState: MarketMoodUiState,
    onRefreshClick: () -> Unit,
    onRetryClick: () -> Unit,
    onOpenSettings: () -> Unit,
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
                is MoodBody.Assessment -> AssessmentBody(body.mood)
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
        Divider()
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

// 누르는 영역 44, 둥근 물결이 퍼지는 아이콘 버튼
@Composable
private fun IconButtonBox(onClick: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(HakBanGgulTheme.dimens.iconButton)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// 돌아가는 진행 표시. 바탕 고리는 테두리 색, 도는 부분은 강조 색이다.
// size는 선 굵기까지 넣은 고리의 바깥 지름이다(CircularProgressIndicator는 크기 안에 선을 그린다).
@Composable
private fun Spinner(size: Dp, strokeWidth: Dp) {
    val colors = HakBanGgulTheme.colors
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = colors.accent,
        trackColor = colors.border,
        strokeWidth = strokeWidth,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
    )
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

// 판정

@Composable
private fun AssessmentBody(mood: MoodUiState) {
    AssessmentCard(mood)
    SectionTitle(stringResource(R.string.valuation_title), stringResource(R.string.valuation_description))
    ValuationCard(stringResource(R.string.price_position_name), mood.pricePosition)
    ValuationCard(stringResource(R.string.rate_burden_name), mood.rateBurden)
    SectionTitle(stringResource(R.string.mood_evidence_title), null)
    mood.groups.forEach { GroupCard(it) }
    Footer()
}

// 카드가 아닌 제목 덩어리. 바탕 없이 제목과 그 아래 한 줄(있을 때만)
@Composable
private fun SectionTitle(title: String, description: String?) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dimens.sectionTitlePaddingHorizontal,
                end = dimens.sectionTitlePaddingHorizontal,
                top = dimens.sectionTitlePaddingTop,
            ),
        verticalArrangement = Arrangement.spacedBy(dimens.nameGap),
    ) {
        Text(title, style = typography.bodyBold, color = colors.text)
        if (description != null) Text(description, style = typography.sectionDescription, color = colors.textMuted)
    }
}

// 매매 판단 카드: 머리(이름과 그 아래 글, 점수와 단계), 눈금, 안내 줄(있을 때만), 근거 줄
@Composable
private fun ValuationCard(name: String, card: ValuationCardUiState) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Card(padding = dimens.valuationCardPadding, gap = dimens.assessmentGap) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.rowContentGap),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.nameGap)) {
                Text(name, style = typography.groupName, color = colors.text)
                Text(card.subtitle.asString(), style = typography.caption, color = colors.textMuted)
            }
            Text(
                card.scoreText.asString(),
                style = typography.valuationScore,
                color = valuationStageColor(card.stage),
                maxLines = 1,
                softWrap = false,
            )
        }

        Gauge(
            position = card.gauge.position,
            segments = card.gauge.segments.zip(
                listOf(colors.valuationGaugeLow, colors.valuationGaugeMiddle, colors.valuationGaugeHigh),
            ),
            labels = card.gauge.labels.map { it.asString() },
        )

        card.notice?.let { Notice(it.asString()) }

        val borderColor = colors.border
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(borderColor, size = Size(size.width, dimens.divider.toPx())) }
                .padding(top = dimens.valuationEvidencePaddingTop),
            verticalArrangement = Arrangement.spacedBy(dimens.nameGap),
        ) {
            card.evidence.forEach { Text(it.asString(), style = typography.caption, color = colors.textMuted) }
        }
    }
}

// 회색 상자의 안내 줄
@Composable
private fun Notice(text: String) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    Text(
        text = text,
        style = HakBanGgulTheme.typography.captionNotice,
        color = colors.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background, HakBanGgulTheme.shapes.notice)
            .padding(horizontal = dimens.noticePaddingHorizontal, vertical = dimens.noticePaddingVertical),
    )
}

@Composable
private fun AssessmentCard(mood: MoodUiState) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Card(padding = dimens.cardPadding, gap = dimens.assessmentGap) {
        // 장세
        Column(verticalArrangement = Arrangement.spacedBy(dimens.nameGap)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.rowContentGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.mood_now),
                    style = typography.small,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                Text(
                    mood.asOfText.asString(),
                    style = typography.caption,
                    color = colors.textMuted,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(mood.stageName.asString(), style = typography.stageName, color = stageColor(mood.stage))
        }

        Divider()

        // 종합 점수: 점수는 점수 색과 굵은 글자, 단계 이름은 단계의 색이 아닌 굵은 글자
        val bold = SpanStyle(fontWeight = typography.bodyBold.fontWeight)
        val scoreColor = toneColor(mood.compositeTone)
        val label = stringResource(R.string.mood_composite_label)
        val separator = stringResource(R.string.mood_composite_separator)
        val computedName = mood.computedStageName.asString()
        Text(
            text = buildAnnotatedString {
                append(label)
                append(" ")
                withStyle(bold.copy(color = scoreColor)) { append(mood.compositeScoreText) }
                append(separator)
                withStyle(bold) { append(computedName) }
            },
            style = typography.body,
            color = colors.text,
        )

        Gauge(
            position = mood.gaugePosition,
            segments = listOf(colors.gaugeBear, colors.gaugeWeak, colors.gaugeNeutral, colors.gaugeBull).map { STAGE_SEGMENT to it },
            labels = listOf(R.string.stage_bear, R.string.stage_weak, R.string.stage_neutral, R.string.stage_bull).map { stringResource(it) },
        )

        mood.pendingNotice?.let { Notice(it.asString()) }
    }
}

// 종합 판정 카드의 눈금에서 칸 하나의 폭(네 칸이 같다)
private const val STAGE_SEGMENT = 0.25f

// 눈금: 칸(폭과 색)과 점수의 자리 표시. 칸마다 아래 가운데에 이름을 적는다.
// 종합 판정 카드는 물장, 약세, 중립, 불장의 폭이 같은 네 칸, 매매 판단 카드는 상태가 정한 폭의 세 칸이다.
@Composable
private fun Gauge(position: Float, segments: List<Pair<Float, Color>>, labels: List<String>) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val shapes = HakBanGgulTheme.shapes
    Column(verticalArrangement = Arrangement.spacedBy(dimens.gaugeLabelGap)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(dimens.gaugeHeight)) {
            val barTop = dimens.gaugeBarTop.toPx()
            val barHeight = dimens.gaugeBarHeight.toPx()
            val barRadius = shapes.gaugeBarRadius.toPx()

            // 양 끝이 둥근 막대 안에 칸을 칠한다.
            val bar = Path().apply {
                addRoundRect(RoundRect(0f, barTop, size.width, barTop + barHeight, CornerRadius(barRadius)))
            }
            clipPath(bar) {
                var left = 0f
                segments.forEach { (width, color) ->
                    val segmentWidth = size.width * width
                    drawRect(color, topLeft = Offset(left, barTop), size = Size(segmentWidth, barHeight))
                    left += segmentWidth
                }
            }

            // 자리 표시와 그 둘레의 흰 테
            val markerWidth = dimens.gaugeMarkerWidth.toPx()
            val outline = dimens.gaugeMarkerOutline.toPx()
            val markerRadius = shapes.gaugeMarkerRadius.toPx()
            val x = size.width * position - markerWidth / 2
            drawRoundRect(
                color = colors.card,
                topLeft = Offset(x - outline, -outline),
                size = Size(markerWidth + outline * 2, size.height + outline * 2),
                cornerRadius = CornerRadius(markerRadius + outline),
            )
            drawRoundRect(
                color = colors.text,
                topLeft = Offset(x, 0f),
                size = Size(markerWidth, size.height),
                cornerRadius = CornerRadius(markerRadius),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            segments.zip(labels).forEach { (segment, label) ->
                Text(
                    text = label,
                    style = HakBanGgulTheme.typography.gaugeLabel,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(segment.first),
                )
            }
        }
    }
}

@Composable
private fun GroupCard(group: GroupUiState) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardBackground()
            .padding(
                start = dimens.groupCardPadding,
                end = dimens.groupCardPadding,
                top = dimens.groupCardPadding,
                bottom = dimens.groupCardPaddingBottom,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = dimens.groupHeaderPaddingBottom),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.rowContentGap),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.nameGap)) {
                Text(group.name.asString(), style = typography.groupName, color = colors.text)
                Text(group.weightText.asString(), style = typography.caption, color = colors.textMuted)
            }
            Text(group.scoreText, style = typography.title, color = toneColor(group.scoreTone))
        }
        group.indicators.forEach { IndicatorRow(it) }
    }
}

// 항목 줄. 눌러도 아무 일도 일어나지 않는다.
@Composable
private fun IndicatorRow(indicator: IndicatorUiState) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    val name = indicator.name.asString()
    val suffix = indicator.suffix?.asString()
    LineRow(
        start = {
            Text(
                text = buildAnnotatedString {
                    append(name)
                    if (suffix != null) {
                        append(" ")
                        withStyle(typography.caption.toSpanStyle().copy(color = colors.textMuted)) { append(suffix) }
                    }
                },
                style = typography.bodyMedium,
                color = colors.text,
            )
            Text(indicator.evidence.asString(), style = typography.caption, color = colors.textMuted)
        },
        end = { Text(indicator.scoreText, style = typography.itemScore, color = toneColor(indicator.scoreTone)) },
        dividerBelow = false,
        gap = dimens.nameGap,
    )
}

@Composable
private fun Footer() {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.padding(
            start = dimens.footerPaddingHorizontal,
            end = dimens.footerPaddingHorizontal,
            top = dimens.footerPaddingTop,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.footerGap),
    ) {
        Text(stringResource(R.string.mood_disclaimer), style = typography.footer, color = colors.textMuted)
        Text(stringResource(R.string.mood_data_sources), style = typography.footer, color = colors.textMuted)
        Text(stringResource(R.string.fred_notice), style = typography.footer, color = colors.textMuted)
        Text(
            text = stringResource(R.string.fred_terms_link),
            style = typography.footerLink,
            color = colors.accent,
            modifier = Modifier
                .clickable(role = Role.Button) { uriHandler.openUri(FRED_TERMS_URL) }
                .padding(vertical = dimens.footerLinkPaddingVertical),
        )
    }
}

// 키 안내, 받는 중, 받지 못함

@Composable
private fun KeyGuideCard(rows: List<KeyGuideRow>, onOpenSettings: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Card(padding = dimens.cardPadding, gap = dimens.guideGap) {
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
    Card(padding = dimens.cardPadding, gap = dimens.guideGap) {
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

// 공용 모양

@Composable
private fun Card(padding: Dp, gap: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().cardBackground().padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
private fun Modifier.cardBackground(): Modifier {
    val colors = HakBanGgulTheme.colors
    val shape = HakBanGgulTheme.shapes.card
    return this
        .background(colors.card, shape)
        .border(HakBanGgulTheme.dimens.cardBorder, colors.border, shape)
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HakBanGgulTheme.dimens.divider)
            .background(HakBanGgulTheme.colors.border),
    )
}

// 위에 구분선이 있는 줄. 왼쪽에 이름과 그 아래 글, 오른쪽에 값
@Composable
private fun LineRow(
    start: @Composable ColumnScope.() -> Unit,
    end: @Composable () -> Unit,
    dividerBelow: Boolean,
    gap: Dp,
) {
    val dimens = HakBanGgulTheme.dimens
    val borderColor = HakBanGgulTheme.colors.border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val line = dimens.divider.toPx()
                drawRect(borderColor, size = Size(size.width, line))
                if (dividerBelow) drawRect(borderColor, topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
            }
            .padding(vertical = dimens.rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.rowContentGap),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap), content = start)
        end()
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

@Composable
private fun stageColor(stage: MarketStage): Color {
    val colors = HakBanGgulTheme.colors
    return when (stage) {
        MarketStage.BULL -> colors.stageBull
        MarketStage.NEUTRAL -> colors.stageNeutral
        MarketStage.WEAK -> colors.stageWeak
        MarketStage.BEAR -> colors.stageBear
    }
}

@Composable
private fun valuationStageColor(stage: ValuationStage): Color {
    val colors = HakBanGgulTheme.colors
    return when (stage) {
        ValuationStage.LOW -> colors.valuationLow
        ValuationStage.MIDDLE -> colors.valuationMiddle
        ValuationStage.HIGH -> colors.valuationHigh
    }
}

@Composable
private fun toneColor(tone: ScoreTone): Color {
    val colors = HakBanGgulTheme.colors
    return when (tone) {
        ScoreTone.POSITIVE -> colors.scorePositive
        ScoreTone.ZERO -> colors.scoreZero
        ScoreTone.NEGATIVE -> colors.scoreNegative
    }
}

// 미리보기. 숫자와 날짜는 시안의 예시를 옮긴 것이다.

private fun stageText(stage: MarketStage) = UiText(
    when (stage) {
        MarketStage.BULL -> R.string.stage_bull
        MarketStage.NEUTRAL -> R.string.stage_neutral
        MarketStage.WEAK -> R.string.stage_weak
        MarketStage.BEAR -> R.string.stage_bear
    },
)

private fun previewIndicator(name: Int, suffix: Int?, evidence: UiText, score: String, tone: ScoreTone) =
    IndicatorUiState(UiText(name), suffix?.let { UiText(it) }, evidence, score, tone)

private fun price(above: String, drawdown: String) = UiText(R.string.evidence_price, listOf(above, drawdown))

private fun previewMood(
    stage: MarketStage,
    computed: MarketStage = stage,
    composite: String,
    compositeTone: ScoreTone,
    gauge: Float,
    pending: UiText? = null,
    stockScore: String = "+0.50",
    stockTone: ScoreTone = ScoreTone.POSITIVE,
    month: Int = 11,
    day: Int = 23,
    pricePosition: ValuationCardUiState = previewPriceCard(),
    rateBurden: ValuationCardUiState = previewRateCard(),
) = MoodUiState(
    stage = stage,
    stageName = stageText(stage),
    asOfText = UiText(R.string.mood_as_of, listOf(month, day)),
    compositeScoreText = composite,
    compositeTone = compositeTone,
    computedStageName = stageText(computed),
    gaugePosition = gauge,
    pendingNotice = pending,
    groups = listOf(
        GroupUiState(
            name = UiText(R.string.group_stocks),
            weightText = UiText(R.string.group_weight, listOf(70)),
            scoreText = stockScore,
            scoreTone = stockTone,
            indicators = listOf(
                previewIndicator(R.string.indicator_broad_market, R.string.indicator_broad_market_suffix, price("+4.1%", "-3.2%"), "+1.0", ScoreTone.POSITIVE),
                previewIndicator(R.string.indicator_nasdaq_100, R.string.indicator_nasdaq_100_suffix, price("+3.0%", "-6.5%"), "+1.0", ScoreTone.POSITIVE),
                previewIndicator(R.string.indicator_technology, R.string.indicator_technology_suffix, price("+1.2%", "-11.8%"), "0.0", ScoreTone.ZERO),
                previewIndicator(R.string.indicator_semiconductors, R.string.indicator_semiconductors_suffix, price("-2.4%", "-17.5%"), "-0.5", ScoreTone.NEGATIVE),
            ),
        ),
        GroupUiState(
            name = UiText(R.string.group_bond_signals),
            weightText = UiText(R.string.group_weight, listOf(30)),
            scoreText = "+0.50",
            scoreTone = ScoreTone.POSITIVE,
            indicators = listOf(
                previewIndicator(
                    R.string.indicator_yield_curve,
                    R.string.indicator_yield_curve_suffix,
                    UiText(R.string.evidence_value, listOf("+0.29%p")),
                    "0.0",
                    ScoreTone.ZERO,
                ),
                previewIndicator(
                    R.string.indicator_high_yield_spread,
                    null,
                    UiText(R.string.evidence_value_with_date, listOf("3.76%", 11, 20)),
                    "+1.0",
                    ScoreTone.POSITIVE,
                ),
            ),
        ),
    ),
    pricePosition = pricePosition,
    rateBurden = rateBurden,
)

private val priceStageNames = listOf(R.string.valuation_price_low, R.string.valuation_middle, R.string.valuation_price_high)
private val rateStageNames = listOf(R.string.valuation_rate_low, R.string.valuation_middle, R.string.valuation_rate_high)

private fun previewCard(
    names: List<Int>,
    subtitle: UiText,
    score: Int,
    stage: ValuationStage,
    notice: UiText?,
    evidence: List<UiText>,
) = ValuationCardUiState(
    subtitle = subtitle,
    scoreText = UiText(R.string.valuation_score, listOf(score, UiText(names[stage.ordinal]))),
    stage = stage,
    gauge = ValuationGaugeUiState(listOf(0.31f, 0.38f, 0.31f), names.map { UiText(it) }, score / 100f),
    notice = notice,
    evidence = evidence,
)

private fun previewPriceCard(
    score: Int = 74,
    stage: ValuationStage = ValuationStage.HIGH,
    short: String = "+4.1%",
    long: String = "+21.4%",
    drawdown: String = "-3.2%",
    threeMonth: String = "+5.8%",
    notice: UiText? = null,
) = previewCard(
    names = priceStageNames,
    subtitle = UiText(R.string.price_position_subtitle),
    score = score,
    stage = stage,
    notice = notice,
    evidence = listOf(
        UiText(R.string.price_evidence_average, listOf(short, long)),
        UiText(R.string.price_evidence_drawdown, listOf(drawdown, threeMonth)),
    ),
)

private fun previewRateCard(
    realYield: Any = "2.79%",
    score: Int = 98,
    stage: ValuationStage = ValuationStage.HIGH,
    yearLine: UiText = UiText(R.string.rate_evidence_year_change, listOf("+0.97%p", UiText(R.string.rate_evidence_rising))),
    treasury: Any = "4.92%",
    policy: Any = "3.88%",
    inflation: String = "4.1%",
    gap: String = "+2.1%p",
    notice: UiText? = null,
) = previewCard(
    names = rateStageNames,
    subtitle = UiText(R.string.rate_burden_subtitle, listOf(realYield)),
    score = score,
    stage = stage,
    notice = notice,
    evidence = listOf(
        yearLine,
        UiText(R.string.rate_evidence_rates, listOf(treasury, policy)),
        UiText(R.string.rate_evidence_inflation, listOf(inflation, "2", gap, 10)),
    ),
)

private fun falling(change: String) = UiText(R.string.rate_evidence_year_change, listOf(change, UiText(R.string.rate_evidence_falling)))

private fun withDate(value: String, month: Int, day: Int) = UiText(R.string.evidence_value_with_date, listOf(value, month, day))

@Composable
private fun Preview(state: MarketMoodUiState) {
    HakBanGgulTheme {
        MarketMoodContent(uiState = state, onRefreshClick = {}, onRetryClick = {}, onOpenSettings = {})
    }
}

private fun assessmentState(mood: MoodUiState, banner: BannerUiState? = null) =
    MarketMoodUiState(RefreshButtonState.ENABLED, banner, MoodBody.Assessment(mood))

@Preview(name = "판정이 나왔을 때 (불장)", widthDp = 390, heightDp = 1670)
@Composable
private fun AssessmentBullPreview() = Preview(
    assessmentState(previewMood(MarketStage.BULL, composite = "+0.65", compositeTone = ScoreTone.POSITIVE, gauge = 0.825f)),
)

@Preview(name = "중립인 날", widthDp = 390, heightDp = 1670)
@Composable
private fun AssessmentNeutralPreview() = Preview(
    assessmentState(
        previewMood(
            MarketStage.NEUTRAL,
            composite = "+0.33",
            compositeTone = ScoreTone.POSITIVE,
            gauge = 0.6625f,
            stockScore = "+0.25",
            pricePosition = previewPriceCard(58, ValuationStage.MIDDLE, "+2.3%", "+17.2%", "-4.0%", "+1.9%"),
            rateBurden = previewRateCard(
                realYield = "1.64%",
                score = 52,
                stage = ValuationStage.MIDDLE,
                yearLine = falling("-0.42%p"),
                treasury = "3.95%",
                policy = "4.33%",
                inflation = "2.6%",
                gap = "+0.6%p",
            ),
        ),
    ),
)

@Preview(name = "물장인 날", widthDp = 390, heightDp = 1670)
@Composable
private fun AssessmentBearPreview() = Preview(
    assessmentState(
        previewMood(
            MarketStage.BEAR,
            composite = "-0.65",
            compositeTone = ScoreTone.NEGATIVE,
            gauge = 0.175f,
            stockScore = "-0.75",
            stockTone = ScoreTone.NEGATIVE,
            pricePosition = previewPriceCard(6, ValuationStage.LOW, "-8.4%", "-3.5%", "-24.1%", "-15.7%"),
            rateBurden = previewRateCard(
                realYield = "0.85%",
                score = 22,
                stage = ValuationStage.LOW,
                yearLine = falling("-0.71%p"),
                treasury = "3.10%",
                policy = "2.50%",
                inflation = "1.8%",
                gap = "-0.2%p",
            ),
        ),
    ),
)

@Preview(name = "장세와 종합 점수가 다른 날", widthDp = 390, heightDp = 1730)
@Composable
private fun AssessmentPendingPreview() = Preview(
    assessmentState(
        previewMood(
            MarketStage.BULL,
            computed = MarketStage.NEUTRAL,
            composite = "+0.41",
            compositeTone = ScoreTone.POSITIVE,
            gauge = 0.706f,
            pending = UiText(R.string.mood_pending_notice, listOf(stageText(MarketStage.NEUTRAL), stageText(MarketStage.BULL), 3, 1)),
            stockScore = "+0.38",
        ),
    ),
)

@Preview(name = "새로 받지 못했을 때 (약세)", widthDp = 390, heightDp = 1760)
@Composable
private fun AssessmentStalePreview() = Preview(
    assessmentState(
        previewMood(
            MarketStage.WEAK,
            composite = "-0.20",
            compositeTone = ScoreTone.NEGATIVE,
            gauge = 0.40f,
            stockScore = "-0.50",
            stockTone = ScoreTone.NEGATIVE,
            day = 18,
            pricePosition = previewPriceCard(41, ValuationStage.MIDDLE, "-1.2%", "+14.0%", "-8.9%", "-3.4%"),
        ),
        banner = BannerUiState(UiText(R.string.banner_stale_title), UiText(R.string.banner_no_connection_message)),
    ),
)

@Preview(name = "매매 판단의 숫자와 단계가 다른 날", widthDp = 390, heightDp = 1790)
@Composable
private fun ValuationBufferPreview() = Preview(
    assessmentState(
        previewMood(
            MarketStage.NEUTRAL,
            composite = "+0.33",
            compositeTone = ScoreTone.POSITIVE,
            gauge = 0.6625f,
            stockScore = "+0.25",
            pricePosition = previewPriceCard(
                33,
                ValuationStage.LOW,
                "+0.6%",
                "+9.8%",
                "-6.1%",
                "-0.8%",
                notice = UiText(R.string.valuation_notice_low, listOf(33, UiText(R.string.valuation_price_low), 36)),
            ),
            rateBurden = previewRateCard(
                realYield = "2.05%",
                score = 67,
                stage = ValuationStage.HIGH,
                yearLine = falling("-0.18%p"),
                treasury = "4.30%",
                policy = "4.33%",
                inflation = "2.9%",
                gap = "+0.9%p",
                notice = UiText(R.string.valuation_notice_high, listOf(67, UiText(R.string.valuation_rate_high), 64)),
            ),
        ),
    ),
)

@Preview(name = "금리 값의 날짜가 다르고 1년 전과 같은 날", widthDp = 390, heightDp = 1730)
@Composable
private fun ValuationRateDatePreview() = Preview(
    assessmentState(
        previewMood(
            MarketStage.BULL,
            composite = "+0.65",
            compositeTone = ScoreTone.POSITIVE,
            gauge = 0.825f,
            rateBurden = previewRateCard(
                realYield = withDate("2.79%", 11, 20),
                yearLine = UiText(R.string.rate_evidence_year_same),
                treasury = withDate("4.92%", 11, 20),
                policy = withDate("3.88%", 11, 20),
            ),
        ),
    ),
)

@Preview(name = "키가 없을 때", widthDp = 390, heightDp = 844)
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

@Preview(name = "받는 중", widthDp = 390, heightDp = 844)
@Composable
private fun LoadingPreview() = Preview(MarketMoodUiState(RefreshButtonState.REFRESHING, null, MoodBody.Loading))

@Preview(name = "한 번도 받지 못했을 때", widthDp = 390, heightDp = 844)
@Composable
private fun FailedPreview() = Preview(
    MarketMoodUiState(RefreshButtonState.ENABLED, null, MoodBody.Failed(UiText(R.string.mood_failed_no_connection))),
)
