package com.example.multitimealarm.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.multitimealarm.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

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

    /** 清除全部闹钟：先取消全部系统闹钟注册，再清空数据库。返回清除的数量 */
    suspend fun deleteAll(): Int {
        val all = dao.getAllTasks()
        all.forEach { AlarmScheduler.cancelTask(appContext, it.task.id) }
        dao.deleteAllTasks()
        Log.i("AlarmRepository", "已清除全部闹钟（${all.size} 个）")
        return all.size
    }

    /** 导出全部闹钟为 JSON 字符串 */
    suspend fun exportJson(): String {
        val all = dao.getAllTasks()
        val arr = JSONArray()
        all.forEach { entry ->
            val o = JSONObject()
            o.put("name", entry.task.name)
            o.put("type", entry.task.type.name)
            o.put("weekdaysMask", entry.task.weekdaysMask)
            o.put("monthDay", entry.task.monthDay)
            o.put("monthDays", entry.task.monthDays)
            o.put("intervalMinutes", entry.task.intervalMinutes)
            entry.task.dateEpochDay?.let { o.put("dateEpochDay", it) }
            o.put("enabled", entry.task.enabled)
            o.put("vibrate", entry.task.vibrate)
            o.put("snoozeMinutes", entry.task.snoozeMinutes)
            o.put("snoozeMaxCount", entry.task.snoozeMaxCount)
            o.put("isNap", entry.task.isNap)
            o.put("note", entry.task.note)
            val times = JSONArray()
            entry.times.forEach { t ->
                times.put(
                    JSONObject()
                        .put("hour", t.hour)
                        .put("minute", t.minute)
                        .put("second", t.second)
                        .put("enabled", t.enabled)
                )
            }
            o.put("times", times)
            arr.put(o)
        }
        return JSONObject()
            .put("app", "MultiTimeAlarm")
            .put("version", 1)
            .put("count", all.size)
            .put("tasks", arr)
            .toString(2)
    }

    /**
     * 从 JSON 恢复（覆盖当前全部闹钟）：取消现有注册 → 清库 → 导入（单事务）→ 重新调度。
     * 返回恢复的任务数；格式非法抛 org.json.JSONException。
     */
    suspend fun restoreJson(json: String): Int {
        val root = JSONObject(json)
        val arr = root.getJSONArray("tasks")
        // 先清空当前
        deleteAll()
        // 数据导入包进单事务，中途失败整体回滚，不会留下半份数据
        val restoredIds = AppDatabase.getInstance(appContext).withTransaction {
            val ids = mutableListOf<Long>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val task = AlarmTaskEntity(
                    name = o.optString("name", "定时提醒"),
                    type = runCatching { TaskType.valueOf(o.getString("type")) }
                        .getOrDefault(TaskType.DAILY),
                    weekdaysMask = o.optInt("weekdaysMask", 0),
                    monthDay = o.optInt("monthDay", 0),
                    monthDays = o.optString("monthDays", ""),
                    intervalMinutes = o.optLong("intervalMinutes", 0),
                    dateEpochDay = if (o.has("dateEpochDay")) o.getLong("dateEpochDay") else null,
                    enabled = o.optBoolean("enabled", true),
                    vibrate = o.optBoolean("vibrate", true),
                    snoozeMinutes = o.optInt("snoozeMinutes", 5),
                    snoozeMaxCount = o.optInt("snoozeMaxCount", 3),
                    isNap = o.optBoolean("isNap", false),
                    note = o.optString("note", ""),
                )
                val taskId = dao.insertTask(task)
                ids.add(taskId)
                val times = o.optJSONArray("times") ?: JSONArray()
                for (j in 0 until times.length()) {
                    val t = times.getJSONObject(j)
                    dao.insertTime(
                        AlarmTimeEntity(
                            taskId = taskId,
                            hour = t.optInt("hour", 8),
                            minute = t.optInt("minute", 0),
                            second = t.optInt("second", 0),
                            enabled = t.optBoolean("enabled", true),
                        )
                    )
                }
            }
            ids
        }
        // 系统闹钟注册放事务外，逐任务调度
        restoredIds.forEach { AlarmScheduler.rescheduleTask(appContext, it) }
        Log.i("AlarmRepository", "已恢复 ${restoredIds.size} 个闹钟")
        return restoredIds.size
    }
}
