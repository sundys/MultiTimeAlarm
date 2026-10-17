package com.example.multitimealarm.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.TaskType
import com.example.multitimealarm.ui.components.SettingDivider
import com.example.multitimealarm.ui.components.SettingSection
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 编辑页内部的时间行（id=0 表示尚未入库的新增时间点） */
private data class TimeRow(val id: Long, val hour: Int, val minute: Int, val second: Int, val enabled: Boolean)

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 提醒类型选项（顺序即展示顺序） */
private val TYPE_OPTIONS = listOf(
    TaskType.DAILY to "每天",
    TaskType.WEEKLY to "按星期",
    TaskType.MONTHLY to "每月",
    TaskType.INTERVAL to "间隔循环",
    TaskType.ONCE to "仅一次",
)

/** 时间点文本：秒为 0 时省略秒，与列表页显示习惯一致 */
private fun formatTimeText(hour: Int, minute: Int, second: Int): String =
    if (second == 0) {
        String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hour, minute, second)
    }

/** 仅一次：所选日期的全部时间点是否都已过去（过去则保存即失效，不允许） */
private fun isOnceFullyPast(dateEpochDay: Long, times: List<TimeRow>): Boolean {
    val date = java.time.LocalDate.ofEpochDay(dateEpochDay)
    val zone = java.time.ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    return times.all { row ->
        date.atTime(row.hour, row.minute, row.second)
            .atZone(zone).toInstant().toEpochMilli() <= now
    }
}

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
    var monthDaysText by remember { mutableStateOf("1") }
    var intervalHoursText by remember { mutableStateOf("2") }
    var intervalMinutesText by remember { mutableStateOf("0") }
    var dateEpochDay by remember { mutableStateOf<Long?>(null) }
    var vibrate by remember { mutableStateOf(true) }
    var createdAt by remember { mutableStateOf(0L) }
    var snoozeMinutesText by remember { mutableStateOf("5") }
    var snoozeMaxCountText by remember { mutableStateOf("3") }
    var note by remember { mutableStateOf("") }
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
                monthDaysText = com.example.multitimealarm.util.MonthDaysParser
                    .toStorageString(
                        com.example.multitimealarm.util.TimeUtils.effectiveMonthlyDays(loaded.task)
                    )
                    .ifEmpty { "1" }
                intervalHoursText = (loaded.task.intervalMinutes / 60).toString()
                intervalMinutesText = (loaded.task.intervalMinutes % 60).toString()
                dateEpochDay = loaded.task.dateEpochDay
                vibrate = loaded.task.vibrate
                createdAt = loaded.task.createdAt
                snoozeMinutesText = loaded.task.snoozeMinutes.toString()
                snoozeMaxCountText = loaded.task.snoozeMaxCount.toString()
                note = loaded.task.note
                times.clear()
                times.addAll(loaded.times.map { TimeRow(it.id, it.hour, it.minute, it.second, it.enabled) })
                isLoaded = true
            }
        }
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var editingTimeIndex by remember { mutableStateOf<Int?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    // 校验失败信息统一走 Snackbar 提示，展示后即清空
    var errorText by remember { mutableStateOf<String?>(null) }
    // 每月日期输入框的字段级错误态
    var monthDaysError by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorText) {
        val message = errorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        errorText = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == null) "新建闹钟" else "编辑闹钟") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            // 保存常驻底部，长表单不需要滚到底
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = {
                            val snoozeMinutes = snoozeMinutesText.toIntOrNull()?.coerceIn(1, 999) ?: 5
                            val snoozeMaxCount = snoozeMaxCountText.toIntOrNull()?.coerceIn(0, 99) ?: 3
                            val intervalMinutes =
                                (intervalHoursText.toIntOrNull() ?: 0) * 60L +
                                    (intervalMinutesText.toIntOrNull() ?: 0).toLong()
                            // 每月多日期：解析校验（全角已实时转换，此处再兜底转换）
                            val monthDays = try {
                                if (type == TaskType.MONTHLY)
                                    com.example.multitimealarm.util.MonthDaysParser.parse(monthDaysText)
                                else emptyList()
                            } catch (e: com.example.multitimealarm.util.MonthDaysParser.InvalidMonthDaysException) {
                                monthDaysError = true
                                errorText = e.message
                                return@Button
                            }
                            // 已过日期检查：当月内早于今天的日期视为无效
                            if (type == TaskType.MONTHLY) {
                                val today = java.time.LocalDate.now()
                                val maxDay = java.time.YearMonth.now().lengthOfMonth()
                                val past = monthDays.filter { it < today.dayOfMonth }
                                val notInMonth = monthDays.filter { it > maxDay }
                                if (past.isNotEmpty()) {
                                    monthDaysError = true
                                    errorText = "日期 ${past.joinToString(",")} 已过期（今天 ${today.dayOfMonth} 号），请重新输入"
                                    return@Button
                                }
                                if (notInMonth.isNotEmpty()) {
                                    monthDaysError = true
                                    errorText = "本月只有 $maxDay 天，日期 ${notInMonth.joinToString(",")} 不存在，请重新输入"
                                    return@Button
                                }
                            }
                            val monthDay = monthDays.firstOrNull() ?: 0

                            // 重复的时间点会导致同一分钟响两次（且续订时第二个会被判为已过而推到次日）
                            val duplicate = times
                                .groupBy { Triple(it.hour, it.minute, it.second) }
                                .entries
                                .firstOrNull { it.value.size > 1 }
                                ?.key

                            val validation = when {
                                times.isEmpty() ->
                                    if (type == TaskType.INTERVAL) "至少添加一个起始时间" else "至少添加一个提醒时间"
                                duplicate != null ->
                                    "提醒时间重复：${formatTimeText(duplicate.first, duplicate.second, duplicate.third)}" +
                                        "，请删除多余的时间点"
                                type == TaskType.WEEKLY && weekdaysMask == 0 -> "请至少选择一个星期"
                                type == TaskType.MONTHLY && monthDay !in 1..31 -> "每月几号需在 1-31 之间"
                                type == TaskType.INTERVAL && intervalMinutes <= 0 -> "间隔时间必须大于 0 分钟"
                                type == TaskType.ONCE && dateEpochDay == null -> "请选择提醒日期"
                                type == TaskType.ONCE && dateEpochDay?.let { isOnceFullyPast(it, times) } == true ->
                                    "所选日期的时间点都已过去，请重新选择日期或时间"
                                else -> null
                            }
                            if (validation != null) {
                                errorText = validation
                                return@Button
                            }

                            val task = AlarmTaskEntity(
                                id = taskId ?: 0L,
                                name = name.ifBlank { "定时提醒" },
                                type = type,
                                weekdaysMask = if (type == TaskType.WEEKLY) weekdaysMask else 0,
                                monthDay = if (type == TaskType.MONTHLY) monthDay else 0,
                                monthDays = if (type == TaskType.MONTHLY)
                                    com.example.multitimealarm.util.MonthDaysParser.toStorageString(monthDays)
                                else "",
                                intervalMinutes = if (type == TaskType.INTERVAL) intervalMinutes else 0,
                                dateEpochDay = if (type == TaskType.ONCE) dateEpochDay else null,
                                // 启用状态不在这里选：保存时按"是否存在未来触发时刻"自动决定
                                enabled = true,
                                vibrate = vibrate,
                                snoozeMinutes = snoozeMinutes,
                                snoozeMaxCount = snoozeMaxCount,
                                note = note,
                                createdAt = if (createdAt != 0L) createdAt else System.currentTimeMillis(),
                            )
                            val timeEntities = times.map {
                                AlarmTimeEntity(
                                    id = it.id, taskId = task.id,
                                    hour = it.hour, minute = it.minute, second = it.second,
                                    enabled = it.enabled,
                                )
                            }
                            // 保存时只要还存在未来的触发时刻就自动启用（不再依赖编辑页开关），
                            // 全部时间点都已过期或被关闭时才保存为停用
                            val hasFuture = timeEntities.any { time ->
                                com.example.multitimealarm.util.TimeUtils.nextTriggerAt(task, time) != null
                            }
                            val finalTask = task.copy(enabled = hasFuture)
                            onSave(finalTask, timeEntities, removedIds.toList())
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = isLoaded,
                    ) {
                        Text("保存", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ============ 提醒时间（视觉重心，放最上面） ============
            SettingSection(
                if (type == TaskType.INTERVAL) "起始时间（${times.size} 个）" else "提醒时间（${times.size} 个）"
            ) {
                if (times.isEmpty()) {
                    Text(
                        text = if (type == TaskType.INTERVAL) "还没有起始时间，点下方按钮添加" else "还没有提醒时间，点下方按钮添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    )
                } else {
                    times.forEachIndexed { index, row ->
                        if (index > 0) SettingDivider()
                        TimeRowItem(
                            row = row,
                            onPick = { editingTimeIndex = index },
                            onToggle = { checked -> times[index] = row.copy(enabled = checked) },
                            onDelete = {
                                if (row.id != 0L) removedIds.add(row.id)
                                times.removeAt(index)
                            },
                        )
                    }
                }
                SettingDivider()
                Row(modifier = Modifier.padding(12.dp)) {
                    FilledTonalButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (type == TaskType.INTERVAL) "添加起始时间" else "添加时间点")
                    }
                }
            }

            // ============ 基本信息 ============
            SettingSection("基本信息") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("闹钟名称") },
                        placeholder = { Text("例如：吃药 / 开会 / 喝水") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（可选）") },
                        placeholder = { Text("例如：饭后服用") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            // ============ 提醒规则 ============
            SettingSection("提醒规则") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 类型选项单行横向滚动，避免折行造成的视觉断裂
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TYPE_OPTIONS.forEach { (option, label) ->
                            FilterChip(
                                selected = type == option,
                                onClick = {
                                    type = option
                                    if (option == TaskType.ONCE && dateEpochDay == null) showDatePicker = true
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        text = when (type) {
                            TaskType.DAILY -> "闹钟会在每天的下列时间点各提醒一次"
                            TaskType.WEEKLY -> "闹钟会在所选星期的下列时间点各提醒一次"
                            TaskType.MONTHLY -> "闹钟会在每月所选日期的下列时间点各提醒一次"
                            TaskType.INTERVAL -> "从起始时间开始，每隔设定的间隔循环提醒一次（持续循环）"
                            TaskType.ONCE -> "闹钟会在指定日期的下列时间点各提醒一次，全部提醒完后自动关闭"
                        },
                        style = MaterialTheme.typography.bodySmall,
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

                    // 每月多日期
                    if (type == TaskType.MONTHLY) {
                        OutlinedTextField(
                            value = monthDaysText,
                            onValueChange = {
                                monthDaysText = com.example.multitimealarm.util.MonthDaysParser
                                    .normalize(it).take(200)
                                monthDaysError = false
                            },
                            label = { Text("每月提醒日期") },
                            placeholder = { Text("如：1,3,9,25 或 1-15 或 2,4,8,21-26") },
                            supportingText = { Text("多日期用英文逗号分隔，支持范围如 1-15（中文逗号自动转换）") },
                            isError = monthDaysError,
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
                                label = { Text("每隔") },
                                suffix = { Text("小时") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = intervalMinutesText,
                                onValueChange = { intervalMinutesText = it.filter(Char::isDigit).take(2) },
                                label = { Text("再加") },
                                suffix = { Text("分钟") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                    }

                    // 仅一次：日期选择
                    if (type == TaskType.ONCE) {
                        Surface(
                            onClick = { showDatePicker = true },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "提醒日期",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = dateEpochDay
                                            ?.let {
                                                LocalDate.ofEpochDay(it)
                                                    .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                                            }
                                            ?: "点击选择日期",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (dateEpochDay == null) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ============ 提醒方式 ============
            SettingSection("提醒方式") {
                SwitchRow(
                    title = "振动",
                    subtitle = "响铃时同时振动提醒",
                    checked = vibrate,
                    onCheckedChange = { vibrate = it },
                )
                SettingDivider()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "贪睡",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = snoozeMinutesText,
                            onValueChange = { snoozeMinutesText = it.filter(Char::isDigit).take(3) },
                            label = { Text("间隔") },
                            suffix = { Text("分钟") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = snoozeMaxCountText,
                            onValueChange = { snoozeMaxCountText = it.filter(Char::isDigit).take(2) },
                            label = { Text("次数上限") },
                            suffix = { Text("次") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    // 提示放在行下方，避免只有一侧带 supportingText 造成两个输入框高度不齐
                    Text(
                        text = "次数上限填 0 表示不限次数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 时/分/秒三列数字滚动选择器
    if (showTimePicker || editingTimeIndex != null) {
        val editingRow = editingTimeIndex?.let { times.getOrNull(it) }
        // 修改模式默认该行当前值，新增模式默认当前时间
        val now = remember(editingTimeIndex) { java.time.LocalTime.now() }
        var pickedHour by remember { mutableStateOf(editingRow?.hour ?: now.hour) }
        var pickedMinute by remember { mutableStateOf(editingRow?.minute ?: now.minute) }
        var pickedSecond by remember { mutableStateOf(editingRow?.second ?: 0) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = {
                Text(
                    when {
                        editingTimeIndex != null -> "修改提醒时间"
                        type == TaskType.INTERVAL -> "选择起始时间"
                        else -> "选择提醒时间"
                    }
                )
            },
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
                            circular = true,
                        )
                        Text("时", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(":", style = MaterialTheme.typography.headlineSmall)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberPickerColumn(
                            value = pickedMinute, count = 60,
                            onValueChange = { pickedMinute = it },
                            modifier = Modifier.width(72.dp),
                            circular = true,
                        )
                        Text("分", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(":", style = MaterialTheme.typography.headlineSmall)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberPickerColumn(
                            value = pickedSecond, count = 60,
                            onValueChange = { pickedSecond = it },
                            modifier = Modifier.width(72.dp),
                            circular = true,
                        )
                        Text("秒", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val editIdx = editingTimeIndex
                    if (editIdx != null && editIdx < times.size) {
                        times[editIdx] = times[editIdx].copy(hour = pickedHour, minute = pickedMinute, second = pickedSecond)
                    } else {
                        times.add(TimeRow(0L, pickedHour, pickedMinute, pickedSecond, enabled = true))
                    }
                    errorText = null
                    showTimePicker = false
                    editingTimeIndex = null
                }) { Text(if (editingTimeIndex != null) "确定" else "添加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    editingTimeIndex = null
                }) { Text("取消") }
            },
        )
    }

    // 年/月/日三列滚动选择器（紧凑小窗）
    if (showDatePicker) {
        val today = remember { LocalDate.now() }
        val yearBase = 2025
        val yearCount = 2099 - yearBase + 1
        var pickedYearOffset by remember { mutableStateOf(today.year - yearBase) }
        var pickedMonth by remember { mutableStateOf(today.monthValue) }
        var pickedDay by remember {
            mutableStateOf(dateEpochDay?.let { LocalDate.ofEpochDay(it).dayOfMonth } ?: today.dayOfMonth)
        }
        // 编辑已有任务时定位到已设日期
        LaunchedEffect(Unit) {
            dateEpochDay?.let {
                val d = LocalDate.ofEpochDay(it)
                if (d.year in yearBase..(yearBase + yearCount - 1)) pickedYearOffset = d.year - yearBase
                pickedMonth = d.monthValue
                pickedDay = d.dayOfMonth
            }
        }
        val monthLen = remember(pickedYearOffset, pickedMonth) {
            java.time.YearMonth.of(yearBase + pickedYearOffset, pickedMonth).lengthOfMonth()
        }
        // 月份/年份变化后收拢超界日期
        LaunchedEffect(pickedYearOffset, pickedMonth, monthLen) {
            if (pickedDay > monthLen) pickedDay = monthLen
        }

        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("选择提醒日期") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberPickerColumn(
                            value = pickedYearOffset, count = yearCount,
                            onValueChange = { pickedYearOffset = it },
                            modifier = Modifier.width(76.dp),
                            label = { String.format(java.util.Locale.US, "%04d", yearBase + it) },
                            circular = true,
                        )
                        Text("年", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberPickerColumn(
                            value = pickedMonth - 1, count = 12,
                            onValueChange = { pickedMonth = it + 1 },
                            modifier = Modifier.width(64.dp),
                            label = { String.format(java.util.Locale.US, "%02d", it + 1) },
                            circular = true,
                        )
                        Text("月", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberPickerColumn(
                            value = pickedDay - 1, count = monthLen,
                            onValueChange = { pickedDay = it + 1 },
                            modifier = Modifier.width(64.dp),
                            label = { String.format(java.util.Locale.US, "%02d", it + 1) },
                            circular = true,
                        )
                        Text("日", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val day = pickedDay.coerceIn(1, monthLen)
                    dateEpochDay = LocalDate.of(yearBase + pickedYearOffset, pickedMonth, day).toEpochDay()
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        )
    }
}

/** 时间点行：大号时间可点改，右侧开关与删除 */
@Composable
private fun TimeRowItem(
    row: TimeRow,
    onPick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatTimeText(row.hour, row.minute, row.second),
            style = MaterialTheme.typography.displaySmall,
            color = if (row.enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = row.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除该时间点",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 卡片内的开关行：标题 + 说明 + 右侧开关 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
