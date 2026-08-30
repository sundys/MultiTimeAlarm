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

    /** 发出全屏响铃通知，锁屏时直接拉起 RingActivity */
    fun notifyRing(context: Context, task: AlarmTaskEntity, time: AlarmTimeEntity) {
        ensureChannel(context)

        val fullScreenIntent = Intent(context, RingActivity::class.java).apply {
            putExtra(RingActivity.EXTRA_TIME_ID, time.id)
            putExtra(RingActivity.EXTRA_TASK_NAME, task.name)
            putExtra(RingActivity.EXTRA_SNOOZE_MINUTES, task.snoozeMinutes)
            putExtra(RingActivity.EXTRA_SNOOZE_MAX_COUNT, task.snoozeMaxCount)
            putExtra(RingActivity.EXTRA_SNOOZE_COUNT, time.snoozeCount)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            time.id.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(task.name.ifBlank { context.getString(R.string.default_task_name) })
            .setContentText("提醒时间到")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // 无通知权限时仍尝试全屏意图（部分 ROM 允许），否则响铃页无法拉起
            context.startActivity(fullScreenIntent)
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
