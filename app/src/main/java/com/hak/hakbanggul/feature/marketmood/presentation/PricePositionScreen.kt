package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.core.ui.asString
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme
import com.hak.hakbanggul.feature.marketmood.domain.ValuationStage
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.noticeHigh
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.noticeLow
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.otherValuation
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.priceCard
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.priceEvidence
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.unavailableValuation
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.valuationDescription
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.valuationTitle
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.withSymbol
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.years

// 주가 위치 화면: 제목과 한 줄, 메인 카드(근거 두 줄), 다른 기간 카드, 맨 아래 안내
@Composable
fun PricePositionScreen(onBack: () -> Unit, onReturnToMain: () -> Unit, viewModel: PricePositionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReturnToMainEffect(state, onReturnToMain)
    PricePositionContent(state, onBack)
}

@Composable
private fun PricePositionContent(state: DetailUiState<PricePositionDetailUiState>, onBack: () -> Unit) {
    if (state is DetailUiState.Calculating) {
        CalculatingDetailScaffold(state, onBack)
        return
    }
    val content = (state as? DetailUiState.Shown)?.content
    DetailScaffold(
        title = stringResource(R.string.price_position_name),
        target = content?.targetText?.asString(),
        onBack = onBack,
        content = content?.let { detail ->
            {
                SectionTitle(detail.valuationTitle.asString(), detail.valuationDescription.asString())
                ValuationCard(detail.main)
                SectionTitle(stringResource(R.string.other_periods_title), stringResource(R.string.other_periods_description))
                OtherPeriodCards(detail.otherPeriods)
                MoodFooter()
            }
        },
    )
}

// 미리보기

@Composable
private fun Preview(state: PricePositionDetailUiState) {
    HakBanGgulTheme { PricePositionContent(DetailUiState.Shown(state), onBack = {}) }
}

@Preview(name = "주가 위치 화면 (근거와 다른 기간)", widthDp = 390, heightDp = 1180)
@Composable
private fun PriceDetailPreview() = Preview(
    PricePositionDetailUiState(
        targetText = withSymbol(R.string.target_broad_market, "SPY"),
        valuationTitle = valuationTitle,
        valuationDescription = valuationDescription,
        main = priceCard(evidence = priceEvidence("-1.9%", "+15.9%", "-7.7%", "-6.2%"), showLink = false),
        otherPeriods = listOf(
            otherValuation(true, years(1), 8, ValuationStage.LOW),
            otherValuation(true, years(3), 16, ValuationStage.LOW),
            otherValuation(true, years(5), 35, ValuationStage.MIDDLE),
        ),
    ),
)

@Preview(name = "주가 위치 화면 · 숫자와 단계가 다른 날, 아직 낼 수 없는 기간 (XLC)", widthDp = 390, heightDp = 1180)
@Composable
private fun PriceDetailRarePreview() = Preview(
    PricePositionDetailUiState(
        targetText = withSymbol(R.string.target_communication_services, "XLC"),
        valuationTitle = valuationTitle,
        valuationDescription = valuationDescription,
        main = priceCard(
            subtitle = withSymbol(R.string.target_communication_services, "XLC"),
            score = 33,
            notice = noticeLow(true, 33),
            evidence = priceEvidence("-1.9%", "+15.9%", "-7.7%", "-6.2%"),
            showLink = false,
        ),
        otherPeriods = listOf(
            otherValuation(true, years(1), 41, ValuationStage.MIDDLE),
            otherValuation(true, years(3), 67, ValuationStage.HIGH, noticeHigh(true, 67)),
            unavailableValuation(true, years(5)),
        ),
    ),
)

@Preview(name = "섹터의 주가 위치 화면 · 반도체 SMH", widthDp = 390, heightDp = 1180)
@Composable
private fun SectorPricePreview() = Preview(
    PricePositionDetailUiState(
        targetText = withSymbol(R.string.target_semiconductors, "SMH"),
        valuationTitle = valuationTitle,
        valuationDescription = valuationDescription,
        main = priceCard(
            subtitle = withSymbol(R.string.target_semiconductors, "SMH"),
            score = 15,
            evidence = priceEvidence("-18.8%", "+29.7%", "-36.2%", "-23.2%"),
            showLink = false,
        ),
        otherPeriods = listOf(
            otherValuation(true, years(1), 2, ValuationStage.LOW),
            otherValuation(true, years(3), 14, ValuationStage.LOW),
            otherValuation(true, years(5), 28, ValuationStage.LOW),
        ),
    ),
)

@Preview(name = "주가 위치 화면 · 판정을 계산하는 중", widthDp = 390, heightDp = 844)
@Composable
private fun PricePositionCalculatingPreview() = HakBanGgulTheme {
    PricePositionContent(
        DetailUiState.Calculating(
            UiText(R.string.price_position_name),
            UiText(R.string.target_with_symbol, listOf(UiText(R.string.target_broad_market), "SPY")),
        ),
        onBack = {},
    )
}
