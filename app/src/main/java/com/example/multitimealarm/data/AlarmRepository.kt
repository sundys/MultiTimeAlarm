package com.example.multitimealarm.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.multitimealarm.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow

/**
 * 数据仓库：写操作后统一触发对应任务的重新调度，
 * 保证数据库状态与系统闹钟注册始终一致。
 */
class AlarmRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.alarmDao()
    private val appContext = context.applicationContext

    private companion object {
        const val TAG = "AlarmRepository"
    }

    fun observeAllTasks(): Flow<List<TaskWithTimes>> = dao.observeAllTasks()

    /** 响铃历史（最近 500 条，新的在前） */
    fun observeHistory(): Flow<List<AlarmHistoryEntity>> = dao.observeHistory()

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun getTaskWithTimes(taskId: Long): TaskWithTimes? =
        dao.getTaskWithTimes(taskId)

    /** 新建任务并注册其全部时间点（任务与时间点同一事务，避免中途失败留下半个闹钟） */
    suspend fun createTask(task: AlarmTaskEntity, times: List<AlarmTimeEntity>): Long {
        val taskId = db.withTransaction {
            val id = dao.insertTask(task)
            times.forEach { dao.insertTime(it.copy(taskId = id)) }
            id
        }
        AlarmScheduler.rescheduleTask(appContext, taskId)
        return taskId
    }

    /** 更新任务：同步时间点的增删改（单事务），并整体重调度 */
    suspend fun updateTask(task: AlarmTaskEntity, times: List<AlarmTimeEntity>, removedTimeIds: List<Long>) {
        db.withTransaction {
            dao.updateTask(task)
            removedTimeIds.forEach { dao.deleteTimeById(it) }
            times.forEach { time ->
                if (time.id == 0L) dao.insertTime(time.copy(taskId = task.id))
                else dao.updateTime(time.copy(taskId = task.id))
            }
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

    suspend fun deleteTask(taskId: Long) {
        AlarmScheduler.cancelTask(appContext, taskId)
        val taskWithTimes = dao.getTaskWithTimes(taskId) ?: return
        dao.deleteTask(taskWithTimes.task)
    }

    /** 清除全部闹钟：先取消全部系统闹钟注册，再清空数据库。返回清除的数量 */
    suspend fun deleteAll(): Int {
        val all = dao.getAllTasks()
        all.forEach { AlarmScheduler.cancelTask(appContext, it.task.id) }
        dao.deleteAllTasks()
        Log.i("AlarmRepository", "已清除全部闹钟（${all.size} 个）")
        return all.size
    }

    /** 导出全部闹钟 JSON（编解码与校验集中在 AlarmBackup，便于单测覆盖） */
    suspend fun exportJson(): String = AlarmBackup.encode(dao.getAllTasks())

    /**
     * 从 JSON 恢复（覆盖当前全部闹钟）。返回恢复的任务数；格式非法抛 [org.json.JSONException]。
     *
     * 顺序很关键：
     * 1. 先把整份 JSON 解析校验完 —— 备份文件有问题时在这里就抛出，**用户现有闹钟一条都不会动**
     * 2. 清空与写入放进同一个事务，中途失败整体回滚
     * 3. 系统闹钟的取消/重注册放在事务提交之后（旧的 timeId 提前记下来，
     *    因为删库后就查不到了；中途进程被杀由冷启动的兜底重调度补上）
     */
    suspend fun restoreJson(json: String): Int {
        val parsed = AlarmBackup.decode(json)
        val oldTimeIds = dao.getAllTasks().flatMap { entry -> entry.times.map { it.id } }
        val newTaskIds = mutableListOf<Long>()
        db.withTransaction {
            dao.deleteAllTasks()
            parsed.entries.forEach { entry ->
                val taskId = dao.insertTask(entry.task)
                newTaskIds.add(taskId)
                entry.times.forEach { dao.insertTime(it.copy(taskId = taskId)) }
            }
        }
        oldTimeIds.forEach { AlarmScheduler.cancelTime(appContext, it) }
        newTaskIds.forEach { AlarmScheduler.rescheduleTask(appContext, it) }
        if (parsed.skippedTasks > 0 || parsed.skippedTimes > 0) {
            Log.w(
                TAG,
                "恢复时跳过 ${parsed.skippedTasks} 条无有效时间点的任务、${parsed.skippedTimes} 个越界时间点",
            )
        }
        Log.i(TAG, "已恢复 ${parsed.count} 个闹钟")
        return parsed.count
    }
}
