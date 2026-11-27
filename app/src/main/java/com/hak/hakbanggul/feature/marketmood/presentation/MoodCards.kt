package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.AppIcons
import com.hak.hakbanggul.core.ui.asString
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage

// 화면 다섯(첫 화면, 장세, 주가 위치, 금리 부담, 섹터)이 함께 그리는 카드와 조각.
// 받은 상태를 그리기만 하고 글을 고르거나 숫자를 바꾸지 않는다.

// FRED API 약관이 앱에 두라고 요구하는 약관 링크
private const val FRED_TERMS_URL = "https://fred.stlouisfed.org/docs/api/terms_of_use.html"

// 장세 눈금에서 칸 하나의 폭(네 칸이 같다)
private const val STAGE_SEGMENT = 0.25f

// 장세 카드: 맨 위 줄(이름 · 기간, 기준 날짜), 큰 단계 이름, 구분선, 종합 점수 줄, 눈금, 안내 줄(있을 때만), 링크 줄(showLink일 때)
@Composable
internal fun RegimeCard(card: RegimeCardUiState, onLinkClick: () -> Unit = {}) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    MoodCard(
        padding = PaddingValues(
            start = dimens.cardPadding,
            end = dimens.cardPadding,
            top = dimens.cardPadding,
            bottom = if (card.showLink) dimens.regimeCardLinkPaddingBottom else dimens.cardPadding,
        ),
        gap = dimens.assessmentGap,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.nameGap)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.rowContentGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    card.title.asString(),
                    style = typography.small,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                Text(
                    card.asOfText.asString(),
                    style = typography.caption,
                    color = colors.textMuted,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(card.stageName.asString(), style = typography.stageName, color = stageColor(card.stage))
        }

        MoodDivider()

        // 종합 점수: 점수는 점수 색과 굵은 글자, 단계 이름은 단계의 색이 아닌 굵은 글자
        val bold = SpanStyle(fontWeight = typography.bodyBold.fontWeight)
        val scoreColor = toneColor(card.compositeTone)
        val label = stringResource(R.string.mood_composite_label)
        val separator = stringResource(R.string.mood_composite_separator)
        val computedName = card.computedStageName.asString()
        Text(
            text = buildAnnotatedString {
                append(label)
                append(" ")
                withStyle(bold.copy(color = scoreColor)) { append(card.compositeScoreText) }
                append(separator)
                withStyle(bold) { append(computedName) }
            },
            style = typography.body,
            color = colors.text,
        )

        StageGauge(card.gaugePosition)

        card.pendingNotice?.let { Notice(it.asString()) }

        if (card.showLink) LinkRow(onLinkClick)
    }
}

// 매매 판단 카드: 머리(이름과 그 아래 글, 점수와 단계), 눈금, 안내 줄(있을 때만), 근거 줄(있을 때만), 링크 줄(showLink일 때)
@Composable
internal fun ValuationCard(card: ValuationCardUiState, onLinkClick: () -> Unit = {}) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    MoodCard(
        padding = PaddingValues(
            start = dimens.valuationCardPadding,
            end = dimens.valuationCardPadding,
            top = dimens.valuationCardPadding,
            bottom = if (card.showLink) dimens.valuationCardLinkPaddingBottom else dimens.valuationCardPadding,
        ),
        gap = dimens.assessmentGap,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.rowContentGap),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.nameGap)) {
                Text(card.name.asString(), style = typography.groupName, color = colors.text)
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

        ValuationGauge(card.gauge)

        card.notice?.let { Notice(it.asString()) }

        if (card.evidence.isNotEmpty()) EvidenceLines(card.evidence.map { it.asString() })

        if (card.showLink) LinkRow(onLinkClick)
    }
}

// 링크 줄: 위 구분선, 왼쪽 "근거와 다른 기간", 오른쪽 "›". 줄 전체를 누를 수 있다.
@Composable
internal fun LinkRow(onClick: () -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val borderColor = colors.border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.linkRowHeight)
            .drawBehind { drawRect(borderColor, size = Size(size.width, dimens.divider.toPx())) }
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.link_evidence_and_periods), style = HakBanGgulTheme.typography.link, color = colors.accent)
        Icon(
            imageVector = AppIcons.Chevron,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(dimens.linkArrow),
        )
    }
}

