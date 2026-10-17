package com.example.multitimealarm

import com.example.multitimealarm.data.AlarmBackup
import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.TaskType
import com.example.multitimealarm.data.TaskWithTimes
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmBackupTest {

    private fun time(hour: Int, minute: Int, second: Int = 0, enabled: Boolean = true) =
        AlarmTimeEntity(id = 0, taskId = 1, hour = hour, minute = minute, second = second, enabled = enabled)

    private fun task(
        name: String = "吃药提醒",
        type: TaskType = TaskType.DAILY,
        weekdaysMask: Int = 0,
        monthDays: String = "",
        intervalMinutes: Long = 0,
        dateEpochDay: Long? = null,
        enabled: Boolean = true,
        isNap: Boolean = false,
        note: String = "",
        snoozeMinutes: Int = 5,
        snoozeMaxCount: Int = 3,
        createdAt: Long = 1_700_000_000_000L,
    ) = AlarmTaskEntity(
        id = 1, name = name, type = type, weekdaysMask = weekdaysMask, monthDays = monthDays,
        intervalMinutes = intervalMinutes, dateEpochDay = dateEpochDay, enabled = enabled,
        isNap = isNap, note = note, snoozeMinutes = snoozeMinutes, snoozeMaxCount = snoozeMaxCount,
        createdAt = createdAt,
    )

    @Test
    fun `round trip keeps every field`() {
        val source = listOf(
            TaskWithTimes(task("吃药提醒", note = "饭后服用"), listOf(time(8, 0), time(12, 30, 15))),
            TaskWithTimes(
                task("月供", type = TaskType.MONTHLY, monthDays = "1,15", enabled = false),
                listOf(time(9, 0)),
            ),
            TaskWithTimes(task("小憩 10 分钟", type = TaskType.ONCE, isNap = true, dateEpochDay = 20_600L), listOf(time(0, 5))),
        )

        val decoded = AlarmBackup.decode(AlarmBackup.encode(source))
        assertEquals(3, decoded.count)

        val first = decoded.entries[0]
        assertEquals("吃药提醒", first.task.name)
        assertEquals(TaskType.DAILY, first.task.type)
        assertEquals("饭后服用", first.task.note)
        assertEquals(1_700_000_000_000L, first.task.createdAt)
        assertEquals(listOf(8 to 0, 12 to 30), first.times.map { it.hour to it.minute })
        assertEquals(15, first.times[1].second)

        val monthly = decoded.entries[1]
        assertEquals(TaskType.MONTHLY, monthly.task.type)
        assertEquals("1,15", monthly.task.monthDays)
        assertEquals(false, monthly.task.enabled)

        val nap = decoded.entries[2]
        assertTrue(nap.task.isNap)
        assertEquals(20_600L, nap.task.dateEpochDay)
    }

    @Test
    fun `time point enabled flag survives`() {
        val source = listOf(TaskWithTimes(task(), listOf(time(7, 0, enabled = false), time(8, 0))))
        val decoded = AlarmBackup.decode(AlarmBackup.encode(source))
        assertEquals(listOf(false, true), decoded.entries[0].times.map { it.enabled })
    }

    @Test
    fun `unknown task type falls back to daily`() {
        val json = """{"tasks":[{"name":"x","type":"SOME_FUTURE_TYPE","times":[{"hour":8,"minute":0}]}]}"""
        val decoded = AlarmBackup.decode(json)
        assertEquals(TaskType.DAILY, decoded.entries[0].task.type)
    }

    @Test
    fun `out of range time points are dropped`() {
        val json = """{"tasks":[
            {"name":"a","type":"DAILY","times":[{"hour":8,"minute":0},{"hour":99,"minute":0},{"hour":8,"minute":70}]},
            {"name":"b","type":"DAILY","times":[{"hour":25,"minute":0}]}
        ]}"""
        val decoded = AlarmBackup.decode(json)
        // a 保留 1 个合法时间点；b 因没有合法时间点整条跳过。
        // skippedTimes 统计所有被丢弃的越界时间点（含被跳过任务里的那些）
        assertEquals(1, decoded.count)
        assertEquals("a", decoded.entries[0].task.name)
        assertEquals(1, decoded.entries[0].times.size)
        assertEquals(1, decoded.skippedTasks)
        assertEquals(3, decoded.skippedTimes)
    }

    @Test
    fun `missing tasks array throws before touching anything`() {
        val thrown = runCatching { AlarmBackup.decode("""{"app":"MultiTimeAlarm","version":2}""") }
        assertTrue(thrown.exceptionOrNull() is JSONException)
    }

    @Test
    fun `malformed json throws`() {
        val thrown = runCatching { AlarmBackup.decode("not json at all") }
        assertTrue(thrown.exceptionOrNull() is JSONException)
    }

    @Test
    fun `empty tasks array decodes to empty`() {
        val decoded = AlarmBackup.decode("""{"tasks":[]}""")
        assertEquals(0, decoded.count)
        assertEquals(0, decoded.skippedTasks)
    }
}
