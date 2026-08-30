package com.example.multitimealarm.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.multitimealarm.R
import com.example.multitimealarm.data.AlarmTaskEntity
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.ring.RingActivity

object AlarmNotifier {

    private const val CHANNEL_ID = "alarm_ring"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_desc)
            setBypassDnd(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** 是否允许全屏意图（Android 14+ 需用户授权，低版本默认允许） */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return NotificationManagerCompat.from(context).canUseFullScreenIntent()
    }

    /** 发出响铃：改为启动前台服务（服务内发通知+播放铃声），不依赖响铃页是否被 ROM 放行 */
    fun notifyRing(context: Context, task: AlarmTaskEntity, time: AlarmTimeEntity) {
        ensureChannel(context)
        com.example.multitimealarm.ring.RingService.start(
            context,
            time.copy(snoozeCount = time.snoozeCount),
            task,
            time.snoozeCount,
        )
    }

    fun ringActivityIntent(
        context: Context,
        timeId: Long,
        taskName: String,
        snoozeMinutes: Int = 5,
        snoozeMaxCount: Int = 3,
        snoozeCount: Int = 0,
    ): Intent = Intent(context, RingActivity::class.java).apply {
        putExtra(RingActivity.EXTRA_TIME_ID, timeId)
        putExtra(RingActivity.EXTRA_TASK_NAME, taskName)
        putExtra(RingActivity.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        putExtra(RingActivity.EXTRA_SNOOZE_MAX_COUNT, snoozeMaxCount)
        putExtra(RingActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
