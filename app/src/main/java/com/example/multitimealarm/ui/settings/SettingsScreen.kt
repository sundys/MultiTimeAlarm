package com.example.multitimealarm.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

/** 主题模式：0=跟随系统，1=浅色，2=暗色 */
const val THEME_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

fun themeLabel(mode: Int): String = when (mode) {
    THEME_LIGHT -> "浅色"
    THEME_DARK -> "暗色"
    else -> "跟随系统"
}

private fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var ringtoneName by remember {
        mutableStateOf(com.example.multitimealarm.util.RingtoneStore.displayName(context))
    }

    // 系统铃声选择器（ColorOS/MIUI 等会自动跳转各自铃声选择界面）
    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val picked = result.data?.let {
            IntentCompat.getParcelableExtra(
                it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
            )
        }
        com.example.multitimealarm.util.RingtoneStore.save(context, picked)
        ringtoneName = com.example.multitimealarm.util.RingtoneStore.displayName(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var batteryIgnoring by remember { mutableStateOf(false) }
    var fullScreenAllowed by remember { mutableStateOf(true) }

    // 每次回到此页面时刷新授权状态（含从系统设置返回）
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                ringtoneName = com.example.multitimealarm.util.RingtoneStore.displayName(context)
                if (Build.VERSION.SDK_INT >= 34) {
                    fullScreenAllowed = context.getSystemService(NotificationManager::class.java)
                        .canUseFullScreenIntent()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("主题颜色") },
                supportingContent = { Text(themeLabel(themeMode)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemeDialog = true },
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("铃声设置") },
                supportingContent = { Text(ringtoneName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            com.example.multitimealarm.util.RingtoneStore.resolveUri(context)?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
                            }
                        }
                        runCatching { ringtonePicker.launch(pickerIntent) }
                            .onFailure { openAppDetails(context) }
                    },
            )
            HorizontalDivider()

            // Android 14+ 全屏意图权限默认不授予，需用户手动允许，否则到点只弹通知不响铃
            if (Build.VERSION.SDK_INT >= 34) {
                ListItem(
                    headlineContent = { Text("全屏弹窗权限") },
                    supportingContent = {
                        Text(
                            if (fullScreenAllowed) "已允许，到点自动弹出响铃页"
                            else "未允许，到点只显示通知不响铃。点击前往授权"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:${context.packageName}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure { openAppDetails(context) }
                        },
                )
                HorizontalDivider()
            }

            // 后台弹窗（悬浮窗）权限：ColorOS 等管控后台弹出界面的依据
            ListItem(
                headlineContent = { Text("后台弹窗权限") },
                supportingContent = {
                    Text(
                        if (Settings.canDrawOverlays(context)) "已允许，后台到点可直接弹出响铃页"
                        else "建议允许，国产系统后台弹出界面需要此权限"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!Settings.canDrawOverlays(context)) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure { openAppDetails(context) }
                        }
                    },
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("忽略电池优化") },
                supportingContent = {
                    Text(
                        if (batteryIgnoring) "已忽略，休眠时闹钟可正常响铃"
                        else "点击授权，防止省电策略在休眠时拦截闹钟提醒"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (batteryIgnoring) {
                            // 已授权：打开系统电池优化列表供查看/撤销
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } else {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure { openAppDetails(context) }
                        }
                    },
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("自启动设置") },
                supportingContent = {
                    Text("小米/华为等系统需允许后台自启动，否则重启后闹钟可能丢失")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // 优先尝试 MIUI 自启动管理页，失败则回退应用详情页
                        val miui = runCatching {
                            context.startActivity(
                                Intent().setClassName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        if (miui.isFailure) openAppDetails(context)
                    },
            )
            HorizontalDivider()

            Text(
                text = "国产定制系统（MIUI/EMUI/ColorOS 等）的后台管控可能拦截闹钟广播。" +
                    "建议完成\"忽略电池优化\"并在自启动设置中允许本应用。",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("主题颜色") },
            text = {
                Column {
                    listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK).forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                },
                            )
                            Text(themeLabel(mode))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("取消") }
            },
        )
    }
}
