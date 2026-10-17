package com.example.multitimealarm.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统事件触发的全量重调度。
 *
 * 以下四种系统事件都会让本包已注册的闹钟失效或算错，必须重新注册一遍：
 * - 开机完成（BOOT_COMPLETED）：重启后系统不保留任何 AlarmManager 注册
 * - App 被更新（MY_PACKAGE_REPLACED）：**包被替换时系统会清空该包的全部闹钟**，
 *   不清的话用户更新完不打开 App 就一直不会响
 * - 时区变更（TIMEZONE_CHANGED）：闹钟按绝对时刻注册，「每天 8:00」会随新时区漂移
 * - 系统时间被改（TIME_SET）：同理
 *
 * 这些都是 Android 8+ 允许静态注册的隐式广播例外，可以直接在清单里声明。
 */
class BootReceiver : BroadcastReceiver() {

    private val rescheduleActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in rescheduleActions) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 调度异常只记日志，避免崩溃循环
                runCatching {
                    // force：这些事件发生后系统侧的注册已经不可信，必须按当前时区重算
                    AlarmScheduler.rescheduleAll(context.applicationContext, force = true)
                }.onFailure { Log.e(TAG, "系统事件重调度失败（${intent.action}）", it) }
                // 更新安装完成后（进程已被替换）清理下载目录里的安装包
                runCatching {
                    com.example.multitimealarm.util.UpdateDownloader
                        .cleanupInstalledApks(context.applicationContext)
                }.onFailure { Log.w(TAG, "清理安装包失败", it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
