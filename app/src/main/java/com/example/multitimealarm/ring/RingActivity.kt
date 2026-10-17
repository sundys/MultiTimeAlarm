package com.example.multitimealarm.ring

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.multitimealarm.data.AppDatabase
import com.example.multitimealarm.notify.AlarmNotifier
import com.example.multitimealarm.scheduler.AlarmScheduler
import com.example.multitimealarm.ui.theme.MultiTimeAlarmTheme
import com.example.multitimealarm.ui.theme.TABULAR_FIGURES
import com.example.multitimealarm.ui.theme.ThemeStore
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
        const val EXTRA_TASK_NOTE = "extra_task_note"
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
            note: String = "",
        ): Intent = Intent(context, RingActivity::class.java).apply {
            putExtra(EXTRA_TIME_ID, timeId)
            putExtra(EXTRA_TASK_NAME, taskName)
            putExtra(EXTRA_TASK_NOTE, note)
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
            note = "",
            snoozeMinutes = 5,
            snoozeMaxCount = 3,
            snoozeCount = 0,
        )
    )

    private data class RingInfo(
        val timeId: Long,
        val taskName: String,
        val note: String,
        val snoozeMinutes: Int,
        val snoozeMaxCount: Int,
        val snoozeCount: Int,
    )

    private fun readRingInfo(intent: Intent?): RingInfo = RingInfo(
        timeId = intent?.getLongExtra(EXTRA_TIME_ID, -1L) ?: -1L,
        taskName = intent?.getStringExtra(EXTRA_TASK_NAME).orEmpty(),
        note = intent?.getStringExtra(EXTRA_TASK_NOTE).orEmpty(),
        snoozeMinutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, 5) ?: 5,
        snoozeMaxCount = intent?.getIntExtra(EXTRA_SNOOZE_MAX_COUNT, 3) ?: 3,
        snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0,
    )

    /** 从通知点入而服务已超时停止时，重新开始响铃 */
    private fun ensureRinging(info: RingInfo) {
        if (!RingService.isRinging && info.timeId > 0) {
            RingService.start(
                this, info.timeId, info.taskName, info.snoozeMinutes,
                info.snoozeMaxCount, info.snoozeCount, info.note,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTurnScreenOn(true)
        }
        AlarmNotifier.cancel(this)
        ringInfo = readRingInfo(intent)
        ensureRinging(ringInfo)

        setContent {
            // 响铃页跟随用户在设置页选的配色（此前用的是默认浅色主题，按钮是默认紫）
            MultiTimeAlarmTheme(
                themeMode = ThemeStore.themeMode(this),
                dynamicColor = ThemeStore.dynamicColor(this),
            ) {
                RingPage()
            }
        }
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
        // 锁屏场景固定纯黑背景，按钮与动效跟随主题主色
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            RingScreen(
                taskName = info.taskName,
                note = info.note,
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
    note: String,
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
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = taskName.ifBlank { "闹钟" },
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        if (note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(36.dp))
        PulsingTime(timeText = timeText, accent = accent)

        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text("关闭", style = MaterialTheme.typography.titleMedium)
        }
        if (snoozeAvailable) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text("再等 $snoozeMinutes 分钟（$snoozeHint）", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** 大号当前时间 + 向外扩散的脉冲圆环（提示"正在响铃"） */
@Composable
private fun PulsingTime(timeText: String, accent: Color) {
    val transition = rememberInfiniteTransition(label = "ringPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    BoxWithConstraints(contentAlignment = Alignment.Center) {
        // 圆环随可用宽度自适应（手机上基本占满内容宽度，大屏封顶 300dp）
        val diameter = minOf(maxWidth, 300.dp)
        // 字号按圆直径计算，并换算成"固定 dp 大小"：
        // 不跟随系统字体缩放放大，否则大字号模式下 88sp 会把时间撑成两行、跑出圆外
        val fontSize = with(LocalDensity.current) { (diameter * 0.30f).toSp() }

        Box(
            modifier = Modifier
                .size(diameter)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(accent),
        )
        Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = fontSize,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}
