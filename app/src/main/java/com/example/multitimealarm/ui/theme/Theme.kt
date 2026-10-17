package com.example.multitimealarm.ui.theme

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private const val TAG = "Theme"

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

    /**
     * 系统是否**真正**提供动态取色。
     *
     * 不少国产 ROM（ColorOS / 部分 EMUI 等）虽然对外是 API 31+，但并没有实现 Monet：
     * 要么 system_accent1_500 资源缺失，要么取到的仍是 AOSP 基线紫（#6750A4）。
     * 这种情况下无论怎么切开关，界面都不会有可见变化，需要在设置页如实说明。
     */
    fun dynamicColorAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val id = context.resources.getIdentifier("system_accent1_500", "color", "android")
        if (id == 0) return false
        val accent = runCatching { context.getColor(id) }.getOrNull() ?: return false
        return accent != AOSP_BASELINE_ACCENT
    }

    /** AOSP 未实现动态取色时 system_accent1_500 的基线值 */
    private val AOSP_BASELINE_ACCENT = 0xFF6750A4.toInt()

    /**
     * 直接读取系统壁纸的主色（API 27+）。
     *
     * ColorOS 等没有实现 Monet 的 ROM 上，系统取色接口只会返回基线色，
     * 这里改为读取壁纸主色自行生成配色，让"跟随壁纸取色"在这些机型上也能真正生效。
     * 读取失败（权限/ROM 限制）返回 null。
     */
    fun wallpaperSeedArgb(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        return runCatching {
            val manager = context.getSystemService(WallpaperManager::class.java)
                ?: return@runCatching null
            manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
        }.getOrNull()
    }

    fun wallpaperColorAvailable(context: Context): Boolean = wallpaperSeedArgb(context) != null

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

    // 跟随壁纸取色：
    // 1) 系统实现了 Monet（AOSP / 类原生）→ 用系统动态配色
    // 2) 未实现（ColorOS 等）→ 直接读壁纸主色自行生成配色
    // 3) 都拿不到 → 回退品牌配色
    val dynamicScheme = if (dynamicColor && ThemeStore.dynamicColorSupported) {
        remember(dark) {
            if (ThemeStore.dynamicColorAvailable(context)) {
                runCatching {
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                    .onSuccess {
                        Log.i(TAG, "系统动态取色 primary=#${Integer.toHexString(it.primary.toArgb())}")
                    }
                    .onFailure { Log.w(TAG, "系统动态取色失败，尝试壁纸主色", it) }
                    .getOrNull()
            } else {
                ThemeStore.wallpaperSeedArgb(context)?.let { argb ->
                    Log.i(TAG, "系统无动态取色，改用壁纸主色 #${Integer.toHexString(argb)}")
                    schemeFromWallpaper(Color(argb), dark)
                }
            }
        }
    } else {
        null
    }

    val colorScheme = dynamicScheme ?: if (dark) DarkColors else LightColors

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

/**
 * 用壁纸主色生成一套近似 Material 3 的配色：表面/中性色沿用品牌方案，
 * 只替换强调色及其容器色。用于 ColorOS 等没有实现 Monet 的 ROM。
 */
private fun schemeFromWallpaper(seed: Color, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    val primary = if (dark) lerp(seed, Color.White, 0.55f) else seed
    val onPrimary = if (primary.luminance() > 0.5f) Color(0xFF1A1C1E) else Color.White
    val primaryContainer = lerp(base.surface, seed, if (dark) 0.32f else 0.16f)
    val onPrimaryContainer =
        lerp(seed, if (dark) Color.White else Color.Black, if (dark) 0.75f else 0.55f)
    val secondary = lerp(seed, base.onSurfaceVariant, if (dark) 0.5f else 0.4f)
    val onSecondary = if (secondary.luminance() > 0.5f) Color(0xFF1A1C1E) else Color.White
    val secondaryContainer = lerp(base.surface, seed, if (dark) 0.22f else 0.10f)
    val tertiary = lerp(seed, base.tertiary, 0.4f)
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = if (dark) seed else lerp(seed, Color.White, 0.5f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = tertiary,
        onTertiary = onSecondary,
        tertiaryContainer = secondaryContainer,
        onTertiaryContainer = onPrimaryContainer,
        surfaceTint = primary,
    )
}
