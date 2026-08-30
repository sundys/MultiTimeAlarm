package com.example.multitimealarm.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机完成后全量恢复闹钟注册，避免重启丢失 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 调度异常只记日志，避免崩溃循环
                runCatching {
                    AlarmScheduler.rescheduleAll(context.applicationContext)
                }.onFailure { Log.e("BootReceiver", "开机重调度失败", it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
