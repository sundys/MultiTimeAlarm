package com.example.multitimealarm.ui.list

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.TaskType
import com.example.multitimealarm.data.TaskWithTimes
import com.example.multitimealarm.ui.AlarmViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
                    onToggleTime = onToggleTime,
                    onDeleteTask = onDeleteTask,
                    onEdit = { onEditTask(item.task.id) },
                )
            }
        }
    }
}

/** 小憩 Tab：展示小憩条目 */
@Composable
fun NapListPage(
    naps: List<TaskWithTimes>,
    onScrollingChanged: (Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        onScrollingChanged(listState.isScrollInProgress)
    }

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
                    onToggleTask = { id, checked -> /* 小憩无总开关需求，占位 */ },
                    onToggleTime = { _, _ -> },
                    onDeleteTask = onDeleteTask,
                    onEdit = { onEditTask(item.task.id) },
                    showToggles = false,
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    item: TaskWithTimes,
    onToggleTask: (Long, Boolean) -> Unit,
    onToggleTime: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onEdit: () -> Unit,
    showToggles: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.task.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = typeSummary(item),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showToggles) {
                    Switch(
                        checked = item.task.enabled,
                        onCheckedChange = { onToggleTask(item.task.id, it) },
                    )
                    IconButton(onClick = { onDeleteTask(item.task.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                } else {
                    IconButton(onClick = { onDeleteTask(item.task.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            item.times
                .sortedWith(compareBy({ it.hour }, { it.minute }, { it.second }))
                .forEach { time ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatTime(time.hour, time.minute, time.second),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        if (showToggles) {
                            Switch(
                                checked = time.enabled,
                                onCheckedChange = { onToggleTime(time.id, it) },
                                enabled = item.task.enabled,
                            )
                        }
                    }
                }
            val next = AlarmViewModel.nextAlarmText(item)
            if (next != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "下次响铃：$next",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

fun formatTime(hour: Int, minute: Int, second: Int): String =
    if (second == 0) String.format("%02d:%02d", hour, minute)
    else String.format("%02d:%02d:%02d", hour, minute, second)

private fun typeSummary(item: TaskWithTimes): String {
    val count = "${item.times.size} 个时间点"
    return when (item.task.type) {
        TaskType.DAILY -> "每天 · $count"
        TaskType.WEEKLY -> {
            val labels = listOf("一", "二", "三", "四", "五", "六", "日")
            val days = (0..6).filter { item.task.weekdaysMask and (1 shl it) != 0 }
                .joinToString("、") { labels[it] }
            val summary = when (item.task.weekdaysMask) {
                AlarmTaskEntity.MASK_EVERY_DAY -> "每天"
                AlarmTaskEntity.MASK_WEEKDAYS -> "工作日"
                AlarmTaskEntity.MASK_WEEKEND -> "周末"
                else -> if (days.isEmpty()) "未选星期" else "周$days"
            }
            "$summary · $count"
        }
        TaskType.MONTHLY -> "每月 ${item.task.monthDay} 日 · $count"
        TaskType.INTERVAL -> {
            val total = item.task.intervalMinutes
            val text = when {
                total % 60L == 0L && total >= 60L -> "每 ${total / 60} 小时"
                total < 60L -> "每 $total 分钟"
                else -> "每 ${total / 60} 小时 ${total % 60} 分钟"
            }
            "$text · $count"
        }
        TaskType.ONCE -> {
            val dateText = item.task.dateEpochDay
                ?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("MM月dd日")) }
                ?: "未设日期"
            "$dateText · $count"
        }
    }
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
