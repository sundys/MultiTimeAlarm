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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTime(time: AlarmTimeEntity): Long

    @Update
    suspend fun updateTime(time: AlarmTimeEntity)

    @Delete
    suspend fun deleteTime(time: AlarmTimeEntity)

    @Query("DELETE FROM alarm_times WHERE id = :timeId")
    suspend fun deleteTimeById(timeId: Long)

    @Query("DELETE FROM alarm_times WHERE taskId = :taskId AND id NOT IN (:keepIds)")
    suspend fun deleteTimesNotIn(taskId: Long, keepIds: List<Long>)

    @Query("UPDATE alarm_times SET enabled = :enabled WHERE id = :timeId")
    suspend fun setTimeEnabled(timeId: Long, enabled: Boolean)

    @Query("UPDATE alarm_times SET snoozeCount = :count WHERE id = :timeId")
    suspend fun setSnoozeCount(timeId: Long, count: Int)
}
