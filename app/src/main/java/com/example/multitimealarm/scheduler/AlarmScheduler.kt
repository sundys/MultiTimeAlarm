package com.example.multitimealarm.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.AppDatabase
import com.example.multitimealarm.util.TimeUtils

/**
 * 多时间点调度器。
 *
 * 核心策略：任务下的每个时间点注册一个独立的 AlarmManager 闹钟
 * （requestCode = 时间点 id），使用 setAlarmClock()（免精确闹钟权限、
 * Doze 下可靠触发并在状态栏显示闹钟图标）。
 *
 * - DAILY：触发后由 AlarmReceiver 自动续订到下一天
 * - ONCE：触发后该时间点自动禁用；全部触发完则任务自动关闭
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    const val EXTRA_TIME_ID = "extra_time_id"
    const val EXTRA_SNOOZE = "extra_snooze"

    /** 贪睡槽位偏移：与主闹钟区分，避免覆盖 DAILY 已续订的下一次闹钟 */
    private const val SNOOZE_REQUEST_OFFSET = 1_000_000

    /** 进程内全量重调度标记：开机广播与 Application.onCreate 都会触发，只执行一次 */
    private val fullRescheduleDone = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 重新调度一个任务的所有时间点，并顺手清理已过期的一次性时间点 */
    suspend fun rescheduleTask(context: Context, taskId: Long) {
        val dao = AppDatabase.getInstance(context).alarmDao()
        val taskWithTimes = dao.getTaskWithTimes(taskId) ?: return
        val task = taskWithTimes.task

        for (time in taskWithTimes.times) {
            cancelTime(context, time.id)
            if (task.enabled && time.enabled) {
                val triggerAt = TimeUtils.nextTriggerAt(task, time)
                if (triggerAt != null) {
                    scheduleTime(context, time.id, triggerAt)
                } else if (task.type == com.example.multitimealarm.data.TaskType.ONCE) {
                    // 一次性时间点已过期（如错过触发），自动失效
                    dao.setTimeEnabled(time.id, false)
                }
            }
        }
    }

    /** 开机后/进程冷启动后全量重调度所有已启用任务（同进程内只执行一次，失败可重试） */
    suspend fun rescheduleAll(context: Context) {
        if (!fullRescheduleDone.compareAndSet(false, true)) return
        try {
            val dao = AppDatabase.getInstance(context).alarmDao()
            for (task in dao.getEnabledTasks()) {
                rescheduleTask(context, task.task.id)
            }
        } catch (e: Exception) {
            fullRescheduleDone.set(false) // 失败放开，允许 BOOT 广播或下次启动重试
            throw e
        }
    }

    /** 取消任务下所有时间点的闹钟（任务关闭或删除时调用） */
    suspend fun cancelTask(context: Context, taskId: Long) {
        val dao = AppDatabase.getInstance(context).alarmDao()
        for (time in dao.getTimesOfTask(taskId)) {
            cancelTime(context, time.id)
        }
    }

    fun scheduleTime(context: Context, timeId: Long, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, timeId.toInt())
        val info = AlarmManager.AlarmClockInfo(triggerAt, mainActivityIntent(context))
        try {
            // setAlarmClock 精确触发；部分 ROM（如 ColorOS 15）要求精确闹钟权限
            am.setAlarmClock(info, pi)
        } catch (e: SecurityException) {
            // 权限被拒时降级为 10 分钟窗口的闹钟（可能略延迟，但保证能响且不崩溃）
            Log.w(TAG, "setAlarmClock 被拒绝，降级 setWindow", e)
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60_000L, pi)
        }
    }

    fun cancelTime(context: Context, timeId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, timeId.toInt()))
        am.cancel(pendingIntent(context, timeId.toInt() + SNOOZE_REQUEST_OFFSET))
    }

    /** 贪睡：为该时间点追加一次 N 分钟后的一次性触发（独立 requestCode，不影响主闹钟） */
    fun snooze(context: Context, timeId: Long, delayMinutes: Long = TimeUtils.SNOOZE_MINUTES) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, mainActivityIntent(context)),
            snoozePendingIntent(context, timeId.toInt()),
        )
    }

    private fun pendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TIME_ID, requestCode.toLong())
        }
        return alarmPendingIntent(context, requestCode, intent)
    }

    private fun snoozePendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TIME_ID, requestCode.toLong())
            putExtra(EXTRA_SNOOZE, true)
        }
        return alarmPendingIntent(context, requestCode + SNOOZE_REQUEST_OFFSET, intent)
    }

    private fun alarmPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
    ): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun mainActivityIntent(context: Context): PendingIntent {
        // 部分 ROM（如 ColorOS）可能返回 null，这里显式兜底到 MainActivity
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, com.example.multitimealarm.MainActivity::class.java)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
}
