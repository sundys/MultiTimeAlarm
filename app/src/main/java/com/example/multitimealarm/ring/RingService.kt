package com.example.multitimealarm.ring

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.multitimealarm.R
import com.example.multitimealarm.data.AlarmTimeEntity
import com.example.multitimealarm.data.AlarmTaskEntity

/**
 * 响铃前台服务：铃声与振动由前台服务承载，
 * 即使 ROM 拦截了响铃页弹出，进程仍为前台重要性（不会被缓存回收处决），铃声持续播放。
 */
class RingService : Service() {

    companion object {
        private const val TAG = "RingService"
        private const val NOTIFICATION_ID = 1001
        /** 响铃超时后留下的"错过闹钟"通知（与响铃通知区分，便于各自取消） */
        private const val MISSED_NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "alarm_ring"

        /**
         * 最长响铃时长。超时只是停止铃声与振动，并换一条可点击的"错过闹钟"通知，
         * 不再像以前那样连通知一起消失——否则用户错过 3 分钟后就没有任何痕迹。
         */
        private const val TIMEOUT_MS = 10 * 60_000L

        const val ACTION_STOP = "com.example.multitimealarm.ring.STOP"
        const val EXTRA_TIME_ID = "time_id"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_SNOOZE_MAX_COUNT = "snooze_max_count"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
        const val EXTRA_TASK_NOTE = "task_note"

        @Volatile
        var isRinging: Boolean = false
            private set

        fun start(
            context: Context,
            time: AlarmTimeEntity,
            task: AlarmTaskEntity,
            snoozeCount: Int,
        ): Boolean = start(
            context,
            time.id,
            task.name,
            task.snoozeMinutes,
            task.snoozeMaxCount,
            snoozeCount,
            task.note,
        )

        /** @return 前台服务是否成功启动；失败时调用方应降级为普通通知，避免闹钟彻底静默 */
        fun start(
            context: Context,
            timeId: Long,
            taskName: String,
            snoozeMinutes: Int,
            snoozeMaxCount: Int,
            snoozeCount: Int,
            note: String = "",
        ): Boolean {
            val intent = Intent(context, RingService::class.java).apply {
                putExtra(EXTRA_TIME_ID, timeId)
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
                putExtra(EXTRA_SNOOZE_MAX_COUNT, snoozeMaxCount)
                putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
                putExtra(EXTRA_TASK_NOTE, note)
            }
            return try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                // Android 12+ 后台启动前台服务受限、部分 ROM 也会直接拒绝
                Log.e(TAG, "前台服务启动失败，需要降级为通知", e)
                false
            }
        }

        fun stop(context: Context) {
            isRinging = false
            context.startService(
                Intent(context, RingService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    // 超时后要用这些信息拼"错过闹钟"通知
    private var lastTimeId = -1L
    private var lastTaskName = ""
    private var lastNote = ""
    private var lastSnoozeMinutes = 5
    private var lastSnoozeMaxCount = 3

    private val timeoutRunnable = Runnable {
        showMissedNotification()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRing()
            stopSelf()
            return START_NOT_STICKY
        }

        val timeId = intent?.getLongExtra(EXTRA_TIME_ID, -1L) ?: -1L
        val taskName = intent?.getStringExtra(EXTRA_TASK_NAME).orEmpty()
        val snoozeMinutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, 5) ?: 5
        val snoozeMaxCount = intent?.getIntExtra(EXTRA_SNOOZE_MAX_COUNT, 3) ?: 3
        val snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0
        val note = intent?.getStringExtra(EXTRA_TASK_NOTE).orEmpty()

        // 记录本次响铃信息，供超时后的"错过闹钟"通知使用
        lastTimeId = timeId
        lastTaskName = taskName
        lastNote = note
        lastSnoozeMinutes = snoozeMinutes
        lastSnoozeMaxCount = snoozeMaxCount

        startAsForeground(taskName, note, timeId, snoozeMinutes, snoozeMaxCount, snoozeCount)
        startRing()

        // 超时未处理自动停止响铃
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        isRinging = true
        return START_NOT_STICKY
    }

    private fun startAsForeground(
        taskName: String,
        note: String,
        timeId: Long,
        snoozeMinutes: Int,
        snoozeMaxCount: Int,
        snoozeCount: Int,
    ) {
        val fullScreenIntent = RingActivity.intent(
            this, timeId, taskName, snoozeMinutes, snoozeMaxCount, snoozeCount, note
        )
        val contentPi = android.app.PendingIntent.getActivity(
            this,
            timeId.toInt(),
            fullScreenIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(taskName.ifBlank { getString(R.string.default_task_name) })
            .setContentText("提醒时间到")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentPi)
            .setFullScreenIntent(contentPi, true)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRing() {
        // 已经创建过就不再新建：prepareAsync 尚未就绪时 isPlaying 仍为 false，
        // 只用 isPlaying 判断会重复创建 MediaPlayer（双份铃声且旧实例永不释放）
        if (mediaPlayer != null) return
        val alarmUri = com.example.multitimealarm.util.RingtoneStore.resolveUri(this)
        if (alarmUri != null) {
            val player = MediaPlayer()
            try {
                player.setDataSource(this@RingService, alarmUri)
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.isLooping = true
                player.setOnPreparedListener { it.start() }
                player.setOnErrorListener { failed, _, _ ->
                    // 出错时释放，避免留下一个永远不响也无法复用的实例
                    if (mediaPlayer === failed) mediaPlayer = null
                    runCatching { failed.release() }
                    true
                }
                player.prepareAsync()
                mediaPlayer = player
            } catch (_: Exception) {
                // 铃声不可用时仅振动，不留半初始化实例
                runCatching { player.release() }
            }
        }
        // 静音设置或铃声不可用时仅振动
        vibrator = resolveVibrator()
        vibrator?.let { v ->
            val pattern = longArrayOf(0, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        }
    }

    /** 响铃超时：换一条常驻可点击的通知，避免铃声和通知一起消失、用户毫无察觉 */
    private fun showMissedNotification() {
        val manager = androidx.core.app.NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val contentIntent = android.app.PendingIntent.getActivity(
            this,
            MISSED_NOTIFICATION_ID,
            RingActivity.intent(
                this, lastTimeId, lastTaskName,
                lastSnoozeMinutes, lastSnoozeMaxCount, 0, lastNote,
            ),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(lastTaskName.ifBlank { getString(R.string.default_task_name) })
            .setContentText("已停止响铃，点击可继续查看或贪睡")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(MISSED_NOTIFICATION_ID, notification) }
    }

    private fun resolveVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun stopRing() {
        handler.removeCallbacks(timeoutRunnable)
        mediaPlayer?.run {
            try {
                if (isPlaying) stop()
                release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopRing()
        isRinging = false
        super.onDestroy()
    }
}
