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
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.otherValuation
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.rateCard
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.rateEvidence
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.unavailableValuation
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.valuationDescription
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.valuationTitle
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.withDate
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.years

// 금리 부담 화면: 제목과 한 줄, 메인 카드(근거 세 줄), 다른 기간 카드, 맨 아래 안내
@Composable
fun RateBurdenScreen(onBack: () -> Unit, onReturnToMain: () -> Unit, viewModel: RateBurdenViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReturnToMainEffect(state, onReturnToMain)
    RateBurdenContent(state, onBack)
}

@Composable
private fun RateBurdenContent(state: DetailUiState<RateBurdenDetailUiState>, onBack: () -> Unit) {
    if (state is DetailUiState.Calculating) {
        CalculatingDetailScaffold(state, onBack)
        return
    }
    val content = (state as? DetailUiState.Shown)?.content
    DetailScaffold(
        title = stringResource(R.string.rate_burden_name),
        target = null,
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
private fun Preview(state: RateBurdenDetailUiState) {
    HakBanGgulTheme { RateBurdenContent(DetailUiState.Shown(state), onBack = {}) }
}

@Preview(name = "금리 부담 화면 (근거와 다른 기간)", widthDp = 390, heightDp = 1180)
@Composable
private fun RateDetailPreview() = Preview(
    RateBurdenDetailUiState(
        valuationTitle = valuationTitle,
        valuationDescription = valuationDescription,
        main = rateCard(evidence = rateEvidence(), showLink = false),
        otherPeriods = listOf(
            otherValuation(false, years(1), 92, ValuationStage.HIGH),
            otherValuation(false, years(3), 97, ValuationStage.HIGH),
            otherValuation(false, years(5), 98, ValuationStage.HIGH),
        ),
    ),
)

// 금리 부담 화면의 드문 모습(숫자와 단계가 다른 날, 낼 수 없는 기간, 날짜가 붙고 1년 전과 같은 날)은 주가 위치 화면과 첫 화면의 시안의 그 카드를 따른다.
@Preview(name = "금리 부담 화면 · 숫자와 단계가 다른 날, 아직 낼 수 없는 기간, 금리 값의 날짜가 다르고 1년 전과 같은 날", widthDp = 390, heightDp = 1240)
@Composable
private fun RateDetailRarePreview() = Preview(
    RateBurdenDetailUiState(
        valuationTitle = valuationTitle,
        valuationDescription = valuationDescription,
        main = rateCard(
            score = 67,
            notice = noticeHigh(false, 67),
            evidence = rateEvidence(
                yearLine = UiText(R.string.rate_evidence_year_same),
                treasury = withDate("4.92%", 11, 20),
                policy = withDate("3.88%", 11, 20),
            ),
            showLink = false,
        ),
        otherPeriods = listOf(
            otherValuation(false, years(1), 92, ValuationStage.HIGH),
            otherValuation(false, years(3), 67, ValuationStage.HIGH, noticeHigh(false, 67)),
            unavailableValuation(false, years(5)),
        ),
    ),
)

@Preview(name = "금리 부담 화면 · 판정을 계산하는 중", widthDp = 390, heightDp = 844)
@Composable
private fun RateBurdenCalculatingPreview() = HakBanGgulTheme {
    RateBurdenContent(DetailUiState.Calculating(UiText(R.string.rate_burden_name), null), onBack = {})
}
