package com.example.multitimealarm.ui

import android.app.Application
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multitimealarm.MultiTimeAlarmApp
import com.example.multitimealarm.data.AlarmRepository
import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.TaskWithTimes
import com.example.multitimealarm.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 列表页 UI 模型：任务数据 + 预计算好的下次响铃时刻与文本（后台线程一次算好，渲染时直接用） */
data class TaskListItem(val data: TaskWithTimes, val next: Long?, val nextText: String?)

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlarmRepository(application)

    /**
     * 乐观开关覆盖层：点击先翻 UI，数据库写入并重发射后自动收敛。
     * 条目不主动清除——当覆盖值与库中一致时自然失效，
     * 避免 Flow 重发射到达前移除覆盖导致开关闪回旧值。
     */
    private val pendingEnabled = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

    /** 闹钟列表：启用中按下一次响铃时间升序，已停止沉底；排序与文本计算在后台线程 */
    val tasks: StateFlow<List<TaskListItem>> =
        combine(repository.observeAllTasks(), pendingEnabled) { list, pending ->
            list.map { entry ->
                pending[entry.task.id]
                    ?.takeIf { it != entry.task.enabled }
                    ?.let { entry.copy(task = entry.task.copy(enabled = it)) }
                    ?: entry
            }
        }
            .map { list ->
                list.map { entry ->
                    val next = entry.times
                        .mapNotNull { time -> TimeUtils.nextTriggerAt(entry.task, time) }
                        .minOrNull()
                    TaskListItem(entry, next, next?.let { formatNextText(it) })
                }.sortedWith(
                    compareBy<TaskListItem> { !it.data.task.enabled }
                        .thenBy { it.next ?: Long.MAX_VALUE }
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun loadTask(taskId: Long): TaskWithTimes? =
        repository.getTaskWithTimes(taskId)

    fun saveTask(task: AlarmTaskEntity, times: List<AlarmTimeEntity>, removedTimeIds: List<Long>) {
        viewModelScope.launch {
            if (task.id == 0L) {
                repository.createTask(task, times)
            } else {
                repository.updateTask(task, times, removedTimeIds)
            }
        }
    }

    fun setTaskEnabled(taskId: Long, enabled: Boolean) {
        pendingEnabled.update { it + (taskId to enabled) }
        viewModelScope.launch { repository.setTaskEnabled(taskId, enabled) }
    }

    fun setTimeEnabled(timeId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setTimeEnabled(timeId, enabled) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { repository.deleteTask(taskId) }
    }

    /** 清除全部闹钟（含系统注册与数据库），回调清除的数量 */
    fun clearAllAlarms(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            onDone(repository.deleteAll())
        }
    }

    /** 导出全部闹钟 JSON（在 IO 线程调用） */
    suspend fun exportAlarmsJson(): String = repository.exportJson()

    /** 从 JSON 恢复（覆盖当前），onResult 回调 (恢复数, null) 或 (0, 错误信息) */
    fun restoreAlarmsJson(json: String, onResult: (Int, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.restoreJson(json) }
                .onSuccess { onResult(it, null) }
                .onFailure { onResult(0, it.message ?: "恢复失败") }
        }
    }

    /** 小憩倒计时：N 分钟后响一次，实现为一次性单时间点任务（自动纳入开机恢复等既有机制） */
    fun createNapTask(minutes: Int) {
        viewModelScope.launch {
            val trigger = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.MINUTE, minutes)
            }
            val dateEpochDay = java.time.Instant.ofEpochMilli(trigger.timeInMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().toEpochDay()
            val task = AlarmTaskEntity(
                name = "小憩 $minutes 分钟",
                type = com.example.multitimealarm.data.TaskType.ONCE,
                dateEpochDay = dateEpochDay,
                vibrate = true,
                isNap = true,
            )
            val time = AlarmTimeEntity(
                taskId = 0L,
                hour = trigger.get(java.util.Calendar.HOUR_OF_DAY),
                minute = trigger.get(java.util.Calendar.MINUTE),
            )
            repository.createTask(task, listOf(time))
        }
    }

    private companion object {
        /** 下一次响铃的展示格式：9月4日 13:41 */
        fun formatNextText(triggerAt: Long): String =
            DateFormat.format("M月d日 HH:mm", triggerAt).toString()
    }
}
