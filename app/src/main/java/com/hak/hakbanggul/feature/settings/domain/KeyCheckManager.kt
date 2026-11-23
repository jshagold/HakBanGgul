package com.hak.hakbanggul.feature.settings.domain

import com.hak.hakbanggul.core.di.ApplicationScope
import com.hak.hakbanggul.core.model.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// 키 확인을 앱 범위에서 돌려, 화면을 벗어나도 끝까지 가게 한다. 상태는 메모리에만 둔다.
@Singleton
class KeyCheckManager @Inject constructor(
    private val saveApiKey: SaveApiKeyUseCase,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<DataSource, KeyCheckState>>(emptyMap())

    // 확인 중이거나 확인에 실패한 출처만 담는다.
    val states: StateFlow<Map<DataSource, KeyCheckState>> = _states.asStateFlow()

    fun startSave(source: DataSource, rawKey: String) {
        // 확인 중인지 보는 것과 확인 중으로 두는 것을 한 번에 해, 두 번 눌러도 한 번만 돈다.
        var started = false
        _states.update { current ->
            started = current[source] !is KeyCheckState.Checking
            if (started) current + (source to KeyCheckState.Checking(rawKey)) else current
        }
        if (!started) return

        scope.launch {
            val outcome = saveApiKey(source, rawKey)
            _states.update { current ->
                if (outcome == SaveKeyOutcome.SAVED) {
                    current - source
                } else {
                    current + (source to KeyCheckState.Failed(rawKey, outcome))
                }
            }
        }
    }
}
