package com.example.multitimealarm

import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.TaskType
import com.example.multitimealarm.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar

class TimeUtilsTest {

    private fun at(year: Int, month1: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month1 - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun task(
        type: TaskType,
        weekdaysMask: Int = 0,
        monthDay: Int = 0,
        intervalMinutes: Long = 0,
        dateEpochDay: Long? = null,
        enabled: Boolean = true,
    ) = AlarmTaskEntity(
        id = 1, name = "t", type = type,
        weekdaysMask = weekdaysMask, monthDay = monthDay,
        intervalMinutes = intervalMinutes, dateEpochDay = dateEpochDay,
        enabled = enabled,
    )

    private fun time(hour: Int, minute: Int, enabled: Boolean = true) =
        AlarmTimeEntity(id = 1, taskId = 1, hour = hour, minute = minute, enabled = enabled)

    // 一.1 单次闹钟：设定某天特定时间响一次
    @Test
    fun `once fires at given date and time`() {
        val t = task(TaskType.ONCE, dateEpochDay = LocalDate.of(2026, 8, 29).toEpochDay())
        val next = TimeUtils.nextTriggerAt(t, time(15, 30), now = at(2026, 8, 29, 10, 0))
        assertEquals(at(2026, 8, 29, 15, 30), next)
    }

    @Test
    fun `once in the past returns null`() {
        val t = task(TaskType.ONCE, dateEpochDay = LocalDate.of(2026, 8, 28).toEpochDay())
        val next = TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0))
        assertNull(next)
    }

    // 二.1 每日提醒
    @Test
    fun `daily later today`() {
        val t = task(TaskType.DAILY)
        assertEquals(
            at(2026, 8, 29, 12, 0),
            TimeUtils.nextTriggerAt(t, time(12, 0), now = at(2026, 8, 29, 10, 0)),
        )
    }

    @Test
    fun `daily rolls to tomorrow`() {
        val t = task(TaskType.DAILY)
        assertEquals(
            at(2026, 8, 30, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)),
        )
    }

    // 一.2 重复闹钟：按星期循环
    @Test
    fun `weekly picks next selected weekday`() {
        // 2026-08-29 是周六；只选周一 -> 下一次是 2026-08-31
        val monday = TimeUtils.weekdayBit(Calendar.MONDAY)
        val t = task(TaskType.WEEKLY, weekdaysMask = monday)
        assertEquals(
            at(2026, 8, 31, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)),
        )
    }

    @Test
    fun `weekly workdays skip weekend`() {
        // 周五 2026-08-28 18:00 之后，下一次工作日是周一 2026-08-31
        val t = task(TaskType.WEEKLY, weekdaysMask = AlarmTaskEntity.MASK_WEEKDAYS)
        assertEquals(
            at(2026, 8, 31, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 28, 18, 0)),
        )
    }

    @Test
    fun `weekly every day equals daily`() {
        val t = task(TaskType.WEEKLY, weekdaysMask = AlarmTaskEntity.MASK_EVERY_DAY)
        assertEquals(
            at(2026, 8, 30, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)),
        )
    }

    @Test
    fun `weekly same day later time still today`() {
        // 周六 10:00 -> 选周六、时间 20:00 -> 今天 20:00
        val t = task(TaskType.WEEKLY, weekdaysMask = TimeUtils.weekdayBit(Calendar.SATURDAY))
        assertEquals(
            at(2026, 8, 29, 20, 0),
            TimeUtils.nextTriggerAt(t, time(20, 0), now = at(2026, 8, 29, 10, 0)),
        )
    }

    // 二.2 每月循环
    @Test
    fun `monthly next month when day passed`() {
        val t = task(TaskType.MONTHLY, monthDay = 1)
        assertEquals(
            at(2026, 9, 1, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)),
        )
    }

    @Test
    fun `monthly skips months without the day`() {
        // 2月1日之后设每月31日：1月31日已过、2月无31日 -> 3月31日
        val t = task(TaskType.MONTHLY, monthDay = 31)
        assertEquals(
            at(2026, 3, 31, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 2, 1, 10, 0)),
        )
    }

    // 二.3 间隔循环
    @Test
    fun `interval every 2 hours from anchor`() {
        val t = task(TaskType.INTERVAL, intervalMinutes = 120)
        // 起始 08:00，现在 10:30 -> 12:00
        assertEquals(
            at(2026, 8, 29, 12, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 30)),
        )
    }

    @Test
    fun `interval before anchor triggers at anchor`() {
        val t = task(TaskType.INTERVAL, intervalMinutes = 120)
        assertEquals(
            at(2026, 8, 29, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 7, 30)),
        )
    }

    @Test
    fun `interval crosses midnight`() {
        val t = task(TaskType.INTERVAL, intervalMinutes = 120)
        // 08:00 起、每 2 小时，23:50 时的下一次是次日 00:00
        assertEquals(
            at(2026, 8, 30, 0, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 23, 50)),
        )
    }

    // 开关与配置校验
    @Test
    fun `disabled task returns null`() {
        val t = task(TaskType.DAILY, enabled = false)
        assertNull(TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)))
    }

    @Test
    fun `disabled time returns null`() {
        val t = task(TaskType.DAILY)
        assertNull(TimeUtils.nextTriggerAt(t, time(8, 0, enabled = false), now = at(2026, 8, 29, 10, 0)))
    }

    @Test
    fun `weekly with no selected day returns null`() {
        val t = task(TaskType.WEEKLY, weekdaysMask = 0)
        assertNull(TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)))
    }

    @Test
    fun `interval with zero interval returns null`() {
        val t = task(TaskType.INTERVAL, intervalMinutes = 0)
        assertNull(TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)))
    }
}
