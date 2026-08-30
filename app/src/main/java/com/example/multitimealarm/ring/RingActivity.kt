package com.example.multitimealarm.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.multitimealarm.data.AppDatabase
import com.example.multitimealarm.notify.AlarmNotifier
import com.example.multitimealarm.scheduler.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 响铃页：锁屏全屏弹出，播放铃声与振动，支持关闭 / 按任务配置贪睡 */
class RingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TIME_ID = "extra_time_id"
        const val EXTRA_TASK_NAME = "extra_task_name"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_SNOOZE_MAX_COUNT = "extra_snooze_max_count" // 0 = 不限次数
        const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTurnScreenOn(true)
        }
        AlarmNotifier.cancel(this)

        val timeId = intent.getLongExtra(EXTRA_TIME_ID, -1L)
        val taskName = intent.getStringExtra(EXTRA_TASK_NAME).orEmpty()
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
        val snoozeMaxCount = intent.getIntExtra(EXTRA_SNOOZE_MAX_COUNT, 3)
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)

        startRing()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    RingScreen(
                        taskName = taskName,
                        snoozeMinutes = snoozeMinutes,
                        snoozeAvailable = snoozeMaxCount == 0 || snoozeCount < snoozeMaxCount,
                        snoozeHint = if (snoozeMaxCount == 0) {
                            "第 ${snoozeCount + 1} 次"
                        } else {
                            "剩余 ${snoozeMaxCount - snoozeCount} 次"
                        },
                        onDismiss = {
                            stopRing()
                            finish()
                        },
                        onSnooze = {
                            if (timeId > 0) snooze(timeId, snoozeMinutes, snoozeCount)
                            stopRing()
                            finish()
                        },
                    )
                }
            }
        }
    }

    /** 记录贪睡次数并按任务配置的间隔追加一次触发 */
    private fun snooze(timeId: Long, minutes: Int, currentCount: Int) {
        AlarmScheduler.snooze(this, timeId, minutes.toLong())
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(applicationContext).alarmDao()
                .setSnoozeCount(timeId, currentCount + 1)
        }
    }

    private fun startRing() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@RingActivity, alarmUri)
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
            // 铃声不可用（如无默认闹钟铃声）时静默，仅振动
        }

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
        super.onDestroy()
    }
}

@Composable
private fun RingScreen(
    taskName: String,
    snoozeMinutes: Int,
    snoozeAvailable: Boolean,
    snoozeHint: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val timeText = remember(now) {
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = taskName.ifBlank { "闹钟" },
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = timeText,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(64.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("关闭", fontSize = 18.sp)
        }
        if (snoozeAvailable) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("贪睡 $snoozeMinutes 分钟（$snoozeHint）", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}
