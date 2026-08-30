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
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "alarm_ring"
        private const val TIMEOUT_MS = 3 * 60_000L

        const val ACTION_STOP = "com.example.multitimealarm.ring.STOP"
        const val EXTRA_TIME_ID = "time_id"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_SNOOZE_MAX_COUNT = "snooze_max_count"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"

        @Volatile
        var isRinging: Boolean = false
            private set

        fun start(
            context: Context,
            time: AlarmTimeEntity,
            task: AlarmTaskEntity,
            snoozeCount: Int,
        ) = start(
            context,
            time.id,
            task.name,
            task.snoozeMinutes,
            task.snoozeMaxCount,
            snoozeCount,
        )

        fun start(
            context: Context,
            timeId: Long,
            taskName: String,
            snoozeMinutes: Int,
            snoozeMaxCount: Int,
            snoozeCount: Int,
        ) {
            val intent = Intent(context, RingService::class.java).apply {
                putExtra(EXTRA_TIME_ID, timeId)
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
                putExtra(EXTRA_SNOOZE_MAX_COUNT, snoozeMaxCount)
                putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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
    private val timeoutRunnable = Runnable {
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

        startAsForeground(taskName, timeId, snoozeMinutes, snoozeMaxCount, snoozeCount)
        startRing()

        // 超时未处理自动停止响铃
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        isRinging = true
        return START_NOT_STICKY
    }

    private fun startAsForeground(
        taskName: String,
        timeId: Long,
        snoozeMinutes: Int,
        snoozeMaxCount: Int,
        snoozeCount: Int,
    ) {
        val fullScreenIntent = RingActivity.intent(this, timeId, taskName, snoozeMinutes, snoozeMaxCount, snoozeCount)
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
        if (mediaPlayer?.isPlaying == true) return
        val alarmUri = com.example.multitimealarm.util.RingtoneStore.resolveUri(this)
        if (alarmUri != null) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@RingService, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {
            }
        }
        // 静音设置或铃声不可用时仅振动
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
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
