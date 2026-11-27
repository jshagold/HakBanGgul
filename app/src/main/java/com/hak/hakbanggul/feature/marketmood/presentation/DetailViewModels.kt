package com.hak.hakbanggul.feature.marketmood.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hak.hakbanggul.core.model.DataSource
import com.hak.hakbanggul.core.network.ApiKeyAvailability
import com.hak.hakbanggul.feature.marketmood.domain.MarketOverview
import com.hak.hakbanggul.feature.marketmood.domain.MarketOverviewState
import com.hak.hakbanggul.feature.marketmood.domain.MoodRules
import com.hak.hakbanggul.feature.marketmood.domain.ObserveMarketOverviewUseCase
import com.hak.hakbanggul.feature.marketmood.domain.ObserveSourceKeysUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// 이동 경로의 인자 이름. 상세 화면과 섹터 화면의 ViewModel이 SavedStateHandle로 대상 ID를 받는다.
const val TARGET_ID_ARG = "targetId"

// 상세 화면과 섹터 화면의 상태를 만든다. 한 벌과 키 쓸 수 있음 여부를 받기 전에는 Undecided다.
// 위부터: 출처의 키 가운데 하나라도 AVAILABLE이 아니면 BackToMain, 계산 중이면 규칙으로 만든 위쪽 줄로 Calculating
// (calculating이 null이면 대상을 찾지 못해 BackToMain), 판정을 낼 수 없으면 BackToMain,
// build가 null(대상을 찾지 못함, 메인 콘텐츠를 낼 수 없음)이면 BackToMain, 아니면 Shown이다.
// 계산 중일 때는 첫 화면으로 돌아가지 않는다. 한 벌이 바뀔 때마다(묶음이 저장될 때마다 포함) 다시 만든다.
internal fun <T> detailState(
    overview: Flow<MarketOverviewState>,
    keys: Flow<Map<DataSource, ApiKeyAvailability>>,
    scope: CoroutineScope,
    calculating: (MoodRules) -> DetailUiState.Calculating?,
    build: (MarketOverview) -> T?,
): StateFlow<DetailUiState<T>> =
    combine(overview, keys) { value, availability ->
        if (availability.values.any { it != ApiKeyAvailability.AVAILABLE }) return@combine DetailUiState.BackToMain
        when (value) {
            is MarketOverviewState.Calculating -> calculating(value.rules) ?: DetailUiState.BackToMain
            is MarketOverviewState.Ready -> value.overview?.let(build)?.let { DetailUiState.Shown(it) } ?: DetailUiState.BackToMain
        }
    }.stateIn(scope, SharingStarted.Eagerly, DetailUiState.Undecided)

private fun SavedStateHandle.targetId(): String = get<String>(TARGET_ID_ARG).orEmpty()

@HiltViewModel
class RegimeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeOverview: ObserveMarketOverviewUseCase,
    observeSourceKeys: ObserveSourceKeysUseCase,
    mapper: MoodUiMapper,
) : ViewModel() {
    private val targetId = savedStateHandle.targetId()

    val uiState: StateFlow<DetailUiState<RegimeDetailUiState>> =
        detailState(
            observeOverview(),
            observeSourceKeys(),
            viewModelScope,
            calculating = { mapper.regimeCalculating(it, targetId) },
        ) { mapper.regimeDetail(it, targetId) }
}

@HiltViewModel
class PricePositionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeOverview: ObserveMarketOverviewUseCase,
    observeSourceKeys: ObserveSourceKeysUseCase,
    mapper: MoodUiMapper,
) : ViewModel() {
    private val targetId = savedStateHandle.targetId()

    val uiState: StateFlow<DetailUiState<PricePositionDetailUiState>> =
        detailState(
            observeOverview(),
            observeSourceKeys(),
            viewModelScope,
            calculating = { mapper.pricePositionCalculating(it, targetId) },
        ) { mapper.pricePositionDetail(it, targetId) }
}

// 대상 ID가 없다(금리 부담은 대상과 상관없다).
@HiltViewModel
class RateBurdenViewModel @Inject constructor(
    observeOverview: ObserveMarketOverviewUseCase,
    observeSourceKeys: ObserveSourceKeysUseCase,
    mapper: MoodUiMapper,
) : ViewModel() {
    val uiState: StateFlow<DetailUiState<RateBurdenDetailUiState>> =
        detailState(
            observeOverview(),
            observeSourceKeys(),
            viewModelScope,
            calculating = { mapper.rateBurdenCalculating() },
        ) { mapper.rateBurdenDetail(it) }
}

@HiltViewModel
class SectorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeOverview: ObserveMarketOverviewUseCase,
    observeSourceKeys: ObserveSourceKeysUseCase,
    mapper: MoodUiMapper,
) : ViewModel() {
    private val targetId = savedStateHandle.targetId()

    val uiState: StateFlow<DetailUiState<SectorUiState>> =
        detailState(
            observeOverview(),
            observeSourceKeys(),
            viewModelScope,
            calculating = { mapper.sectorCalculating(it, targetId) },
        ) { mapper.sector(it, targetId) }
}
