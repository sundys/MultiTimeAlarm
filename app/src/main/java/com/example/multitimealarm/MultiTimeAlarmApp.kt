package com.example.multitimealarm

import android.app.Application
import com.example.multitimealarm.data.AppDatabase
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
        // 冷启动时兜底重调度一次（覆盖 BOOT_COMPLETED 之外的进程被杀场景）
        applicationScope.launch {
            AlarmScheduler.rescheduleAll(this@MultiTimeAlarmApp)
        }
    }

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
