package com.example.multitimealarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multitimealarm.MultiTimeAlarmApp
import com.example.multitimealarm.data.AlarmRepository
import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.TaskWithTimes
import com.example.multitimealarm.util.TimeUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlarmRepository(application)

    /** 闹钟列表：按下一次响铃时间升序（多时间点取最早的一个，无未来触发的排最后） */
    val tasks: StateFlow<List<TaskWithTimes>> = repository.observeAllTasks()
        .map { list ->
            list.sortedBy { item ->
                item.times
                    .mapNotNull { time -> TimeUtils.nextTriggerAt(item.task, time) }
                    .minOrNull() ?: Long.MAX_VALUE
            }
        }
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
        viewModelScope.launch { repository.setTaskEnabled(taskId, enabled) }
    }

    fun setTimeEnabled(timeId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setTimeEnabled(timeId, enabled) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { repository.deleteTask(taskId) }
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

    companion object {
        /** 列表页展示：任务的下一次响铃描述（空 = 无未来提醒） */
        fun nextAlarmText(taskWithTimes: TaskWithTimes): String? {
            val next = taskWithTimes.times
                .mapNotNull { TimeUtils.nextTriggerAt(taskWithTimes.task, it) }
                .minOrNull() ?: return null
            return android.text.format.DateFormat.format("MM-dd HH:mm", next).toString()
        }
    }
}
