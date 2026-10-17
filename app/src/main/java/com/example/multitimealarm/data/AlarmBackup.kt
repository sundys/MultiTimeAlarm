package com.example.multitimealarm.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份/恢复的纯数据层：只负责「实体 ↔ JSON」的转换与校验，
 * 不碰数据库、不碰系统闹钟，因此可以完整地做单元测试。
 *
 * 恢复时先把整份 JSON 解析校验完、再交给仓库写库，
 * 保证「备份文件有问题」这种情况不会破坏用户现有的闹钟数据。
 */
object AlarmBackup {

    private const val APP_TAG = "MultiTimeAlarm"

    /** 格式版本：v1 无 createdAt，v2 起写入 createdAt */
    private const val FORMAT_VERSION = 2

    /** 一条待恢复的任务及其时间点（id 均为 0，由数据库重新分配） */
    data class Entry(val task: AlarmTaskEntity, val times: List<AlarmTimeEntity>)

    data class DecodeResult(
        val entries: List<Entry>,
        /** 因时间点全部非法而整条跳过的任务数 */
        val skippedTasks: Int,
        /** 越界被丢弃的时间点数 */
        val skippedTimes: Int,
    ) {
        val count: Int get() = entries.size
    }

    fun encode(tasks: List<TaskWithTimes>): String {
        val arr = JSONArray()
        tasks.forEach { entry ->
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
            // createdAt 用于恢复后保持列表顺序；时间点的 snoozeCount 属于"上次响铃后的临时状态"，
            // 有意不备份，恢复后从 0 开始
            o.put("createdAt", entry.task.createdAt)

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
            .put("app", APP_TAG)
            .put("version", FORMAT_VERSION)
            .put("count", tasks.size)
            .put("tasks", arr)
            .toString(2)
    }

    /**
     * 解析并校验备份。结构性问题（不是合法 JSON、缺少 tasks 数组）直接抛出，
     * 调用方在此之前不应改动数据库；单个时间点越界则丢弃并计入 skippedTimes。
     */
    fun decode(json: String): DecodeResult {
        val root = JSONObject(json)
        val arr = root.getJSONArray("tasks")
        val entries = mutableListOf<Entry>()
        var skippedTasks = 0
        var skippedTimes = 0

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val times = mutableListOf<AlarmTimeEntity>()
            val rawTimes = o.optJSONArray("times") ?: JSONArray()
            for (j in 0 until rawTimes.length()) {
                val t = rawTimes.getJSONObject(j)
                val hour = t.optInt("hour", -1)
                val minute = t.optInt("minute", -1)
                val second = t.optInt("second", 0)
                if (!isValidTime(hour, minute, second)) {
                    skippedTimes++
                    continue
                }
                times.add(
                    AlarmTimeEntity(
                        taskId = 0L,
                        hour = hour,
                        minute = minute,
                        second = second,
                        enabled = t.optBoolean("enabled", true),
                    )
                )
            }
            if (times.isEmpty()) {
                // 没有任何有效时间点的任务没有意义，与其恢复成一条永远不响的闹钟，不如跳过
                skippedTasks++
                continue
            }

            entries.add(
                Entry(
                    task = AlarmTaskEntity(
                        id = 0L,
                        name = o.optString("name", "定时提醒"),
                        type = runCatching { TaskType.valueOf(o.getString("type")) }
                            .getOrDefault(TaskType.DAILY),
                        weekdaysMask = o.optInt("weekdaysMask", 0),
                        monthDay = o.optInt("monthDay", 0),
                        monthDays = o.optString("monthDays", ""),
                        intervalMinutes = o.optLong("intervalMinutes", 0),
                        dateEpochDay = if (o.has("dateEpochDay")) o.optLong("dateEpochDay") else null,
                        enabled = o.optBoolean("enabled", true),
                        vibrate = o.optBoolean("vibrate", true),
                        snoozeMinutes = o.optInt("snoozeMinutes", 5),
                        snoozeMaxCount = o.optInt("snoozeMaxCount", 3),
                        isNap = o.optBoolean("isNap", false),
                        note = o.optString("note", ""),
                        // v1 备份没有 createdAt，用当前时间兜底
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    ),
                    times = times,
                )
            )
        }
        return DecodeResult(entries, skippedTasks, skippedTimes)
    }

    private fun isValidTime(hour: Int, minute: Int, second: Int): Boolean =
        hour in 0..23 && minute in 0..59 && second in 0..59
}
