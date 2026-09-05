package com.example.multitimealarm.ring

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
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

/**
 * 响铃页：锁屏全屏弹出。铃声与振动由 RingService 前台服务承载，
 * 本页仅展示 UI 与关闭/贪睡操作。
 *
 * launchMode=singleInstance：连续两次闹钟时通过 onNewIntent 刷新页面内容。
 */
class RingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TIME_ID = "extra_time_id"
        const val EXTRA_TASK_NAME = "extra_task_name"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_SNOOZE_MAX_COUNT = "extra_snooze_max_count" // 0 = 不限次数
        const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"

        fun intent(
            context: Context,
            timeId: Long,
            taskName: String,
            snoozeMinutes: Int = 5,
            snoozeMaxCount: Int = 3,
            snoozeCount: Int = 0,
        ): Intent = Intent(context, RingActivity::class.java).apply {
            putExtra(EXTRA_TIME_ID, timeId)
            putExtra(EXTRA_TASK_NAME, taskName)
            putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            putExtra(EXTRA_SNOOZE_MAX_COUNT, snoozeMaxCount)
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 当前展示的响铃信息（可变状态：onNewIntent 覆盖） */
    private var ringInfo by mutableStateOf(
        RingInfo(
            timeId = -1L,
            taskName = "",
            snoozeMinutes = 5,
            snoozeMaxCount = 3,
            snoozeCount = 0,
        )
    )

    private data class RingInfo(
        val timeId: Long,
        val taskName: String,
        val snoozeMinutes: Int,
        val snoozeMaxCount: Int,
        val snoozeCount: Int,
    )

    private fun readRingInfo(intent: Intent?): RingInfo = RingInfo(
        timeId = intent?.getLongExtra(EXTRA_TIME_ID, -1L) ?: -1L,
        taskName = intent?.getStringExtra(EXTRA_TASK_NAME).orEmpty(),
        snoozeMinutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, 5) ?: 5,
        snoozeMaxCount = intent?.getIntExtra(EXTRA_SNOOZE_MAX_COUNT, 3) ?: 3,
        snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0,
    )

    /** 从通知点入而服务已超时停止时，重新开始响铃 */
    private fun ensureRinging(info: RingInfo) {
        if (!RingService.isRinging && info.timeId > 0) {
            RingService.start(this, info.timeId, info.taskName, info.snoozeMinutes, info.snoozeMaxCount, info.snoozeCount)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTurnScreenOn(true)
        }
        AlarmNotifier.cancel(this)
        ringInfo = readRingInfo(intent)
        ensureRinging(ringInfo)

        setContent { RingPage() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ringInfo = readRingInfo(intent)
        ensureRinging(ringInfo)
    }

    @Composable
    private fun RingPage() {
        val info = ringInfo
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                RingScreen(
                    taskName = info.taskName,
                    snoozeMinutes = info.snoozeMinutes,
                    snoozeAvailable = info.snoozeMaxCount == 0 || info.snoozeCount < info.snoozeMaxCount,
                    snoozeHint = if (info.snoozeMaxCount == 0) {
                        "第 ${info.snoozeCount + 1} 次"
                    } else {
                        "剩余 ${info.snoozeMaxCount - info.snoozeCount} 次"
                    },
                    onDismiss = {
                        RingService.stop(this)
                        finish()
                    },
                    onSnooze = {
                        if (info.timeId > 0) snooze(info.timeId, info.snoozeMinutes, info.snoozeCount)
                        RingService.stop(this)
                        finish()
                    },
                )
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
                Text("再等 $snoozeMinutes 分钟（$snoozeHint）", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}
