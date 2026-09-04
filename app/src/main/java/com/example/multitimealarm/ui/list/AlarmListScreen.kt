package com.example.multitimealarm.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.TaskType
import com.example.multitimealarm.data.TaskWithTimes
import com.example.multitimealarm.ui.AlarmViewModel

/** 闹钟 Tab：展示所有循环闹钟任务 */
@Composable
fun AlarmListPage(
    tasks: List<TaskWithTimes>,
    onScrollingChanged: (Boolean) -> Unit,
    onToggleTask: (Long, Boolean) -> Unit,
    onToggleTime: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        onScrollingChanged(listState.isScrollInProgress)
    }

    var deleteTarget by remember { mutableStateOf<TaskWithTimes?>(null) }

    if (tasks.isEmpty()) {
        EmptyHint("还没有闹钟\n点击右下角 + 新建闹钟，一个闹钟可设置多个提醒时间")
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tasks, key = { it.task.id }) { item ->
                TaskCard(
                    item = item,
                    onToggleTask = onToggleTask,
                    onEdit = { onEditTask(item.task.id) },
                    onLongPress = { deleteTarget = item },
                )
            }
        }
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            name = target.task.name,
            onConfirm = {
                onDeleteTask(target.task.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 小憩 Tab：展示小憩条目 */
@Composable
fun NapListPage(
    naps: List<TaskWithTimes>,
    onScrollingChanged: (Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onToggleTask: (Long, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        onScrollingChanged(listState.isScrollInProgress)
    }

    var deleteTarget by remember { mutableStateOf<TaskWithTimes?>(null) }

    if (naps.isEmpty()) {
        EmptyHint("还没有小憩\n点击右下角 + 定时 N 分钟后响铃")
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(naps, key = { it.task.id }) { item ->
                TaskCard(
                    item = item,
                    onToggleTask = onToggleTask,
                    onEdit = { onEditTask(item.task.id) },
                    onLongPress = { deleteTarget = item },
                )
            }
        }
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            name = target.task.name,
            onConfirm = {
                onDeleteTask(target.task.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
    }
}

/** 闹钟卡片：两行紧凑样式。点击编辑，长按删除，右侧总开关 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    item: TaskWithTimes,
    onToggleTask: (Long, Boolean) -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 第一行：闹钟名称
                Text(
                    text = item.task.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                // 第二行：下次响铃时间  类型  N个时间点
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val next = AlarmViewModel.nextAlarmText(item)
                    Text(
                        text = next ?: "已停止",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        color = if (next != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = typeText(item),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${item.times.size}个时间点",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = item.task.enabled,
                onCheckedChange = { onToggleTask(item.task.id, it) },
            )
        }
    }
}

private fun typeText(item: TaskWithTimes): String = when (item.task.type) {
    TaskType.DAILY -> "每天"
    TaskType.WEEKLY -> when (item.task.weekdaysMask) {
        AlarmTaskEntity.MASK_EVERY_DAY -> "每天"
        AlarmTaskEntity.MASK_WEEKDAYS -> "工作日"
        AlarmTaskEntity.MASK_WEEKEND -> "周末"
        else -> {
            val labels = listOf("一", "二", "三", "四", "五", "六", "日")
            val days = (0..6).filter { item.task.weekdaysMask and (1 shl it) != 0 }
                .joinToString("") { labels[it] }
            if (days.isEmpty()) "未选星期" else "周$days"
        }
    }
    TaskType.MONTHLY -> "每月"
    TaskType.INTERVAL -> {
        val total = item.task.intervalMinutes
        when {
            total >= 60L && total % 60L == 0L -> "每${total / 60}小时"
            total < 60L -> "每${total}分钟"
            else -> "每${total / 60}时${total % 60}分"
        }
    }
    TaskType.ONCE -> "仅一次"
    }

/** 小憩倒计时选择对话框 */
@Composable
fun NapDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var customText by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Int?>(10) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("小憩倒计时") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("多久之后响铃？", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20).forEach { minutes ->
                        FilterChip(
                            selected = selected == minutes,
                            onClick = {
                                selected = minutes
                                customText = ""
                            },
                            label = { Text("$minutes 分钟") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60).forEach { minutes ->
                        FilterChip(
                            selected = selected == minutes,
                            onClick = {
                                selected = minutes
                                customText = ""
                            },
                            label = { Text("$minutes 分钟") },
                        )
                    }
                }
                OutlinedTextField(
                    value = customText,
                    onValueChange = {
                        customText = it.filter(Char::isDigit).take(4)
                        selected = null
                    },
                    label = { Text("自定义（分钟）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = (selected ?: customText.toIntOrNull()) ?: return@TextButton
                if (minutes > 0) onConfirm(minutes)
            }) { Text("开始小憩") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 长按删除确认对话框 */
@Composable
private fun DeleteConfirmDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除闹钟") },
        text = { Text("确定删除「$name」吗？其所有提醒时间将一并移除。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
