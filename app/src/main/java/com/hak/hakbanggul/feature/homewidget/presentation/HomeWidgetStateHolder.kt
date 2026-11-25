package com.hak.hakbanggul.feature.homewidget.presentation

import com.hak.hakbanggul.core.di.ApplicationScope
import com.hak.hakbanggul.feature.homewidget.domain.ObserveWidgetStateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

// 놓인 위젯 모두가 받는 위젯의 상태. 앱 전체에 하나다.
// 만들어질 때부터 앱 범위에서 ObserveWidgetStateUseCase를 한 번만 모은다. 첫 값이 나오기 전에는 null이다.
// 위젯마다, 그릴 때마다 새로 모으지 않아, 그대로 두는 동안에 새로 놓은 위젯도 그대로 두고 있는 것을 받는다.
@Singleton
class HomeWidgetStateHolder @Inject constructor(
    observeWidgetState: ObserveWidgetStateUseCase,
    @ApplicationScope scope: CoroutineScope,
) {
    val state: StateFlow<HomeWidgetUiState?> = observeWidgetState()
        .map { it.toUiState() }
        .stateIn(scope, SharingStarted.Eagerly, null)
}
