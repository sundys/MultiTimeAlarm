package com.example.multitimealarm.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.TaskType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** 编辑页内部的时间行（id=0 表示尚未入库的新增时间点） */
private data class TimeRow(val id: Long, val hour: Int, val minute: Int, val second: Int, val enabled: Boolean)

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    taskId: Long?,
    loadTask: suspend (Long) -> com.example.multitimealarm.data.TaskWithTimes?,
    onSave: (AlarmTaskEntity, List<AlarmTimeEntity>, List<Long>) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TaskType.DAILY) }
    var weekdaysMask by remember { mutableStateOf(AlarmTaskEntity.MASK_EVERY_DAY) }
    var monthDayText by remember { mutableStateOf("1") }
    var intervalHoursText by remember { mutableStateOf("2") }
    var intervalMinutesText by remember { mutableStateOf("0") }
    var dateEpochDay by remember { mutableStateOf<Long?>(null) }
    var vibrate by remember { mutableStateOf(true) }
    var taskEnabled by remember { mutableStateOf(true) }
    var createdAt by remember { mutableStateOf(0L) }
    var snoozeMinutesText by remember { mutableStateOf("5") }
    var snoozeMaxCountText by remember { mutableStateOf("3") }
    val times = remember { mutableStateListOf<TimeRow>() }
    val removedIds = remember { mutableStateListOf<Long>() }
    var isLoaded by remember { mutableStateOf(taskId == null) }

    // 编辑模式下载入已有任务
    LaunchedEffect(taskId) {
        if (taskId != null) {
            loadTask(taskId)?.let { loaded ->
                name = loaded.task.name
                type = loaded.task.type
                weekdaysMask = loaded.task.weekdaysMask
                monthDayText = loaded.task.monthDay.takeIf { it in 1..31 }?.toString() ?: "1"
                intervalHoursText = (loaded.task.intervalMinutes / 60).toString()
                intervalMinutesText = (loaded.task.intervalMinutes % 60).toString()
                dateEpochDay = loaded.task.dateEpochDay
                vibrate = loaded.task.vibrate
                taskEnabled = loaded.task.enabled
                createdAt = loaded.task.createdAt
                snoozeMinutesText = loaded.task.snoozeMinutes.toString()
                snoozeMaxCountText = loaded.task.snoozeMaxCount.toString()
                times.clear()
                times.addAll(loaded.times.map { TimeRow(it.id, it.hour, it.minute, it.second, it.enabled) })
                isLoaded = true
            }
        }
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == null) "新建闹钟" else "编辑闹钟") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("取消") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("闹钟名称") },
                placeholder = { Text("例如：吃药 / 开会 / 喝水") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // 提醒类型
            Text("提醒类型", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TaskType.DAILY,
                    onClick = { type = TaskType.DAILY },
                    label = { Text("每天") },
                )
                FilterChip(
                    selected = type == TaskType.WEEKLY,
                    onClick = { type = TaskType.WEEKLY },
                    label = { Text("按星期") },
                )
                FilterChip(
                    selected = type == TaskType.MONTHLY,
                    onClick = { type = TaskType.MONTHLY },
                    label = { Text("每月") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TaskType.INTERVAL,
                    onClick = { type = TaskType.INTERVAL },
                    label = { Text("间隔循环") },
                )
                FilterChip(
                    selected = type == TaskType.ONCE,
                    onClick = {
                        type = TaskType.ONCE
                        if (dateEpochDay == null) showDatePicker = true
                    },
                    label = { Text("仅一次") },
                )
            }
            Text(
                text = when (type) {
                    TaskType.DAILY -> "闹钟会在每天的下列时间点各提醒一次"
                    TaskType.WEEKLY -> "闹钟会在所选星期的下列时间点各提醒一次"
                    TaskType.MONTHLY -> "闹钟会在每月所选日期的下列时间点各提醒一次"
                    TaskType.INTERVAL -> "从起始时间开始，每隔设定的间隔循环提醒一次（持续循环）"
                    TaskType.ONCE -> "闹钟会在指定日期的下列时间点各提醒一次，全部提醒完后自动关闭"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 按星期：星期选择 + 快捷预设
            if (type == TaskType.WEEKLY) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEKDAY_LABELS.forEachIndexed { index, label ->
                        val bit = 1 shl index
                        FilterChip(
                            selected = weekdaysMask and bit != 0,
                            onClick = { weekdaysMask = weekdaysMask xor bit },
                            label = { Text(label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = weekdaysMask == AlarmTaskEntity.MASK_EVERY_DAY,
                        onClick = { weekdaysMask = AlarmTaskEntity.MASK_EVERY_DAY },
                        label = { Text("每天") },
                    )
                    FilterChip(
                        selected = weekdaysMask == AlarmTaskEntity.MASK_WEEKDAYS,
                        onClick = { weekdaysMask = AlarmTaskEntity.MASK_WEEKDAYS },
                        label = { Text("工作日") },
                    )
                    FilterChip(
                        selected = weekdaysMask == AlarmTaskEntity.MASK_WEEKEND,
                        onClick = { weekdaysMask = AlarmTaskEntity.MASK_WEEKEND },
                        label = { Text("周末") },
                    )
                }
            }

            // 每月几号
            if (type == TaskType.MONTHLY) {
                OutlinedTextField(
                    value = monthDayText,
                    onValueChange = { monthDayText = it.filter(Char::isDigit).take(2) },
                    label = { Text("每月几号（1-31，无该日的月份自动跳过）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // 间隔循环：小时间隔 + 分钟间隔
            if (type == TaskType.INTERVAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = intervalHoursText,
                        onValueChange = { intervalHoursText = it.filter(Char::isDigit).take(3) },
                        label = { Text("每隔（小时）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = intervalMinutesText,
                        onValueChange = { intervalMinutesText = it.filter(Char::isDigit).take(2) },
                        label = { Text("再加（分钟）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }

            // 仅一次：日期选择
            if (type == TaskType.ONCE) {
                Card(modifier = Modifier.fillMaxWidth(), onClick = { showDatePicker = true }) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("提醒日期", fontSize = 14.sp)
                            Text(
                                text = dateEpochDay
                                    ?.let {
                                        LocalDate.ofEpochDay(it)
                                            .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                                    }
                                    ?: "点击选择日期",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (dateEpochDay == null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // 多时间点列表
            Text(
                text = if (type == TaskType.INTERVAL) "起始时间（${times.size} 个）" else "提醒时间（${times.size} 个）",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (errorText != null) {
                Text(errorText!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            times.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (row.second == 0) String.format("%02d:%02d", row.hour, row.minute)
                        else String.format("%02d:%02d:%02d", row.hour, row.minute, row.second),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = row.enabled,
                        onCheckedChange = { checked -> times[index] = row.copy(enabled = checked) },
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (row.id != 0L) removedIds.add(row.id)
                        times.removeAt(index)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除该时间点")
                    }
                }
            }
            Button(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (type == TaskType.INTERVAL) "添加起始时间" else "添加时间点")
            }

            // 贪睡设置
            Text("贪睡设置", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = snoozeMinutesText,
                    onValueChange = { snoozeMinutesText = it.filter(Char::isDigit).take(3) },
                    label = { Text("贪睡间隔（分钟）") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = snoozeMaxCountText,
                    onValueChange = { snoozeMaxCountText = it.filter(Char::isDigit).take(2) },
                    label = { Text("次数上限（0=不限）") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("振动", modifier = Modifier.weight(1f))
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val snoozeMinutes = snoozeMinutesText.toIntOrNull()?.coerceIn(1, 999) ?: 5
                    val snoozeMaxCount = snoozeMaxCountText.toIntOrNull()?.coerceIn(0, 99) ?: 3
                    val intervalMinutes =
                        (intervalHoursText.toIntOrNull() ?: 0) * 60L + (intervalMinutesText.toIntOrNull() ?: 0).toLong()
                    val monthDay = monthDayText.toIntOrNull() ?: 0

                    errorText = when {
                        times.isEmpty() ->
                            if (type == TaskType.INTERVAL) "至少添加一个起始时间" else "至少添加一个提醒时间"
                        type == TaskType.WEEKLY && weekdaysMask == 0 -> "请至少选择一个星期"
                        type == TaskType.MONTHLY && monthDay !in 1..31 -> "每月几号需在 1-31 之间"
                        type == TaskType.INTERVAL && intervalMinutes <= 0 -> "间隔时间必须大于 0 分钟"
                        type == TaskType.ONCE && dateEpochDay == null -> "请选择提醒日期"
                        else -> null
                    }
                    if (errorText != null) return@Button

                    val task = AlarmTaskEntity(
                        id = taskId ?: 0L,
                        name = name.ifBlank { "定时提醒" },
                        type = type,
                        weekdaysMask = if (type == TaskType.WEEKLY) weekdaysMask else 0,
                        monthDay = if (type == TaskType.MONTHLY) monthDay else 0,
                        intervalMinutes = if (type == TaskType.INTERVAL) intervalMinutes else 0,
                        dateEpochDay = if (type == TaskType.ONCE) dateEpochDay else null,
                        enabled = taskEnabled,
                        vibrate = vibrate,
                        snoozeMinutes = snoozeMinutes,
                        snoozeMaxCount = snoozeMaxCount,
                        createdAt = if (createdAt != 0L) createdAt else System.currentTimeMillis(),
                    )
                    val timeEntities = times.map {
                        AlarmTimeEntity(
                            id = it.id, taskId = task.id,
                            hour = it.hour, minute = it.minute, second = it.second,
                            enabled = it.enabled,
                        )
                    }
                    onSave(task, timeEntities, removedIds.toList())
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = isLoaded,
            ) {
                Text("保存", fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 时/分/秒三列数字滚动选择器
    if (showTimePicker) {
        var pickedHour by remember { mutableStateOf(times.lastOrNull()?.hour ?: 8) }
        var pickedMinute by remember { mutableStateOf(times.lastOrNull()?.minute ?: 0) }
        var pickedSecond by remember { mutableStateOf(times.lastOrNull()?.second ?: 0) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(if (type == TaskType.INTERVAL) "选择起始时间" else "选择提醒时间") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberPickerColumn(
                            value = pickedHour, count = 24,
                            onValueChange = { pickedHour = it },
                            modifier = Modifier.width(72.dp),
                        )
                        Text("时", fontSize = 13.sp)
                    }
                    Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberPickerColumn(
                            value = pickedMinute, count = 60,
                            onValueChange = { pickedMinute = it },
                            modifier = Modifier.width(72.dp),
                        )
                        Text("分", fontSize = 13.sp)
                    }
                    Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberPickerColumn(
                            value = pickedSecond, count = 60,
                            onValueChange = { pickedSecond = it },
                            modifier = Modifier.width(72.dp),
                        )
                        Text("秒", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    times.add(TimeRow(0L, pickedHour, pickedMinute, pickedSecond, enabled = true))
                    errorText = null
                    showTimePicker = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateEpochDay?.let { it * TimeUnit.DAYS.toMillis(1) },
        )
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("选择提醒日期") },
            text = { DatePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dateEpochDay = it / TimeUnit.DAYS.toMillis(1)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        )
    }
}
