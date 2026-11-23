package com.hak.hakbanggul.feature.settings.domain

// 출처 하나의 진행 중이거나 끝난 키 확인. enteredKey는 사용자가 넣은 그대로(빈칸을 빼기 전)다.
sealed interface KeyCheckState {
    val enteredKey: String

    data class Checking(override val enteredKey: String) : KeyCheckState

    data class Failed(override val enteredKey: String, val outcome: SaveKeyOutcome) : KeyCheckState
}
