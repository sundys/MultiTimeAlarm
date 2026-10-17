package com.example.multitimealarm.ui.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
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
    onCreate: () -> Unit,
) {
    TaskListContent(
        tasks = tasks,
        emptyIcon = Icons.Filled.AlarmAdd,
        emptyHint = "还没有闹钟\n一个闹钟可以设置多个提醒时间，到点分别提醒",
        emptyAction = "新建闹钟",
        onCreate = onCreate,
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
    onCreate: () -> Unit,
) {
    TaskListContent(
        tasks = naps,
        emptyIcon = Icons.Filled.Bedtime,
        emptyHint = "还没有小憩\n定个 N 分钟后的倒计时，到点响铃",
        emptyAction = "新建小憩",
        onCreate = onCreate,
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
    emptyIcon: ImageVector,
    emptyHint: String,
    emptyAction: String,
    onCreate: () -> Unit,
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
        EmptyHint(emptyIcon, emptyHint, emptyAction, onCreate)
    } else {
        val enabled = tasks.filter { it.data.task.enabled }
        val disabled = tasks.filterNot { it.data.task.enabled }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(enabled, key = { "t${it.data.task.id}" }) { item ->
                TaskCard(
                    item = item,
                    modifier = Modifier.animateItem(),
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
                            modifier = Modifier.animateItem(),
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

/** 已停止分组折叠头：整行可点，箭头随展开状态旋转 */
@Composable
private fun DisabledGroupHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "groupArrow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已停止（$count）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation),
        )
    }
}

/** 空状态：淡色图标 + 说明 + 引导按钮 */
@Composable
private fun EmptyHint(
    icon: ImageVector,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/**
 * 闹钟卡片：左侧大号下次响铃时间为视觉锚点，右侧名称与类型信息。
 * 点击编辑，长按删除，最右总开关。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    item: TaskListItem,
    modifier: Modifier = Modifier,
    onToggleTask: (Long, Boolean) -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
) {
    val task = item.data.task
    // 时间列宽度随系统字体缩放，既能容纳大号时间，也保证第二行与名称左对齐
    val timeSlot = 84.dp * LocalDensity.current.fontScale
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // 第一行：时间 + 闹钟名称（按基线对齐），开关靠右
            Row(modifier = Modifier.fillMaxWidth()) {
                if (item.nextTimeText != null) {
                    Text(
                        text = item.nextTimeText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(timeSlot)
                            .alignByBaseline(),
                    )
                } else {
                    Text(
                        text = "已停止",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .width(timeSlot)
                            .alignByBaseline(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = task.enabled,
                    onCheckedChange = { onToggleTask(task.id, it) },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            Spacer(Modifier.height(3.dp))
            // 第二行：日期 · 类型 · 时间点数，从卡片左边距开始
            Text(
                text = subtitleOf(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 副行：下次响铃日期 · 提醒类型 · 时间点数（· 备注） */
private fun subtitleOf(item: TaskListItem): String {
    val parts = mutableListOf<String>()
    item.nextDateText?.let { parts += it }
    parts += typeText(item.data)
    parts += "${item.data.times.size}个时间点"
    item.data.task.note.takeIf { it.isNotBlank() }?.let { parts += "备注：$it" }
    return parts.joinToString(" · ")
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
                Text("多久之后响铃？", style = MaterialTheme.typography.bodyMedium)
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
