package com.example.multitimealarm.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val PICKER_ITEM_HEIGHT = 44.dp
private val PICKER_VISIBLE_ROWS = 3

/**
 * 单列数字滚动选择器，中间行即当前值，停下时回报选中的索引。
 *
 * circular = false：普通模式，0..count-1，初始定位到 value。
 * circular = true ：循环模式，内容复制多份并锚定在中段，滑到头自动回绕（23→00、59→00），
 *                   onValueChange 始终回报 count 取模后的真实值。
 */
@Composable
fun NumberPickerColumn(
    value: Int,
    count: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: (Int) -> String = { String.format(java.util.Locale.US, "%02d", it) },
    circular: Boolean = false,
) {
    val listState = rememberLazyListState()
    val copies = if (circular) 5 else 1
    val base = if (circular) count * (copies / 2) else 0
    var initialized by remember { mutableStateOf(false) }
    var reanchoring by remember { mutableStateOf(false) }

    // 等对话框完成布局后再滚动定位（首帧测量前滚动会偏移一格）
    LaunchedEffect(count, circular) {
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        listState.scrollToItem(value.coerceIn(0, count - 1) + base)
        initialized = true
    }

    // 滚动停下后，回报"离视口中心最近"的项为选中值（初始定位完成前不回报）
    LaunchedEffect(listState.isScrollInProgress) {
        if (!initialized || listState.isScrollInProgress || reanchoring) return@LaunchedEffect
        val info = listState.layoutInfo
        if (info.visibleItemsInfo.isEmpty()) return@LaunchedEffect
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val centered = info.visibleItemsInfo
            .minByOrNull { abs(it.offset + it.size / 2 - center) }
            ?.index
            ?: return@LaunchedEffect

        if (circular) {
            // 接近首/尾副本时，静默回锚到中段等价位置
            if (centered < count || centered >= count * (copies - 1)) {
                reanchoring = true
                listState.scrollToItem(centered % count + base)
                reanchoring = false
            }
            val realValue = centered % count
            if (!reanchoring && realValue != value) onValueChange(realValue)
        } else {
            if (centered in 0 until count && centered != value) onValueChange(centered)
        }
    }

    // 仅显示中间 3 行（上下各留 1 行缓冲），首尾行不显示
    Box(modifier.height(PICKER_ITEM_HEIGHT * PICKER_VISIBLE_ROWS)) {
        // 中间选中行高亮底色
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(PICKER_ITEM_HEIGHT)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    RoundedCornerShape(10.dp),
                ),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(vertical = PICKER_ITEM_HEIGHT),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count * copies) { i ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PICKER_ITEM_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(i % count),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
