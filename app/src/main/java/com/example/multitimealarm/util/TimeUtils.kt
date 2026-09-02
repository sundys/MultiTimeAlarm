package com.example.multitimealarm.util

import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.TaskType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar

object TimeUtils {

    /** 默认贪睡间隔（分钟），任务未自定义时使用 */
    const val SNOOZE_MINUTES = 5L

    /**
     * 计算一个时间点的下一次触发时刻（毫秒）。已过期或不可触发时返回 null。
     *
     * - DAILY：今天 hour:minute，若已过则为明天
     * - WEEKLY：下一个选中的星期几的 hour:minute（最多向后看 7 天）
     * - MONTHLY：未来某个月的 monthDay 日的 hour:minute（该月无此日期则跳过，最多看 4 年）
     * - INTERVAL：以 hour:minute 为起始锚点，之后每 intervalMinutes 一次（持续循环，含跨天）
     * - ONCE：任务指定日期的 hour:minute，若已过则返回 null（不再触发）
     */
    fun nextTriggerAt(
        task: AlarmTaskEntity,
        time: AlarmTimeEntity,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        if (!task.enabled || !time.enabled) return null
        return when (task.type) {
            TaskType.DAILY -> nextDaily(now, time)
            TaskType.WEEKLY -> nextWeekly(now, task.weekdaysMask, time)
            TaskType.MONTHLY -> nextMonthly(now, task, time)
            TaskType.INTERVAL -> nextInterval(now, task.intervalMinutes, time)
            TaskType.ONCE -> nextOnce(now, task.dateEpochDay, time)
        }
    }

    private fun calendarAt(now: Long, time: AlarmTimeEntity): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, time.second)
            set(Calendar.MILLISECOND, 0)
        }

    private fun nextDaily(now: Long, time: AlarmTimeEntity): Long {
        val cal = calendarAt(now, time)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun nextWeekly(now: Long, weekdaysMask: Int, time: AlarmTimeEntity): Long? {
        if (weekdaysMask == 0) return null
        val cal = calendarAt(now, time)
        repeat(8) { // 从今天起最多看 7 天，共 8 个候选日
            val dayBit = weekdayBit(cal.get(Calendar.DAY_OF_WEEK))
            if (weekdaysMask and dayBit != 0 && cal.timeInMillis > now) {
                return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    /** java.util.Calendar 的星期常量映射到掩码位：周一=bit0 … 周日=bit6 */
    fun weekdayBit(calendarDayOfWeek: Int): Int {
        // Calendar.SUNDAY=1 … SATURDAY=7；周一(2)->bit0，周日(1)->bit6
        val bitIndex = (calendarDayOfWeek + 5) % 7
        return 1 shl bitIndex
    }

    private fun nextMonthly(now: Long, task: AlarmTaskEntity, time: AlarmTimeEntity): Long? {
        val days = effectiveMonthlyDays(task)
        if (days.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val start = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        var year = start.year
        var month = start.monthValue
        repeat(48) { // 最多向后看 4 年
            // 当月全部候选时刻中，取晚于 now 的最早一个；当月已全部过期则看下月
            val maxDay = YearMonth.of(year, month).lengthOfMonth()
            val upcoming = days
                .filter { it in 1..maxDay }
                .map { d ->
                    LocalDate.of(year, month, d).atTime(time.hour, time.minute, 0)
                        .atZone(zone).toInstant().toEpochMilli()
                }
                .filter { it > now }
            if (upcoming.isNotEmpty()) return upcoming.min()
            month += 1
            if (month > 12) {
                month = 1
                year += 1
            }
        }
        return null
    }

    /** 每月生效日期集合：monthDays 优先，为空回退旧单日字段 */
    fun effectiveMonthlyDays(task: AlarmTaskEntity): List<Int> {
        val days = MonthDaysParser.fromStorageString(task.monthDays)
        return if (days.isNotEmpty()) days else listOf(task.monthDay).filter { it in 1..31 }
    }

    private fun nextInterval(now: Long, intervalMinutes: Long, time: AlarmTimeEntity): Long? {
        if (intervalMinutes <= 0) return null
        var anchor = calendarAt(now, time).timeInMillis
        while (anchor <= now) {
            anchor += intervalMinutes * 60_000L
        }
        return anchor
    }

    private fun nextOnce(now: Long, dateEpochDay: Long?, time: AlarmTimeEntity): Long? {
        val epochDay = dateEpochDay ?: return null
        val date = LocalDate.ofEpochDay(epochDay)
        val cal = calendarAt(now, time).apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth)
        }
        return if (cal.timeInMillis > now) cal.timeInMillis else null
    }
}
