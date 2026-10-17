package com.example.multitimealarm

import android.app.Application
import android.util.Log
import com.example.multitimealarm.notify.AlarmNotifier
import com.example.multitimealarm.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MultiTimeAlarmApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.ensureChannel(this)
        // 冷启动时兜底重调度一次（覆盖 BOOT_COMPLETED 之外的进程被杀场景）。
        // 调度异常只记日志不崩溃，否则任何 ROM 差异都会造成启动即闪退的死循环。
        applicationScope.launch {
            // 更新安装完成后进程会被替换，这里在冷启动时清理已安装的旧安装包
            runCatching {
                com.example.multitimealarm.util.UpdateDownloader.cleanupInstalledApks(this@MultiTimeAlarmApp)
            }.onFailure { Log.w("MultiTimeAlarmApp", "清理安装包失败", it) }
            runCatching {
                AlarmScheduler.rescheduleAll(this@MultiTimeAlarmApp)
            }.onFailure {
                Log.e("MultiTimeAlarmApp", "启动重调度失败", it)
            }
        }
    }
}
