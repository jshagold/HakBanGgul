package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.core.ui.asString
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.priceCard
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.regimeCard
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.sectorRegime
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.valuationDescription
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.valuationTitle
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.withSymbol

// 섹터 화면: 장세 카드, 제목과 한 줄, 주가 위치 카드, 맨 아래 안내. 금리 부담은 두지 않는다.
@Composable
fun SectorScreen(
    onBack: () -> Unit,
    onReturnToMain: () -> Unit,
    onOpenRegime: (targetId: String) -> Unit,
    onOpenPricePosition: (targetId: String) -> Unit,
    viewModel: SectorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReturnToMainEffect(state, onReturnToMain)
    SectorContent(state, onBack, onOpenRegime, onOpenPricePosition)
}

@Composable
private fun SectorContent(
    state: DetailUiState<SectorUiState>,
    onBack: () -> Unit,
    onOpenRegime: (String) -> Unit,
    onOpenPricePosition: (String) -> Unit,
) {
    if (state is DetailUiState.Calculating) {
        CalculatingDetailScaffold(state, onBack)
        return
    }
    val content = (state as? DetailUiState.Shown)?.content
    DetailScaffold(
        title = content?.name?.asString().orEmpty(),
        target = content?.symbol,
        onBack = onBack,
        content = content?.let { sector ->
            {
                RegimeCard(sector.regime) { onOpenRegime(sector.targetId) }
                SectionTitle(sector.valuationTitle.asString(), sector.valuationDescription.asString())
                ValuationCard(sector.pricePosition) { onOpenPricePosition(sector.targetId) }
                MoodFooter()
            }
        },
    )
}

// 미리보기

@Preview(name = "섹터 화면 · 반도체 SMH", widthDp = 390, heightDp = 900)
@Composable
private fun SectorDetailPreview() = HakBanGgulTheme {
    SectorContent(
        DetailUiState.Shown(
            SectorUiState(
                targetId = "semiconductors",
                name = UiText(R.string.target_semiconductors),
                symbol = "SMH",
                regime = regimeCard(name = sectorRegime(R.string.target_semiconductors), stage = MarketStage.BEAR, score = "-0.55", gauge = 0.225f),
                valuationTitle = valuationTitle,
                valuationDescription = valuationDescription,
                pricePosition = priceCard(subtitle = withSymbol(R.string.target_semiconductors, "SMH"), score = 15),
            ),
        ),
        onBack = {},
        onOpenRegime = {},
        onOpenPricePosition = {},
    )
}

@Preview(name = "섹터 화면 · 판정을 계산하는 중 (반도체 SMH)", widthDp = 390, heightDp = 844)
@Composable
private fun SectorCalculatingPreview() = HakBanGgulTheme {
    SectorContent(
        DetailUiState.Calculating(UiText(R.string.target_semiconductors), UiText(R.string.detail_target_symbol, listOf("SMH"))),
        onBack = {},
        onOpenRegime = {},
        onOpenPricePosition = {},
    )
}