// 다른 기간 카드: 머리(이름과 기간 글, 오른쪽 값), 그 아래는 종류마다 다르다.
@Composable
internal fun OtherPeriodCard(card: OtherPeriodCardUiState) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    MoodCard(
        padding = PaddingValues(horizontal = dimens.otherCardPaddingHorizontal, vertical = dimens.otherCardPaddingVertical),
        gap = dimens.otherCardGap,
    ) {
        val periodText = card.periodText.asString()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.rowContentGap),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.otherCardNameGap)) {
                Text(card.name.asString(), style = typography.bodyBold, color = colors.text)
                when (card) {
                    is OtherPeriodCardUiState.Regime -> {
                        // "최근 6개월 흐름 · 종합 점수 +0.15". 점수는 점수의 색으로 적는다.
                        val separator = stringResource(R.string.mood_composite_separator)
                        val label = stringResource(R.string.mood_composite_label)
                        val scoreColor = toneColor(card.compositeTone)
                        Text(
                            text = buildAnnotatedString {
                                append(periodText)
                                append(separator)
                                append(label)
                                append(" ")
                                withStyle(SpanStyle(color = scoreColor)) { append(card.compositeScoreText) }
                            },
                            style = typography.sectionDescription,
                            color = colors.textMuted,
                        )
                    }
                    else -> Text(periodText, style = typography.sectionDescription, color = colors.textMuted)
                }
            }
            when (card) {
                is OtherPeriodCardUiState.Regime -> Text(
                    card.stageName.asString(),
                    style = typography.otherPeriodScore,
                    color = stageColor(card.stage),
                    maxLines = 1,
                    softWrap = false,
                )
                is OtherPeriodCardUiState.Valuation -> Text(
                    card.scoreText.asString(),
                    style = typography.otherPeriodScore,
                    color = valuationStageColor(card.stage),
                    maxLines = 1,
                    softWrap = false,
                )
                is OtherPeriodCardUiState.Unavailable -> Text(
                    stringResource(R.string.period_unavailable),
                    style = typography.bodyBold,
                    color = colors.textMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        when (card) {
            is OtherPeriodCardUiState.Regime -> {
                StageGauge(card.gaugePosition)
                card.pendingNotice?.let { Notice(it.asString()) }
                EvidenceLines(card.evidence.map { it.asString() })
            }
            is OtherPeriodCardUiState.Valuation -> {
                ValuationGauge(card.gauge)
                card.notice?.let { Notice(it.asString()) }
            }
            is OtherPeriodCardUiState.Unavailable ->
                Text(card.message.asString(), style = typography.captionBanner, color = colors.textMuted)
        }
    }
}

// 다른 기간 카드들(사이 8)
@Composable
internal fun OtherPeriodCards(cards: List<OtherPeriodCardUiState>) {
    Column(verticalArrangement = Arrangement.spacedBy(HakBanGgulTheme.dimens.otherCardsGap)) {
        cards.forEach { OtherPeriodCard(it) }
    }
}

// 근거 줄: 위에 구분선, 그 아래 여백 10, 13 textMuted
@Composable
private fun EvidenceLines(lines: List<String>) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val borderColor = colors.border
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(borderColor, size = Size(size.width, dimens.divider.toPx())) }
            .padding(top = dimens.valuationEvidencePaddingTop),
        verticalArrangement = Arrangement.spacedBy(dimens.nameGap),
    ) {
        lines.forEach { Text(it, style = HakBanGgulTheme.typography.caption, color = colors.textMuted) }
    }
}

