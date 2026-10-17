package com.example.multitimealarm.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 主题模式：0=跟随系统，1=浅色，2=暗色 */
const val THEME_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

/**
 * 主题偏好读写：此前散落在 MainActivity 里，响铃页是独立 Activity 也要读同一份，
 * 因此集中到这里，两处共用同一组 key。
 */
object ThemeStore {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    /** 跟随壁纸取色需要 Android 12+ */
    val dynamicColorSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun themeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun dynamicColor(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setDynamicColor(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }
}

/**
 * 应用主题：品牌蓝配色 + 统一 typography/shapes + 系统栏明暗。
 *
 * 主题模式与动态取色都由调用方持有状态传进来（MainActivity 需要在设置页即时切换），
 * 这里只做渲染，不自己读 SharedPreferences。
 */
@Composable
fun MultiTimeAlarmTheme(
    themeMode: Int = THEME_SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        // 跟随壁纸取色：仅 Android 12+ 可用，取色失败时退回品牌色方案
        dynamicColor && ThemeStore.dynamicColorSupported ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkColors
        else -> LightColors
    }

    // 系统栏：开启 edge-to-edge 后保持透明，只切换图标明暗（浅色主题深色图标）
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var ctx: Context? = view.context
            while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
            (ctx as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
