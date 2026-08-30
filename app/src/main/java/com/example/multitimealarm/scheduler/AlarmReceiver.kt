package com.example.multitimealarm.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.multitimealarm.data.AppDatabase
import com.example.multitimealarm.data.TaskType
import com.example.multitimealarm.notify.AlarmNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟触发入口。
 * 收到触发后：发出全屏响铃通知 → 按任务类型续订（DAILY 到明天 / ONCE 标记完成）。
 */
class AlarmReceiver : BroadcastReceiver() {

    private val TAG = "AlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val timeId = intent.getLongExtra(AlarmScheduler.EXTRA_TIME_ID, -1L)
        if (timeId <= 0) return
        val snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 任何调度/通知异常都不应让进程崩溃（崩溃会导致响铃丢失且应用无法打开）
                runCatching {
                    handleTrigger(context.applicationContext, timeId, snooze)
                }.onFailure { Log.e(TAG, "闹钟触发处理失败", it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTrigger(context: Context, timeId: Long, snooze: Boolean) {
        val dao = AppDatabase.getInstance(context).alarmDao()
        val time = dao.getTime(timeId) ?: return
        val taskWithTimes = dao.getTaskWithTimes(time.taskId) ?: return
        val task = taskWithTimes.task

        if (snooze) {
            // 贪睡触发：用户主动要求的提醒必须响铃。
            // 一次性任务首次响铃后任务会被自动关闭，此处不能以 enabled 为由丢弃贪睡；
            // 任务被彻底删除时上方 getTime/getTaskWithTimes 已返回 null。
            AlarmNotifier.notifyRing(context, task, time)
            return
        }

        // 开关已关闭则直接丢弃本次触发
        if (!task.enabled || !time.enabled) return

        // 真实触发：清零贪睡计数，响铃页据此显示剩余贪睡次数
        dao.setSnoozeCount(timeId, 0)
        AlarmNotifier.notifyRing(context, task, time.copy(snoozeCount = 0))

        // ColorOS/Android 14+ 可能拒绝全屏意图权限，通知被降级为普通通知。
        // 尽力直接拉起响铃页（有精确闹钟权限或自启动授权的 ROM 上可行），失败也不影响通知。
        if (!AlarmNotifier.canUseFullScreenIntent(context)) {
            runCatching {
                context.startActivity(
                    AlarmNotifier.ringActivityIntent(
                        context, timeId, task.name,
                        task.snoozeMinutes, task.snoozeMaxCount, 0,
                    )
                )
            }
        }

        when (task.type) {
            TaskType.ONCE -> {
                // 本次触发完成，禁用该时间点；全部时间点完成后自动关闭任务
                dao.setTimeEnabled(timeId, false)
                val remaining = dao.getTimesOfTask(task.id).count { it.enabled }
                if (remaining == 0) {
                    dao.setTaskEnabled(task.id, false)
                }
            }
            // DAILY / WEEKLY / MONTHLY / INTERVAL：重算下一次触发并续订
            else -> AlarmScheduler.rescheduleTask(context, task.id)
        }
    }
}
