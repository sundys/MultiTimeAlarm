package com.example.multitimealarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.multitimealarm.ui.theme.BrandBlue

/**
 * 分类切换胶囊：整颗居中，选中一半填充品牌蓝 + 白字。
 *
 * 相比占满整行的 TabRow，它读起来是一个"控件"而不是一层导航，且不占用横向空白；
 * 选中态用 [BrandBlue] 固定色，浅色 / 暗色主题下都是同一个醒目的品牌蓝。
 */
@Composable
fun CategorySwitcher(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = BrandBlue,
) {
    // 选中段文字颜色按背景亮度决定，动态取色下浅色主色也能保证可读性
    val selectedContentColor =
        if (selectedColor.luminance() > 0.5f) Color(0xFF1A1C1E) else Color.White
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                // 切换时底色与文字色同时过渡，避免瞬变
                val background by animateColorAsState(
                    targetValue = if (selected) selectedColor else Color.Transparent,
                    label = "switcherBackground",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) selectedContentColor
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "switcherContent",
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(background)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .padding(horizontal = 26.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
