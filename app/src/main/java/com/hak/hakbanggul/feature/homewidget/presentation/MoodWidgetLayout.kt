package com.hak.hakbanggul.feature.homewidget.presentation

import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.AppLaunchIntents
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.core.ui.asString
import com.hak.hakbanggul.core.ui.theme.AppColors
import com.hak.hakbanggul.core.ui.theme.AppDimens
import com.hak.hakbanggul.core.ui.theme.AppTypography
import com.hak.hakbanggul.feature.homewidget.domain.WidgetRefreshButton
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight

// 작은 위젯과 큰 위젯이 함께 쓰는 조각: 카드 바탕, 버튼 자리, 글 한 줄, 매매 판단 줄.
// 색과 크기는 공용 테마의 값을 꺼내 Glance의 것으로 바꿔 쓴다. 색은 고정 값이라 기기가 어두운 화면이어도 바뀌지 않는다.
internal object WidgetTheme {
    val colors = AppColors()
    val typography = AppTypography()
    val dimens = AppDimens()
}

// 버튼 자리의 크기와 자리, 아이콘과 진행 표시
internal data class RefreshSlotSpec(
    val width: Dp,
    val height: Dp,
    val top: Dp,
    val end: Dp,
    val icon: Dp,
    val progress: Dp,
    @param:LayoutRes val progressLayout: Int,
)

// 카드: 바탕 그림을 받은 크기에 꽉 차게 깔고, 버튼 자리 밖을 누르면 앱의 시장 분위기 화면을 연다.
// 내용(content)과 버튼 자리(slot)를 겹쳐 놓는다. 버튼 자리는 카드 오른쪽 위에서 잰다.
@Composable
internal fun MoodWidgetCard(
    refreshButton: WidgetRefreshButton,
    slot: RefreshSlotSpec,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_card_background))
            .clickable(actionStartActivity(AppLaunchIntents.marketMood(context))),
        contentAlignment = Alignment.TopEnd,
    ) {
        content()
        RefreshButtonSlot(refreshButton, slot)
    }
}

// 버튼 자리: 상태에 따라 누를 수 있는 아이콘, 흐린 아이콘, 도는 진행 표시 가운데 하나.
// 비활성과 진행 표시도 누름을 받아 아무 일도 하지 않는다(받지 않으면 카드의 앱 열기로 넘어간다).
@Composable
private fun RefreshButtonSlot(state: WidgetRefreshButton, slot: RefreshSlotSpec) {
    val context = LocalContext.current
    val action = if (state == WidgetRefreshButton.ENABLED) {
        actionRunCallback<RefreshWidgetAction>()
    } else {
        actionRunCallback<IgnoreWidgetClickAction>()
    }
    Box(modifier = GlanceModifier.padding(top = slot.top, end = slot.end)) {
        Box(
            modifier = GlanceModifier.size(slot.width, slot.height).clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                WidgetRefreshButton.IN_PROGRESS -> AndroidRemoteViews(
                    remoteViews = RemoteViews(context.packageName, slot.progressLayout),
                    modifier = GlanceModifier.size(slot.progress),
                )
                else -> Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = context.getString(R.string.action_refresh),
                    modifier = GlanceModifier.size(slot.icon),
                    colorFilter = ColorFilter.tint(
                        ColorProvider(
                            if (state == WidgetRefreshButton.ENABLED) {
                                WidgetTheme.colors.textMuted
                            } else {
                                WidgetTheme.colors.widgetRefreshDisabled
                            },
                        ),
                    ),
                )
            }
        }
    }
}

// 글 한 줄. keepWords가 참이면 띄어쓰기에서만 줄을 바꾼다. maxLines를 주면 그 줄 수를 넘지 않는다.
@Composable
internal fun WidgetText(
    text: UiText,
    style: ComposeTextStyle,
    color: Color,
    keepWords: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    val resolved = text.asString(LocalContext.current)
    Text(
        text = if (keepWords) keepWordsTogether(resolved) else resolved,
        style = style.toGlance(color),
        maxLines = maxLines,
    )
}

