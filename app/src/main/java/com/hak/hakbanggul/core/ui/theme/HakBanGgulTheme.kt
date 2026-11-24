package com.hak.hakbanggul.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val LocalAppColors = staticCompositionLocalOf { AppColors() }
private val LocalAppTypography = staticCompositionLocalOf { AppTypography() }
private val LocalAppShapes = staticCompositionLocalOf { AppShapes() }
private val LocalAppDimens = staticCompositionLocalOf { AppDimens() }

// 앱의 공용 테마. 밝은 화면 하나만 두고, 기기의 배경 색을 따라가지 않는다.
// 같은 값을 Material3의 색·글자·모양에도 넣어 Material 부품이 같은 색을 쓰게 한다.
@Composable
fun HakBanGgulTheme(content: @Composable () -> Unit) {
    val colors = AppColors()
    val typography = AppTypography()
    val shapes = AppShapes()
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography,
        LocalAppShapes provides shapes,
        LocalAppDimens provides AppDimens(),
    ) {
        MaterialTheme(
            colorScheme = materialColors(colors),
            typography = materialTypography(typography),
            shapes = materialShapes(),
            content = content,
        )
    }
}

// 화면이 테마의 값을 가져오는 곳
object HakBanGgulTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val typography: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current
    val shapes: AppShapes
        @Composable @ReadOnlyComposable get() = LocalAppShapes.current
    val dimens: AppDimens
        @Composable @ReadOnlyComposable get() = LocalAppDimens.current
}

private fun materialColors(c: AppColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = c.onAccent,
    primaryContainer = c.badgeSavedBackground,
    onPrimaryContainer = c.badgeSavedText,
    secondary = c.accent,
    onSecondary = c.onAccent,
    tertiary = c.accent,
    onTertiary = c.onAccent,
    background = c.background,
    onBackground = c.text,
    surface = c.card,
    onSurface = c.text,
    surfaceVariant = c.background,
    onSurfaceVariant = c.textMuted,
    surfaceContainerLowest = c.card,
    surfaceContainerLow = c.card,
    surfaceContainer = c.card,
    surfaceContainerHigh = c.card,
    surfaceContainerHighest = c.card,
    outline = c.disabled,
    outlineVariant = c.border,
    error = c.errorBorder,
    onError = c.onAccent,
    errorContainer = c.warningBackground,
    onErrorContainer = c.warningText,
)

private fun materialTypography(t: AppTypography) = Typography(
    titleLarge = t.title,
    titleMedium = t.groupName,
    titleSmall = t.bodyMedium,
    bodyLarge = t.body,
    bodyMedium = t.small,
    bodySmall = t.caption,
    labelLarge = t.bodyBold,
    labelMedium = t.badge,
    labelSmall = t.badge,
)

private fun materialShapes() = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)
