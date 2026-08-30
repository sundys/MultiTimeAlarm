package com.example.multitimealarm.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 任务下的一个提醒时间点。
 * DAILY 任务每天在 hour:minute 触发；ONCE 任务在任务指定日期的 hour:minute 触发一次，
 * 触发后 enabled 置为 false，任务下所有时间点都触发完则任务自动关闭。
 */
@Entity(
    tableName = "alarm_times",
    foreignKeys = [
        ForeignKey(
            entity = AlarmTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("taskId")],
)
data class AlarmTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val hour: Int,
    val minute: Int,
    /** 秒（大多数闹钟为 0；选择器支持到秒） */
    val second: Int = 0,
    val enabled: Boolean = true,
    /** 自上一次真实触发以来已贪睡的次数（用于贪睡次数上限控制） */
    val snoozeCount: Int = 0,
)
