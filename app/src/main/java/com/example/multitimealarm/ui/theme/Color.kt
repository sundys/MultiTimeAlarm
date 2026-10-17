package com.example.multitimealarm.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 品牌蓝：与应用图标底色一致。
 *
 * 浅色方案直接以它为 primary；深色方案的 primary 是提亮版本（深色背景下才够对比），
 * 所以需要"两种主题下都是醒目品牌蓝"的元素（如首页新建按钮）直接引用这个常量，
 * 而不是用 colorScheme.primary。
 */
val BrandBlue = Color(0xFF1A73E8)

/**
 * 应用配色：以品牌蓝为种子手写的一套 Material 3 配色。
 *
 * 逐槽位写死而非用动态生成，是为了保证各系统版本、各 ROM 上表现一致。
 * 需要跟随壁纸取色时（Android 12+）由 ThemeStore 走 dynamicColorScheme，不走这里。
 */
val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    inversePrimary = Color(0xFFADC6FF),

    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),

    tertiary = Color(0xFF00696D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF6FF6FB),
    onTertiaryContainer = Color(0xFF002022),

    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),

    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceTint = BrandBlue,
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),

    // 卡片层次：surfaceContainer 用于设置页/编辑页的卡片底
    surfaceBright = Color(0xFFFDFBFF),
    surfaceDim = Color(0xFFDDD8E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F6FA),
    surfaceContainer = Color(0xFFF1F0F4),
    surfaceContainerHigh = Color(0xFFECEBEF),
    surfaceContainerHighest = Color(0xFFE6E6EA),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    inversePrimary = BrandBlue,

    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283041),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),

    tertiary = Color(0xFF4CDADF),
    onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF004F51),
    onTertiaryContainer = Color(0xFF6FF6FB),

    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E6),

    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFADC6FF),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF2F3033),

    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E11),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    scrim = Color(0xFF000000),
)