// 섹터 목록 카드: 머리 줄과 섹터 줄들
@Composable
internal fun SectorListCard(header: SectorHeaderUiState, rows: List<SectorRowUiState>, onRowClick: (String) -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardBackground()
            .padding(
                start = dimens.sectorCardPaddingHorizontal,
                end = dimens.sectorCardPaddingHorizontal,
                top = dimens.sectorCardPaddingTop,
                bottom = dimens.sectorCardPaddingBottom,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = dimens.sectorHeaderPaddingBottom),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.sectorColumnGap),
        ) {
            Text(header.nameText.asString(), style = typography.sectionDescription, color = colors.textMuted, modifier = Modifier.weight(1f))
            Text(
                header.regimeText.asString(),
                style = typography.sectionDescription,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(dimens.sectorRegimeWidth),
            )
            Text(
                header.pricePositionText.asString(),
                style = typography.sectionDescription,
                color = colors.textMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.width(dimens.sectorPriceWidth),
            )
            Spacer(modifier = Modifier.width(dimens.sectorArrow))
        }
        rows.forEach { SectorRow(it, onRowClick) }
    }
}

// 섹터 줄: 위에 구분선, 왼쪽에 이름과 ETF 기호(기준 날짜 줄이 있으면 그 아래), 값 두 칸과 "›" 또는 상태 글 하나
@Composable
private fun SectorRow(row: SectorRowUiState, onClick: (String) -> Unit) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    val borderColor = colors.border
    val name = row.name.asString()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.sectorRowMinHeight)
            .drawBehind { drawRect(borderColor, size = Size(size.width, dimens.divider.toPx())) }
            .then(if (row.clickable) Modifier.clickable(role = Role.Button) { onClick(row.targetId) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.sectorColumnGap),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.sectorNameDateGap)) {
            Text(
                text = buildAnnotatedString {
                    append(name)
                    append(" ")
                    withStyle(typography.sectionDescription.toSpanStyle().copy(color = colors.textMuted)) { append(row.symbol) }
                },
                style = typography.bodyMedium,
                color = colors.text,
            )
            row.asOfText?.let { Text(it.asString(), style = typography.sectionDescription, color = colors.textMuted) }
            // 받는 중이면 이름 줄 아래에 작은 진행 표시와 그 글을 한 줄로 둔다(기준 날짜 줄과 함께 오지 않는다).
            row.refreshingText?.let { text ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.progressSectorTextGap),
                ) {
                    Box(modifier = Modifier.size(dimens.progressSectorSlot), contentAlignment = Alignment.Center) {
                        Spinner(dimens.progressSector, dimens.progressSectorStroke)
                    }
                    Text(
                        text.asString(),
                        style = typography.sectionDescription,
                        color = colors.textMuted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        when (val content = row.content) {
            is SectorRowContent.Values -> {
                Text(
                    content.regimeStageName.asString(),
                    style = typography.bodyBold,
                    color = stageColor(content.regimeStage),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.width(dimens.sectorRegimeWidth),
                )
                Text(
                    content.valuationText.asString(),
                    style = typography.bodyBold,
                    color = valuationStageColor(content.valuationStage),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(dimens.sectorPriceWidth),
                )
                Icon(
                    imageVector = AppIcons.Chevron,
                    contentDescription = null,
                    tint = colors.sectorChevron,
                    modifier = Modifier.size(dimens.sectorArrow),
                )
            }
            is SectorRowContent.Status -> Text(
                content.text.asString(),
                style = typography.small,
                color = colors.textMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.width(dimens.sectorStatusWidth),
            )
        }
    }
}

