package com.example.multitimealarm.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PICKER_ITEM_HEIGHT = 44.dp

/**
 * 单列数字滚动选择器：0..count-1，上下滑动选择，中间行即当前值。
 * 用 LazyColumn + snap 惯性实现，停下时回报选中的数字。
 */
@Composable
fun NumberPickerColumn(
    value: Int,
    count: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // 初始滚动到当前值
    LaunchedEffect(count) {
        listState.scrollToItem(value.coerceIn(0, count - 1))
    }

    // 滚动停下后回报选中值
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val idx = listState.firstVisibleItemIndex.coerceIn(0, count - 1)
            if (idx != value) onValueChange(idx)
        }
    }

    Box(modifier.height(PICKER_ITEM_HEIGHT * 5)) {
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
            contentPadding = PaddingValues(vertical = PICKER_ITEM_HEIGHT * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count) { i ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PICKER_ITEM_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = String.format("%02d", i),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
