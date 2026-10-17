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
    /** 前台服务启动失败时的兜底通知 id（与 RingService 的响铃/错过通知错开） */
    private const val FALLBACK_NOTIFICATION_ID = 1003

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_desc)
            // 不再调用 setBypassDnd(true)：它需要 ACCESS_NOTIFICATION_POLICY（通知策略访问），
            // 本应用没有声明该权限，渠道上的这个标记不会生效；
            // 免打扰下能否响铃由用户的渠道设置决定，渠道本身已是 IMPORTANCE_HIGH。
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
        val started = com.example.multitimealarm.ring.RingService.start(
            context,
            time,
            task,
            time.snoozeCount,
        )
        // Android 12+ 后台启动前台服务受限、部分 ROM 直接拒绝启动时，
        // 前台服务会起不来 → 连通知都不会有。这里降级为一条高优先级通知，保证闹钟至少能被看到。
        if (!started) postFallbackRingNotification(context, task, time)
    }

    /** 前台服务启动失败时的兜底通知 */
    private fun postFallbackRingNotification(
        context: Context,
        task: AlarmTaskEntity,
        time: AlarmTimeEntity,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val contentIntent = PendingIntent.getActivity(
            context,
            time.id.toInt(),
            ringActivityIntent(
                context, time.id, task.name,
                task.snoozeMinutes, task.snoozeMaxCount, time.snoozeCount, task.note,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(task.name.ifBlank { context.getString(R.string.default_task_name) })
            .setContentText("提醒时间到，点击查看")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(FALLBACK_NOTIFICATION_ID, notification) }
    }

    fun ringActivityIntent(
        context: Context,
        timeId: Long,
        taskName: String,
        snoozeMinutes: Int = 5,
        snoozeMaxCount: Int = 3,
        snoozeCount: Int = 0,
        note: String = "",
    ): Intent = Intent(context, RingActivity::class.java).apply {
        putExtra(RingActivity.EXTRA_TIME_ID, timeId)
        putExtra(RingActivity.EXTRA_TASK_NAME, taskName)
        putExtra(RingActivity.EXTRA_TASK_NOTE, note)
        putExtra(RingActivity.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        putExtra(RingActivity.EXTRA_SNOOZE_MAX_COUNT, snoozeMaxCount)
        putExtra(RingActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
