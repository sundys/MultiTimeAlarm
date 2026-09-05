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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multitimealarm.data.TaskWithTimes
import com.example.multitimealarm.ui.TaskListItem

/** 闹钟 Tab：展示所有循环闹钟任务 */
@Composable
fun AlarmListPage(
    tasks: List<TaskListItem>,
    onScrollingChanged: (Boolean) -> Unit,
    onToggleTask: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
) {
    TaskListContent(
        tasks = tasks,
        emptyHint = "还没有闹钟\n点击右下角 + 新建闹钟，一个闹钟可设置多个提醒时间",
        onScrollingChanged = onScrollingChanged,
        onToggleTask = onToggleTask,
        onDeleteTask = onDeleteTask,
        onEditTask = onEditTask,
    )
}

/** 小憩 Tab：展示小憩条目 */
@Composable
fun NapListPage(
    naps: List<TaskListItem>,
    onScrollingChanged: (Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onToggleTask: (Long, Boolean) -> Unit,
) {
    TaskListContent(
        tasks = naps,
        emptyHint = "还没有小憩\n点击右下角 + 定时 N 分钟后响铃",
        onScrollingChanged = onScrollingChanged,
        onToggleTask = onToggleTask,
        onDeleteTask = onDeleteTask,
        onEditTask = onEditTask,
    )
}

/** 列表公共实现：启用中的置顶，已停止的折叠沉底 */
@Composable
private fun TaskListContent(
    tasks: List<TaskListItem>,
    emptyHint: String,
    onScrollingChanged: (Boolean) -> Unit,
    onToggleTask: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        onScrollingChanged(listState.isScrollInProgress)
    }

    var deleteTarget by remember { mutableStateOf<TaskListItem?>(null) }
    // 已停止分组默认折叠
    var disabledExpanded by rememberSaveable { mutableStateOf(false) }

    if (tasks.isEmpty()) {
        EmptyHint(emptyHint)
    } else {
        val enabled = tasks.filter { it.data.task.enabled }
        val disabled = tasks.filterNot { it.data.task.enabled }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(enabled, key = { "t${it.data.task.id}" }) { item ->
                TaskCard(
                    item = item,
                    onToggleTask = onToggleTask,
                    onEdit = { onEditTask(item.data.task.id) },
                    onLongPress = { deleteTarget = item },
                )
            }
            if (disabled.isNotEmpty()) {
                item(key = "disabled_header") {
                    DisabledGroupHeader(
                        count = disabled.size,
                        expanded = disabledExpanded,
                        onClick = { disabledExpanded = !disabledExpanded },
                    )
                }
                if (disabledExpanded) {
                    items(disabled, key = { "t${it.data.task.id}" }) { item ->
                        TaskCard(
                            item = item,
                            onToggleTask = onToggleTask,
                            onEdit = { onEditTask(item.data.task.id) },
                            onLongPress = { deleteTarget = item },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            name = target.data.task.name,
            onConfirm = {
                onDeleteTask(target.data.task.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 已停止分组折叠头：显示数量与展开/收起操作 */
@Composable
private fun DisabledGroupHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已停止（$count）",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClick) {
            Text(if (expanded) "收起" else "展开", fontSize = 13.sp)
        }
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
    item: TaskListItem,
    onToggleTask: (Long, Boolean) -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
) {
    val task = item.data.task
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
                    text = task.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                // 第二行：下次响铃时间  类型  N个时间点
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.nextText ?: "已停止",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        color = if (item.nextText != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = typeText(item.data),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${item.data.times.size}个时间点",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = task.enabled,
                onCheckedChange = { onToggleTask(task.id, it) },
            )
        }
    }
}

private fun typeText(item: TaskWithTimes): String = when (item.task.type) {
    com.example.multitimealarm.data.TaskType.DAILY -> "每天"
    com.example.multitimealarm.data.TaskType.WEEKLY -> when (item.task.weekdaysMask) {
        com.example.multitimealarm.data.AlarmTaskEntity.MASK_EVERY_DAY -> "每天"
        com.example.multitimealarm.data.AlarmTaskEntity.MASK_WEEKDAYS -> "工作日"
        com.example.multitimealarm.data.AlarmTaskEntity.MASK_WEEKEND -> "周末"
        else -> {
            val labels = listOf("一", "二", "三", "四", "五", "六", "日")
            val days = (0..6).filter { item.task.weekdaysMask and (1 shl it) != 0 }
                .joinToString("") { labels[it] }
            if (days.isEmpty()) "未选星期" else "周$days"
        }
    }
    com.example.multitimealarm.data.TaskType.MONTHLY -> "每月"
    com.example.multitimealarm.data.TaskType.INTERVAL -> {
        val total = item.task.intervalMinutes
        when {
            total >= 60L && total % 60L == 0L -> "每${total / 60}小时"
            total < 60L -> "每${total}分钟"
            else -> "每${total / 60}时${total % 60}分"
        }
    }
    com.example.multitimealarm.data.TaskType.ONCE -> "仅一次"
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
