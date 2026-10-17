package com.example.multitimealarm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 卡片与设置项通用组件：设置页、编辑页共用同一套视觉语言
 * （16dp 圆角卡片 + 左侧彩色图标底衬 + 右侧值/箭头）。
 */

/** 图标配色：中等明度的强调色，浅色 / 暗色主题下都能看清 */
object SettingTint {
    val Violet = Color(0xFF8B5CF6)
    val Orange = Color(0xFFF0871F)
    val Pink = Color(0xFFEC4899)
    val Indigo = Color(0xFF6366F1)
    val Teal = Color(0xFF14B8A6)
    val Amber = Color(0xFFD9A200)
    val Green = Color(0xFF22A06B)
    val Blue = Color(0xFF3B82F6)
    val Grey = Color(0xFF7C8798)
}

/** 分类小标题 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 通用卡片容器：圆角 + 表面色 + 细描边 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(content = content)
    }
}

/** 分类卡片：小标题 + 卡片容器 */
@Composable
fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionHeader(title)
    AppCard(content = content)
}

/** 图标底衬：圆角色块 + 居中图标 */
@Composable
fun SettingIcon(icon: ImageVector, tint: Color, size: Int = 40, iconSize: Int = 21) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

/** 卡片内分隔线：左侧留出图标宽度，与文字对齐 */
@Composable
fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * 通用设置项：左侧图标，中间标题 + 说明，右侧当前值 + 箭头。
 *
 * [trailing] 非空时用它替代"当前值 + 箭头"（用于开关这类自带交互的控件）。
 */
@Composable
fun SettingItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, iconTint)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        } else {
            value?.let {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 104.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
