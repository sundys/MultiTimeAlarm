package com.example.multitimealarm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 闹钟响铃历史：每次真实到点触发写一条（贪睡不重复记录）。
 *
 * 任务名、时间点都做快照保存——即使之后闹钟被修改或删除，历史记录仍然可读，
 * 用于回看某天到底响过哪些提醒、是否有漏掉的。
 */
@Entity(
    tableName = "alarm_history",
    indices = [Index("triggeredAt")],
)
data class AlarmHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 触发时对应的任务 id（任务可能已删除，仅作关联参考） */
    val taskId: Long,
    /** 任务名快照 */
    val taskName: String,
    /** 时间点 id 快照 */
    val timeId: Long,
    /** 计划触发的时 / 分 / 秒 */
    val hour: Int,
    val minute: Int,
    val second: Int,
    /** 实际响铃时间（毫秒） */
    val triggeredAt: Long = System.currentTimeMillis(),
)
