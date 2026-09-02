package com.example.multitimealarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 任务类型：每日 / 按星期 / 每月 / 间隔循环 / 仅一次 */
enum class TaskType { DAILY, WEEKLY, MONTHLY, INTERVAL, ONCE }

/**
 * 定时任务。一个任务可包含多个提醒时间点（见 [AlarmTimeEntity]）。
 *
 * 各类型的附加字段：
 * - WEEKLY：[weekdaysMask] 位掩码，bit0=周一 … bit6=周日
 * - MONTHLY：[monthDays] 每月多个日期（如 1,3,9,25 或 1-15 组合）
 * - INTERVAL：[intervalMinutes] 间隔分钟数，时间点作为起始锚点
 * - ONCE：[dateEpochDay] 所有时间点共用的目标日期
 * - DAILY：无附加字段
 *
 * 贪睡配置：[snoozeMinutes] 贪睡间隔（分钟）；[snoozeMaxCount] 贪睡次数上限，0 表示不限。
 */
@Entity(tableName = "alarm_tasks")
data class AlarmTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TaskType = TaskType.DAILY,
    val weekdaysMask: Int = 0,
    val monthDay: Int = 0,
    /** 每月多日期（逗号分隔升序，如 "1,3,9,25" 或 "2,4,21,22,23,24,25,26"）；为空时回退 monthDay 单日 */
    val monthDays: String = "",
    val intervalMinutes: Long = 0,
    val dateEpochDay: Long? = null,
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    val snoozeMaxCount: Int = 3,
    /** 小憩条目：首页归入"小憩"Tab 展示 */
    val isNap: Boolean = false,
    /** 闹钟备注 */
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /** 星期位掩码：周一=bit0 … 周日=bit6 */
        const val MASK_EVERY_DAY = 0b01111111
        const val MASK_WEEKDAYS = 0b0011111   // 周一至周五
        const val MASK_WEEKEND = 0b1100000    // 周六、周日
    }
}
