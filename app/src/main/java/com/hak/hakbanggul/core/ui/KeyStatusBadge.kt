package com.hak.hakbanggul.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hak.hakbanggul.R
import com.hak.hakbanggul.core.ui.theme.HakBanGgulTheme

// 키의 상태 딱지의 종류
enum class KeyBadgeKind {
    // 키 없음
    NONE,

    // 저장됨
    SAVED,

    // 틀림
    INVALID,
}

// 키의 상태 딱지. 색만으로 구분하지 않게 글과 색을 늘 함께 쓴다.
@Composable
fun KeyStatusBadge(kind: KeyBadgeKind, modifier: Modifier = Modifier) {
    val colors = HakBanGgulTheme.colors
    val dimens = HakBanGgulTheme.dimens
    val (textRes, background, textColor) = when (kind) {
        KeyBadgeKind.NONE -> Triple(R.string.key_badge_none, colors.badgeNoneBackground, colors.badgeNoneText)
        KeyBadgeKind.SAVED -> Triple(R.string.key_badge_saved, colors.badgeSavedBackground, colors.badgeSavedText)
        KeyBadgeKind.INVALID -> Triple(R.string.key_badge_invalid, colors.badgeInvalidBackground, colors.badgeInvalidText)
    }
    Text(
        text = stringResource(textRes),
        style = HakBanGgulTheme.typography.badge,
        color = textColor,
        modifier = modifier
            .background(background, HakBanGgulTheme.shapes.badge)
            .padding(horizontal = dimens.badgePaddingHorizontal, vertical = dimens.badgePaddingVertical),
    )
}
