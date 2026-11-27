package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import com.hak.hakbanggul.core.ui.KeyBadgeKind
import com.hak.hakbanggul.core.ui.SourceLabels
import com.hak.hakbanggul.core.ui.UiText
import com.hak.hakbanggul.feature.marketmood.domain.MarketDataRefresher
import com.hak.hakbanggul.feature.marketmood.domain.MarketOverviewState
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMarketOverviewUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveSourceKeysUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RefreshFailure
import com.hak.hakbanggul.feature.marketmood.domain.RefreshState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MarketMoodViewModel @Inject constructor(
    observeOverview: ObserveMarketOverviewUseCase,
    observeSourceKeys: ObserveSourceKeysUseCase,
    private val refresher: MarketDataRefresher,
    private val mapper: MoodUiMapper,
) : ViewModel() {

    // 한 벌의 상태, 키 쓸 수 있음 여부, 받기 상태를 합쳐 만든다. 값이 처음 오기 전에는 Undecided다.
    // "판정이 있다"는 계산한 한 벌이 있는지(null이 아님)로 본다. 계산 중이면 계산 중 카드를 보인다.
    val uiState: StateFlow<MarketMoodUiState> =
        combine(
            observeOverview(),
            observeSourceKeys(),
            refresher.state,
            ::buildState,
        )
            .stateIn(viewModelScope, SharingStarted.Eagerly, INITIAL_STATE)

    fun onRefreshClick() = refresher.refresh()

    fun onRetryClick() = refresher.refresh()

    fun onScreenShown() = refresher.onScreenShown()

    private fun buildState(
        value: MarketOverviewState,
        keys: Map<DataSource, ApiKeyAvailability>,
        refresh: RefreshState,
    ): MarketMoodUiState {
        val lastFailure = refresh.lastFailure
        val overview = (value as? MarketOverviewState.Ready)?.overview
        val body = when {
            keys.values.any { it != ApiKeyAvailability.AVAILABLE } -> MoodBody.KeyGuide(keyGuideRows(keys))
            value is MarketOverviewState.Calculating -> MoodBody.Calculating
            overview != null -> MoodBody.Assessment(mapper.mainScreen(overview, refresh.pendingSeriesIds))
            lastFailure == null || refresh.isRefreshing -> MoodBody.Loading
            else -> MoodBody.Failed(failedMessage(lastFailure))
        }
        // 계산 중이어도 받는 중이 아니면 누를 수 있다.
        val refreshButton = when {
            body is MoodBody.KeyGuide -> RefreshButtonState.DISABLED
            refresh.isRefreshing -> RefreshButtonState.REFRESHING
            else -> RefreshButtonState.ENABLED
        }
        // 띠는 하나만: 판정이 보이고 받는 중이며 아직 보내지 않은 종목이 있으면 기다림 띠, 아니면 판정이 보이고 가장 최근 받기가 실패했으면 안내 띠.
        val waitingBanner = if (body is MoodBody.Assessment && refresh.isRefreshing && refresh.unsentCount > 0) {
            waitingBanner(refresh.unsentCount)
        } else {
            null
        }
        val banner = if (waitingBanner == null && body is MoodBody.Assessment && lastFailure != null) banner(lastFailure) else null
        return MarketMoodUiState(refreshButton, banner, waitingBanner, body)
    }

    // "1분", "8종목"은 무료 요금제의 한도라 FetchPacing에서 끼우지 않고 글로 둔다.
    private fun waitingBanner(unsentCount: Int) = WaitingBannerUiState(
        title = UiText(R.string.waiting_banner_title, listOf(unsentCount)),
        message = UiText(R.string.waiting_banner_message),
    )

    private fun keyGuideRows(keys: Map<DataSource, ApiKeyAvailability>): List<KeyGuideRow> =
        keys.map { (source, availability) ->
            KeyGuideRow(
                source = source,
                nameRes = SourceLabels.nameRes(source),
                descriptionRes = SourceLabels.descriptionRes(source),
                badge = when (availability) {
                    ApiKeyAvailability.MISSING -> KeyBadgeKind.NONE
                    ApiKeyAvailability.AVAILABLE -> KeyBadgeKind.SAVED
                    ApiKeyAvailability.INVALID -> KeyBadgeKind.INVALID
                },
            )
        }

    private fun banner(failure: RefreshFailure): BannerUiState = when (failure) {
        RefreshFailure.RATE_LIMITED ->
            BannerUiState(UiText(R.string.banner_rate_limited_title), UiText(R.string.banner_rate_limited_message))
        RefreshFailure.NO_CONNECTION ->
            BannerUiState(UiText(R.string.banner_stale_title), UiText(R.string.banner_no_connection_message))
        RefreshFailure.OTHER ->
            BannerUiState(UiText(R.string.banner_stale_title), UiText(R.string.banner_other_message))
    }

    private fun failedMessage(failure: RefreshFailure): UiText = when (failure) {
        RefreshFailure.RATE_LIMITED -> UiText(R.string.mood_failed_rate_limited)
        RefreshFailure.NO_CONNECTION -> UiText(R.string.mood_failed_no_connection)
        RefreshFailure.OTHER -> UiText(R.string.mood_failed_other)
    }

    private companion object {
        val INITIAL_STATE = MarketMoodUiState(
            refreshButton = RefreshButtonState.DISABLED,
            banner = null,
            waitingBanner = null,
            body = MoodBody.Undecided,
        )
    }
}
