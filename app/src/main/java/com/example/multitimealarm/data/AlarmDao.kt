package com.example.multitimealarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Transaction
    @Query("SELECT * FROM alarm_tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<TaskWithTimes>>

    @Transaction
    @Query("SELECT * FROM alarm_tasks WHERE id = :taskId")
    suspend fun getTaskWithTimes(taskId: Long): TaskWithTimes?

    @Transaction
    @Query("SELECT * FROM alarm_tasks WHERE enabled = 1")
    suspend fun getEnabledTasks(): List<TaskWithTimes>

    @Transaction
    @Query("SELECT * FROM alarm_tasks ORDER BY createdAt DESC")
    suspend fun getAllTasks(): List<TaskWithTimes>

    @Query("DELETE FROM alarm_tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT * FROM alarm_times WHERE id = :timeId")
    suspend fun getTime(timeId: Long): AlarmTimeEntity?

    @Query("SELECT * FROM alarm_times WHERE taskId = :taskId")
    suspend fun getTimesOfTask(taskId: Long): List<AlarmTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AlarmTaskEntity): Long

    @Update
    suspend fun updateTask(task: AlarmTaskEntity)

    @Delete
    suspend fun deleteTask(task: AlarmTaskEntity)

    @Query("UPDATE alarm_tasks SET enabled = :enabled WHERE id = :taskId")
    suspend fun setTaskEnabled(taskId: Long, enabled: Boolean)

    /**
     * 插入时间点。与既有时间点 (taskId, 时, 分, 秒) 冲突时忽略。
     *
     * 这里刻意不用 REPLACE：REPLACE 会删旧行再插新行、拿到新 id，
     * 而 id 就是系统闹钟的 PendingIntent requestCode，id 一变就留下一个无法取消的旧注册。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTime(time: AlarmTimeEntity): Long

    @Update
    suspend fun updateTime(time: AlarmTimeEntity)

    @Delete
    suspend fun deleteTime(time: AlarmTimeEntity)

    @Query("DELETE FROM alarm_times WHERE id = :timeId")
    suspend fun deleteTimeById(timeId: Long)

    @Query("UPDATE alarm_times SET enabled = :enabled WHERE id = :timeId")
    suspend fun setTimeEnabled(timeId: Long, enabled: Boolean)

    @Query("UPDATE alarm_times SET snoozeCount = :count WHERE id = :timeId")
    suspend fun setSnoozeCount(timeId: Long, count: Int)

    // ============ 响铃历史 ============

    @Insert
    suspend fun insertHistory(entry: AlarmHistoryEntity): Long

    /** 最近 500 条响铃记录，新的在前 */
    @Query("SELECT * FROM alarm_history ORDER BY triggeredAt DESC, id DESC LIMIT 500")
    fun observeHistory(): Flow<List<AlarmHistoryEntity>>

    /** 仅保留最近的 [keep] 条，避免记录无限增长 */
    @Query(
        "DELETE FROM alarm_history WHERE id NOT IN " +
            "(SELECT id FROM alarm_history ORDER BY triggeredAt DESC, id DESC LIMIT :keep)"
    )
    suspend fun pruneHistory(keep: Int)

    @Query("DELETE FROM alarm_history")
    suspend fun clearHistory()
}
