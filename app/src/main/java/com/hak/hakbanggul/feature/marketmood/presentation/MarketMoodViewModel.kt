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
import com.hak.hakbanggul.feature.marketmood.domain.MarketOverview
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMarketOverviewUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveSourceKeysUseCase
import com.hak.hakbanggul.feature.marketmood.domain.RefreshFailure
import com.hak.hakbanggul.feature.marketmood.domain.RefreshState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MarketMoodViewModel @Inject constructor(
    observeOverview: ObserveMarketOverviewUseCase,
    observeSourceKeys: ObserveSourceKeysUseCase,
    private val refresher: MarketDataRefresher,
    private val mapper: MoodUiMapper,
) : ViewModel() {

    // 한 벌, 키 쓸 수 있음 여부, 받기 상태를 합쳐 만든다. 값이 처음 오기 전에는 Undecided다.
    // "판정이 있다"는 한 벌이 있는지(null이 아님)로 본다.
    // 한 벌은 계산에 시간이 걸려, 처음 오기 전에도 키와 받기 상태로 상태를 만든다(받는 중이면 진행 표시를 보인다).
    val uiState: StateFlow<MarketMoodUiState> =
        combine(
            observeOverview().map<MarketOverview?, OverviewValue> { OverviewValue.Received(it) }.onStart { emit(OverviewValue.NotYet) },
            observeSourceKeys(),
            refresher.state,
            ::buildState,
        )
            .stateIn(viewModelScope, SharingStarted.Eagerly, INITIAL_STATE)

    fun onRefreshClick() = refresher.refresh()

    fun onRetryClick() = refresher.refresh()

    fun onScreenShown() = refresher.onScreenShown()

    // 한 벌이 아직 오지 않음과 와서 null(판정을 낼 수 없음)을 가른다.
    private sealed interface OverviewValue {
        data object NotYet : OverviewValue
        data class Received(val overview: MarketOverview?) : OverviewValue
    }

    private fun buildState(
        value: OverviewValue,
        keys: Map<DataSource, ApiKeyAvailability>,
        refresh: RefreshState,
    ): MarketMoodUiState {
        val lastFailure = refresh.lastFailure
        val overview = (value as? OverviewValue.Received)?.overview
        val body = when {
            keys.values.any { it != ApiKeyAvailability.AVAILABLE } -> MoodBody.KeyGuide(keyGuideRows(keys))
            value is OverviewValue.NotYet -> MoodBody.Undecided
            overview != null -> MoodBody.Assessment(mapper.mainScreen(overview, refresh.isRefreshing))
            lastFailure == null || refresh.isRefreshing -> MoodBody.Loading
            else -> MoodBody.Failed(failedMessage(lastFailure))
        }
        // 본문이 아직 정해지지 않았어도 받는 중이면 진행 표시를 보인다.
        val refreshButton = when {
            body is MoodBody.KeyGuide -> RefreshButtonState.DISABLED
            refresh.isRefreshing -> RefreshButtonState.REFRESHING
            body is MoodBody.Undecided -> RefreshButtonState.DISABLED
            else -> RefreshButtonState.ENABLED
        }
        val banner = if (body is MoodBody.Assessment && lastFailure != null) banner(lastFailure) else null
        return MarketMoodUiState(refreshButton, banner, body)
    }

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
            body = MoodBody.Undecided,
        )
    }
}
