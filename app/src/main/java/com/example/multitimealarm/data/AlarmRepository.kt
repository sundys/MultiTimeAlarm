package com.example.multitimealarm.data

import android.content.Context
import com.example.multitimealarm.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow

/**
 * 数据仓库：写操作后统一触发对应任务的重新调度，
 * 保证数据库状态与系统闹钟注册始终一致。
 */
class AlarmRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).alarmDao()
    private val appContext = context.applicationContext

    fun observeAllTasks(): Flow<List<TaskWithTimes>> = dao.observeAllTasks()

    suspend fun getTaskWithTimes(taskId: Long): TaskWithTimes? =
        dao.getTaskWithTimes(taskId)

    /** 新建任务并注册其全部时间点 */
    suspend fun createTask(task: AlarmTaskEntity, times: List<AlarmTimeEntity>): Long {
        val taskId = dao.insertTask(task)
        times.forEach { dao.insertTime(it.copy(taskId = taskId)) }
        AlarmScheduler.rescheduleTask(appContext, taskId)
        return taskId
    }

    /** 更新任务：同步时间点的增删改，并整体重调度 */
    suspend fun updateTask(task: AlarmTaskEntity, times: List<AlarmTimeEntity>, removedTimeIds: List<Long>) {
        dao.updateTask(task)
        removedTimeIds.forEach { dao.deleteTimeById(it) }
        times.forEach { time ->
            if (time.id == 0L) dao.insertTime(time.copy(taskId = task.id))
            else dao.updateTime(time.copy(taskId = task.id))
        }
        AlarmScheduler.rescheduleTask(appContext, task.id)
    }

    suspend fun setTaskEnabled(taskId: Long, enabled: Boolean) {
        dao.setTaskEnabled(taskId, enabled)
        if (enabled) {
            AlarmScheduler.rescheduleTask(appContext, taskId)
        } else {
            AlarmScheduler.cancelTask(appContext, taskId)
        }
    }

    suspend fun setTimeEnabled(timeId: Long, enabled: Boolean) {
        val time = dao.getTime(timeId) ?: return
        dao.setTimeEnabled(timeId, enabled)
        AlarmScheduler.rescheduleTask(appContext, time.taskId)
    }

    suspend fun deleteTask(taskId: Long) {
        AlarmScheduler.cancelTask(appContext, taskId)
        val taskWithTimes = dao.getTaskWithTimes(taskId) ?: return
        dao.deleteTask(taskWithTimes.task)
    }
}