// 묶음 카드: 머리(이름과 덧붙이는 글, 묶음 점수)와 항목 줄들
@Composable
internal fun GroupCard(group: GroupUiState) {
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

// 카드가 아닌 제목 덩어리. 바탕 없이 제목과 그 아래 한 줄(있을 때만)
@Composable
internal fun SectionTitle(title: String, description: String?) {
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

// 회색 상자의 안내 줄
@Composable
internal fun Notice(text: String) {
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

// 장세 눈금: 물장, 약세, 중립, 불장의 폭이 같은 네 칸
@Composable
internal fun StageGauge(position: Float) {
    val colors = HakBanGgulTheme.colors
    Gauge(
        position = position,
        segments = listOf(colors.gaugeBear, colors.gaugeWeak, colors.gaugeNeutral, colors.gaugeBull).map { STAGE_SEGMENT to it },
        labels = listOf(R.string.stage_bear, R.string.stage_weak, R.string.stage_neutral, R.string.stage_bull).map { stringResource(it) },
    )
}

// 매매 판단 눈금: 상태가 정한 폭의 세 칸
@Composable
internal fun ValuationGauge(gauge: ValuationGaugeUiState) {
    val colors = HakBanGgulTheme.colors
    Gauge(
        position = gauge.position,
        segments = gauge.segments.zip(listOf(colors.valuationGaugeLow, colors.valuationGaugeMiddle, colors.valuationGaugeHigh)),
        labels = gauge.labels.map { it.asString() },
    )
}

// 눈금: 칸(폭과 색)과 점수의 자리 표시. 칸마다 아래 가운데에 이름을 적는다.
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

// 맨 아래 안내: 참고 안내, 데이터 출처, FRED 고지, FRED 약관 링크
@Composable
internal fun MoodFooter() {
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

// 카드 바탕(흰 바탕, 테두리, 둥근 모서리)과 안쪽 여백, 덩어리 사이
@Composable
internal fun MoodCard(padding: PaddingValues, gap: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().cardBackground().padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
internal fun Modifier.cardBackground(): Modifier {
    val colors = HakBanGgulTheme.colors
    val shape = HakBanGgulTheme.shapes.card
    return this
        .background(colors.card, shape)
        .border(HakBanGgulTheme.dimens.cardBorder, colors.border, shape)
}

@Composable
internal fun MoodDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HakBanGgulTheme.dimens.divider)
            .background(HakBanGgulTheme.colors.border),
    )
}

// 위에 구분선이 있는 줄. 왼쪽에 이름과 그 아래 글, 오른쪽에 값
@Composable
internal fun LineRow(
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

// 누르는 영역 44, 둥근 물결이 퍼지는 아이콘 버튼
@Composable
internal fun IconButtonBox(onClick: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
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

// 계산 중 카드: 받는 중 카드와 같은 모양(바탕, 테두리, 모서리, 안쪽 여백, 진행 표시)에 글 두 줄.
// 첫 화면과 상세 화면이 함께 그린다.
@Composable
internal fun CalculatingCard() {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val typography = HakBanGgulTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().cardBackground().padding(dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.progressTextGap),
    ) {
        Box(modifier = Modifier.size(dimens.progressCardSlot), contentAlignment = Alignment.Center) {
            Spinner(dimens.progressCard, dimens.progressCardStroke)
        }
        Column(verticalArrangement = Arrangement.spacedBy(dimens.nameGap)) {
            Text(stringResource(R.string.mood_calculating_title), style = typography.title, color = colors.text)
            Text(stringResource(R.string.mood_calculating_message), style = typography.captionBanner, color = colors.textMuted)
        }
    }
}

// 돌아가는 진행 표시. 바탕 고리는 테두리 색, 도는 부분은 강조 색이다.
// size는 선 굵기까지 넣은 고리의 바깥 지름이다(CircularProgressIndicator는 크기 안에 선을 그린다).
@Composable
internal fun Spinner(size: Dp, strokeWidth: Dp) {
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

@Composable
internal fun stageColor(stage: MarketStage): Color {
    val colors = HakBanGgulTheme.colors
    return when (stage) {
        MarketStage.BULL -> colors.stageBull
        MarketStage.NEUTRAL -> colors.stageNeutral
        MarketStage.WEAK -> colors.stageWeak
        MarketStage.BEAR -> colors.stageBear
    }
}

@Composable
internal fun valuationStageColor(stage: ValuationStage): Color {
    val colors = HakBanGgulTheme.colors
    return when (stage) {
        ValuationStage.LOW -> colors.valuationLow
        ValuationStage.MIDDLE -> colors.valuationMiddle
        ValuationStage.HIGH -> colors.valuationHigh
    }
}

@Composable
internal fun toneColor(tone: ScoreTone): Color {
    val colors = HakBanGgulTheme.colors
    return when (tone) {
        ScoreTone.POSITIVE -> colors.scorePositive
        ScoreTone.ZERO -> colors.scoreZero
        ScoreTone.NEGATIVE -> colors.scoreNegative
    }
}
