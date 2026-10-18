package com.example.multitimealarm

import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.IntervalUnit
import com.example.multitimealarm.data.TaskType
import com.example.multitimealarm.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        monthDays: String = "",
        monthLastDay: Boolean = false,
        intervalMinutes: Long = 0,
        intervalValue: Long = intervalMinutes,
        intervalUnit: IntervalUnit = IntervalUnit.MINUTE,
        dateEpochDay: Long? = null,
        enabled: Boolean = true,
        createdAt: Long = at(2026, 8, 29, 8, 0),
    ) = AlarmTaskEntity(
        id = 1, name = "t", type = type,
        weekdaysMask = weekdaysMask, monthDay = monthDay, monthDays = monthDays,
        monthLastDay = monthLastDay,
        intervalMinutes = intervalMinutes, intervalValue = intervalValue, intervalUnit = intervalUnit,
        dateEpochDay = dateEpochDay,
        enabled = enabled, createdAt = createdAt,
    )

    private fun time(hour: Int, minute: Int, enabled: Boolean = true) =
        AlarmTimeEntity(id = 1, taskId = 1, hour = hour, minute = minute, enabled = enabled)

    private fun time(hour: Int, minute: Int, second: Int) =
        AlarmTimeEntity(id = 1, taskId = 1, hour = hour, minute = minute, second = second)

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
        val t = task(TaskType.MONTHLY, monthDays = "1")
        assertEquals(
            at(2026, 9, 1, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)),
        )
    }

    @Test
    fun `monthly skips months without the day`() {
        // 2月1日之后设每月31日：1月31日已过、2月无31日 -> 3月31日
        val t = task(TaskType.MONTHLY, monthDays = "31")
        assertEquals(
            at(2026, 3, 31, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 2, 1, 10, 0)),
        )
    }

    // 每月多日期
    @Test
    fun `monthly multi days picks earliest upcoming in current month`() {
        // 今天 8/29 10:00，日期 1,3,9,25 -> 9/1 为下月第一个
        val t = task(TaskType.MONTHLY, monthDays = "1,3,9,25")
        assertEquals(
            at(2026, 9, 1, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)),
        )
    }

    @Test
    fun `monthly multi days later this month first`() {
        // 今天 8/29，日期 30,31 -> 8/30 08:00
        val t = task(TaskType.MONTHLY, monthDays = "30,31")
        assertEquals(
            at(2026, 8, 30, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)),
        )
    }

    @Test
    fun `monthly multi days rolls to next month after all passed`() {
        // 今天 8/29 之后设 1,3 -> 下月 9/1
        val t = task(TaskType.MONTHLY, monthDays = "1,3")
        assertEquals(
            at(2026, 9, 1, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 23, 0)),
        )
    }

    @Test
    fun `monthly multi days triggered today rolls to next selected day`() {
        // 今天 8/29 13:41 已过，日期 12,4,8,21-26 排序后最近的是 9/21（8/29 当天无更早未过期日）
        val t = task(TaskType.MONTHLY, monthDays = "21-26,12,4,8")
        // 8/29 13:41 之后：8 月剩余 29-31 均不在集合中，下一个是 9/21 13:41
        assertEquals(
            at(2026, 9, 4, 13, 41),
            TimeUtils.nextTriggerAt(t, time(13, 41), now = at(2026, 8, 29, 13, 41)),
        )
    }

    @Test
    fun `monthly multi days same day later time still valid`() {
        // 今天 8/29 10:00，日期集合含 29 且时间为 13:41 -> 今天 13:41 仍有效
        val t = task(TaskType.MONTHLY, monthDays = "29,15")
        assertEquals(
            at(2026, 8, 29, 13, 41),
            TimeUtils.nextTriggerAt(t, time(13, 41), now = at(2026, 8, 29, 10, 0)),
        )
    }

    @Test
    fun `monthly falls back to single monthDay when monthDays empty`() {
        val t = task(TaskType.MONTHLY, monthDay = 1)
        assertEquals(
            at(2026, 9, 1, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 10, 0)),
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

    @Test
    fun `formatHhmm pads hour and minute to two digits`() {
        assertEquals("08:05", TimeUtils.formatHhmm(at(2026, 8, 20, 8, 5)))
    }

    @Test
    fun `formatHhmm keeps 24 hour clock in the evening`() {
        assertEquals("23:59", TimeUtils.formatHhmm(at(2026, 8, 20, 23, 59)))
    }

    @Test
    fun `formatMonthDay prints month and day without padding`() {
        assertEquals("8月20日", TimeUtils.formatMonthDay(at(2026, 8, 20, 8, 5)))
        assertEquals("12月3日", TimeUtils.formatMonthDay(at(2026, 12, 3, 0, 0)))
    }

    @Test
    fun `monthly keeps the seconds of the time point`() {
        val t = task(TaskType.MONTHLY, monthDays = "1")
        val trigger = TimeUtils.nextTriggerAt(t, time(8, 0, 30), now = at(2026, 8, 20, 10, 0))
        assertNotNull(trigger)
        // 秒必须保留，而不是被截断到整分
        assertEquals(30_000L, trigger!! % 60_000L)
    }

    @Test
    fun `interval in days crosses month boundary`() {
        val t = task(
            TaskType.INTERVAL, intervalValue = 2, intervalUnit = IntervalUnit.DAY,
            createdAt = at(2026, 8, 29, 8, 0),
        )
        assertEquals(
            at(2026, 8, 31, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 29, 9, 0)),
        )
        assertEquals(
            at(2026, 9, 2, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 8, 31, 9, 0)),
        )
    }

    @Test
    fun `interval in months uses real calendar months without drift`() {
        val t = task(
            TaskType.INTERVAL, intervalValue = 1, intervalUnit = IntervalUnit.MONTH,
            createdAt = at(2026, 1, 31, 8, 0),
        )
        // 1 月 31 日 + 1 个月 = 2 月 28 日（2026 非闰年）
        assertEquals(
            at(2026, 2, 28, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 2, 1, 0, 0)),
        )
        // 每次都从锚点重算，2 月之后应回到 3 月 31 日，而不是漂移成 28 日
        assertEquals(
            at(2026, 3, 31, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 2, 28, 9, 0)),
        )
    }

    @Test
    fun `monthly last day follows actual month length`() {
        val t = task(TaskType.MONTHLY, monthLastDay = true)
        assertEquals(
            at(2026, 2, 28, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 2, 1, 0, 0)),
        )
        assertEquals(
            at(2026, 3, 31, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2026, 2, 28, 9, 0)),
        )
        // 闰年 2 月为 29 日
        assertEquals(
            at(2028, 2, 29, 8, 0),
            TimeUtils.nextTriggerAt(t, time(8, 0), now = at(2028, 2, 1, 0, 0)),
        )
    }
}
