package com.hak.hakbanggul.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

// 화면에 보일 글 하나. ViewModel이 Android의 Context 없이 문구를 고르기 위해 쓴다.
// args에는 글자, 숫자, 다른 UiText가 들어갈 수 있다.
data class UiText(
    @param:StringRes val resId: Int,
    val args: List<Any> = emptyList(),
)

// 글자로 푼다. args 안의 UiText를 먼저 풀어 끼운다.
@Composable
fun UiText.asString(): String {
    val resolved = args.map { arg -> if (arg is UiText) arg.asString() else arg }
    return stringResource(resId, *resolved.toTypedArray())
}
