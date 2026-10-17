package com.example.multitimealarm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 应用圆角规格：卡片 16dp（medium）、对话框与底部按钮 24/28dp（large/extraLarge）。
 * 设置页、编辑页卡片统一引用 medium，避免各页手写 dp 值导致圆角不一致。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
