package com.example.multitimealarm.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 应用字体规格：在 Material 3 默认 typography 基础上，把"时间显示"相关的几档
 * 调成闹钟应用该有的字重，并统一开启等宽数字（tnum）。
 *
 * 等宽数字的作用：秒级刷新的时间（响铃页每秒重绘）数字宽度一致，不会左右抖动。
 * 其余档位沿用 M3 默认，页面里直接引用 `MaterialTheme.typography.xxx`，不再手写 fontSize。
 */
private val Base = Typography()

/** 时间类文本统一使用等宽数字 */
const val TABULAR_FIGURES = "tnum"

val AppTypography = Base.copy(
    // 响铃页超大号时间
    displayLarge = Base.displayLarge.copy(
        fontSize = 88.sp,
        lineHeight = 96.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    // 编辑页时间点（一屏内要放下多个时间点，不能太大）
    displaySmall = Base.displaySmall.copy(
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    // 列表卡片左侧的下次响铃时间
    headlineSmall = Base.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    // 卡片标题 / 闹钟名称
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium),
)
