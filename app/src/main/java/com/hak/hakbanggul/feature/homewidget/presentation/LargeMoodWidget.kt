package com.hak.hakbanggul.feature.homewidget.presentation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import com.hak.hakbanggul.R
import com.hak.hakbanggul.feature.homewidget.di.homeWidgetEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

// 용어: 큰 위젯
// 가로 2칸 세로 2칸. 첫 줄이 위쪽 끝, 마지막 줄이 아래쪽 끝, 단계 이름이나 안내 문구가 그 사이 가운데에 놓인다.
class LargeMoodWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val holder = homeWidgetEntryPoint(context).homeWidgetStateHolder()
        // 첫 값이 나올 때까지 기다린 뒤 그린다.
        val initial = holder.state.filterNotNull().first()
        provideContent {
            val ui = holder.state.collectAsState().value ?: initial
            LargeMoodContent(ui)
        }
    }
}

private val largeSlot = WidgetTheme.dimens.let {
    RefreshSlotSpec(
        width = it.widgetLargeButtonWidth,
        height = it.widgetLargeButtonHeight,
        top = it.widgetLargeButtonTop,
        end = it.widgetLargeButtonEnd,
        icon = it.widgetLargeIcon,
        progress = it.widgetLargeProgress,
        progressLayout = R.layout.widget_progress_large,
    )
}

@Composable
private fun LargeMoodContent(ui: HomeWidgetUiState) {
    val colors = WidgetTheme.colors
    val type = WidgetTheme.typography
    MoodWidgetCard(ui.refreshButton, largeSlot) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(WidgetTheme.dimens.widgetLargePadding),
            horizontalAlignment = Alignment.Start,
        ) {
            WidgetText(ui.title, type.widgetLargeTitle, colors.textMuted)
            // 첫 줄과 마지막 줄 사이를 채우는 칸의 세로 가운데에 둔다(시안의 space-between과 같은 자리).
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (val main = ui.main) {
                    is WidgetMain.Stage -> WidgetText(main.name, type.widgetLargeStage, stageColor(main.stage))
                    is WidgetMain.Message -> WidgetText(main.text, type.widgetLargeMessage, colors.text, keepWords = true)
                }
            }
            when (val main = ui.main) {
                is WidgetMain.Stage -> WidgetText(main.asOfText, type.widgetLargeFooter, colors.textMuted)
                is WidgetMain.Message -> WidgetText(main.openAppHint, type.widgetLargeFooter, colors.textMuted)
            }
        }
    }
}