// 매매 판단 두 줄 묶음(위에서 주가 위치, 금리 부담). 줄 사이는 띄우지 않는다.
// 큰 위젯은 이름과 점수·단계, 작은 위젯은 줄인 이름과 단계 이름을 쓴다.
@Composable
internal fun WidgetValuationLines(stage: WidgetMain.Stage, short: Boolean) {
    Column {
        listOf(stage.pricePosition, stage.rateBurden).forEach { line ->
            WidgetValuationRow(
                name = if (short) line.shortName else line.name,
                value = if (short) line.stageName else line.scoreText,
                stage = line.stage,
            )
        }
    }
}

// 매매 판단 줄 하나: 이름 쪽(보통 굵기, 회색)과 단계 쪽(굵게, 단계의 색)을 가로로 놓고 사이를 띄운다.
// Glance의 글 하나에는 크기·굵기·색을 하나만 줄 수 있어 두 글로 그린다. 띄어쓰기를 글 끝에 넣어 띄우지 않는다.
// 넘쳐도 줄을 바꾸지 않는다.
@Composable
private fun WidgetValuationRow(name: UiText, value: UiText, stage: ValuationStage) {
    val type = WidgetTheme.typography
    Row(verticalAlignment = Alignment.CenterVertically) {
        WidgetText(name, type.widgetValuationName, WidgetTheme.colors.textMuted, maxLines = 1)
        Spacer(GlanceModifier.width(WidgetTheme.dimens.widgetValuationNameGap))
        WidgetText(value, type.widgetValuationStage, valuationStageColor(stage), maxLines = 1)
    }
}

// 단계 이름의 색. 앱의 시장 분위기 화면과 같은 테마 값이다.
internal fun stageColor(stage: MarketStage): Color = when (stage) {
    MarketStage.BULL -> WidgetTheme.colors.stageBull
    MarketStage.NEUTRAL -> WidgetTheme.colors.stageNeutral
    MarketStage.WEAK -> WidgetTheme.colors.stageWeak
    MarketStage.BEAR -> WidgetTheme.colors.stageBear
}

// 매매 판단 단계의 색. 앱의 주가 위치·금리 부담 카드와 같은 테마 값이다(두 카드가 같다).
internal fun valuationStageColor(stage: ValuationStage): Color = when (stage) {
    ValuationStage.LOW -> WidgetTheme.colors.valuationLow
    ValuationStage.MIDDLE -> WidgetTheme.colors.valuationMiddle
    ValuationStage.HIGH -> WidgetTheme.colors.valuationHigh
}

// Compose의 글자 모양을 Glance의 것으로 바꾼다. 크기와 굵기만 옮긴다(줄 높이는 Glance에서 정할 수 없다).
private fun ComposeTextStyle.toGlance(color: Color): TextStyle {
    val weight = fontWeight ?: ComposeFontWeight.Normal
    return TextStyle(
        color = ColorProvider(color),
        fontSize = fontSize,
        fontWeight = when {
            weight >= ComposeFontWeight.Bold -> FontWeight.Bold
            weight >= ComposeFontWeight.Medium -> FontWeight.Medium
            else -> FontWeight.Normal
        },
    )
}

// Android의 기본 줄 바꿈은 한글 낱말 가운데에서도 줄을 바꾼다. 띄어쓰기가 아닌 두 글자 사이마다
// WORD JOINER(U+2060, 보이지 않고 그 자리에서 줄을 바꾸지 못하게 하는 글자)를 넣어 띄어쓰기에서만 바뀌게 한다.
internal fun keepWordsTogether(text: String): String = buildString(text.length * 2) {
    text.forEachIndexed { i, c ->
        if (i > 0 && !c.isWhitespace() && !text[i - 1].isWhitespace() && !c.isLowSurrogate()) append(WORD_JOINER)
        append(c)
    }
}

private const val WORD_JOINER = '⁠'
