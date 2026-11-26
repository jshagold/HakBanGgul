package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.asString
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme
import com.hak.hakbanggul.feature.marketmood.domain.MarketStage
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.broadRegime
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.months
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.otherRegime
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.pending
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.regimeCard
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.sectorRegime
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.withSymbol
import com.hak.hakbanggul.feature.marketmood.presentation.MoodPreviewData.years

// 장세 화면(전체 장세, 섹터의 장세): 메인 카드, 근거, 다른 기간 카드, 맨 아래 안내
@Composable
fun RegimeScreen(onBack: () -> Unit, onReturnToMain: () -> Unit, viewModel: RegimeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReturnToMainEffect(state, onReturnToMain)
    RegimeContent(state, onBack)
}

@Composable
private fun RegimeContent(state: DetailUiState<RegimeDetailUiState>, onBack: () -> Unit) {
    val content = (state as? DetailUiState.Shown)?.content
    DetailScaffold(
        title = content?.title?.asString().orEmpty(),
        target = content?.symbol,
        onBack = onBack,
        content = content?.let { detail ->
            {
                RegimeCard(detail.main)
                SectionTitle(stringResource(R.string.evidence_title), detail.evidenceBasis.asString())
                detail.groups.forEach { GroupCard(it) }
                SectionTitle(stringResource(R.string.other_periods_title), stringResource(R.string.other_periods_description))
                OtherPeriodCards(detail.otherPeriods)
                MoodFooter()
            }
        },
    )
}

// 미리보기

@Composable
private fun Preview(state: RegimeDetailUiState) {
    HakBanGgulTheme { RegimeContent(DetailUiState.Shown(state), onBack = {}) }
}

private fun broadOthers(halfPending: Boolean) = listOf(
    otherRegime(
        period = months(6),
        stage = MarketStage.NEUTRAL,
        score = if (halfPending) "+0.55" else "+0.15",
        tone = ScoreTone.POSITIVE,
        gauge = if (halfPending) 0.775f else 0.575f,
        averageDays = 100,
        slopeDays = 40,
        average = "-5.3%",
        drop = "-7.7%",
        slope = "+0.7%",
        pending = if (halfPending) pending(MarketStage.BULL, MarketStage.NEUTRAL, 2) else null,
    ),
    otherRegime(
        period = years(1),
        stage = MarketStage.BULL,
        score = "+0.62",
        tone = ScoreTone.POSITIVE,
        gauge = 0.81f,
        averageDays = 200,
        slopeDays = 80,
        average = "-1.9%",
        drop = "-7.7%",
        slope = "+4.6%",
    ),
)

@Preview(name = "전체 장세 화면 (근거와 다른 기간)", widthDp = 390, heightDp = 1580)
@Composable
private fun RegimeDetailPreview() = Preview(
    RegimeDetailUiState(
        title = broadRegime,
        symbol = null,
        main = regimeCard(showLink = false),
        evidenceBasis = com.hak.hakbanggul.core.ui.UiText(R.string.evidence_basis, listOf(months(3))),
        groups = MoodPreviewData.groups(),
        otherPeriods = broadOthers(halfPending = false),
    ),
)

@Preview(name = "전체 장세 화면 · 메인과 다른 기간 카드의 점수와 장세가 다른 날", widthDp = 390, heightDp = 1700)
@Composable
private fun RegimeDetailPendingPreview() = Preview(
    RegimeDetailUiState(
        title = broadRegime,
        symbol = null,
        main = regimeCard(
            computed = MarketStage.NEUTRAL,
            score = "+0.15",
            tone = ScoreTone.POSITIVE,
            gauge = 0.575f,
            pending = pending(MarketStage.NEUTRAL, MarketStage.WEAK, 1),
            showLink = false,
        ),
        evidenceBasis = com.hak.hakbanggul.core.ui.UiText(R.string.evidence_basis, listOf(months(3))),
        groups = MoodPreviewData.groups(
            stockScore = "0.00",
            stockTone = ScoreTone.ZERO,
            average = "+2.4%" to "+1.0",
            high = "-6.1%" to "0.0",
            slope = "-0.3%" to "-1.0",
        ),
        otherPeriods = broadOthers(halfPending = true),
    ),
)

@Preview(name = "섹터의 장세 화면 · 반도체 SMH", widthDp = 390, heightDp = 1580)
@Composable
private fun SectorRegimePreview() = Preview(
    RegimeDetailUiState(
        title = sectorRegime(R.string.target_semiconductors),
        symbol = "SMH",
        main = regimeCard(
            name = sectorRegime(R.string.target_semiconductors),
            stage = MarketStage.BEAR,
            score = "-0.55",
            gauge = 0.225f,
            showLink = false,
        ),
        evidenceBasis = com.hak.hakbanggul.core.ui.UiText(R.string.evidence_basis, listOf(months(3))),
        groups = MoodPreviewData.groups(
            target = withSymbol(R.string.target_semiconductors, "SMH"),
            stockScore = "-1.00",
            average = "-21.5%" to "-1.0",
            high = "-32.7%" to "-1.0",
            slope = "-5.2%" to "-1.0",
        ),
        otherPeriods = listOf(
            otherRegime(
                name = sectorRegime(R.string.target_semiconductors),
                period = months(6),
                stage = MarketStage.BEAR,
                score = "-0.55",
                tone = ScoreTone.NEGATIVE,
                gauge = 0.225f,
                averageDays = 100,
                slopeDays = 40,
                average = "-23.1%",
                drop = "-36.2%",
                slope = "-4.7%",
            ),
            otherRegime(
                name = sectorRegime(R.string.target_semiconductors),
                period = years(1),
                stage = MarketStage.WEAK,
                score = "-0.08",
                tone = ScoreTone.NEGATIVE,
                gauge = 0.46f,
                averageDays = 200,
                slopeDays = 80,
                average = "-18.8%",
                drop = "-36.2%",
                slope = "+16.4%",
            ),
        ),
    ),
)
