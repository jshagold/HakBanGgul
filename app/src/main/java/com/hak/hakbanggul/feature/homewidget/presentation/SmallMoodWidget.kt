package com.hak.hakbanggul.feature.homewidget.presentation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import com.hak.hakbanggul.R
import com.hak.hakbanggul.feature.homewidget.di.homeWidgetEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

// 용어: 작은 위젯
// 가로 2칸 세로 1칸. 줄들이 위아래로 붙어 카드 세로 가운데에 모인다. "눌러서 앱 열기"는 그리지 않는다.
class SmallMoodWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val holder = homeWidgetEntryPoint(context).homeWidgetStateHolder()
        // 첫 값이 나올 때까지 기다린 뒤 그린다.
        val initial = holder.state.filterNotNull().first()
        provideContent {
            val ui = holder.state.collectAsState().value ?: initial
            SmallMoodContent(ui)
        }
    }
}

private val smallSlot = WidgetTheme.dimens.let {
    RefreshSlotSpec(
        width = it.widgetSmallButtonWidth,
        height = it.widgetSmallButtonHeight,
        top = it.widgetSmallButtonTop,
        end = it.widgetSmallButtonEnd,
        icon = it.widgetSmallIcon,
        progress = it.widgetSmallProgress,
        progressLayout = R.layout.widget_progress_small,
    )
}

@Composable
private fun SmallMoodContent(ui: HomeWidgetUiState) {
    val colors = WidgetTheme.colors
    val type = WidgetTheme.typography
    val dimens = WidgetTheme.dimens
    MoodWidgetCard(ui.refreshButton, smallSlot) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = dimens.widgetSmallPaddingHorizontal, vertical = dimens.widgetSmallPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            WidgetText(ui.title, type.widgetSmallLine, colors.textMuted)
            when (val main = ui.main) {
                is WidgetMain.Stage -> {
                    WidgetText(main.name, type.widgetSmallStage, stageColor(main.stage))
                    WidgetText(main.asOfText, type.widgetSmallLine, colors.textMuted)
                }
                is WidgetMain.Message -> {
                    Spacer(GlanceModifier.height(dimens.widgetSmallMessageGap))
                    WidgetText(main.text, type.widgetSmallMessage, colors.text, keepWords = true)
                }
            }
        }
    }
}
