package com.example.multitimealarm.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.multitimealarm.ui.components.SettingDivider
import com.example.multitimealarm.ui.components.SettingItem
import com.example.multitimealarm.ui.components.SettingSection
import com.example.multitimealarm.ui.components.SettingTint
import com.example.multitimealarm.ui.theme.THEME_DARK
import com.example.multitimealarm.ui.theme.THEME_LIGHT
import com.example.multitimealarm.ui.theme.THEME_SYSTEM
import com.example.multitimealarm.ui.theme.ThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val REPO_PAGE = "https://github.com/sundys/MultiTimeAlarm"

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
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onClearAll: ((Int) -> Unit) -> Unit,
    onExportJson: suspend () -> String,
    onRestoreJson: (String, (Int, String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var ringtoneName by remember {
        mutableStateOf(com.example.multitimealarm.util.RingtoneStore.displayName(context))
    }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }
    // 系统是否真的实现了动态取色：部分 ROM（ColorOS 等）只有 API 31+，没有 Monet
    val dynamicColorAvailable = remember { ThemeStore.dynamicColorAvailable(context) }
    // 没有 Monet 时改用"直接读取壁纸主色"的兜底方案
    val wallpaperColorAvailable = remember { ThemeStore.wallpaperColorAvailable(context) }

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

    var statusText by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    // 下载完成但"安装未知应用"未授权时暂存安装包，授权返回后自动续装
    var pendingInstallFile by remember { mutableStateOf<java.io.File?>(null) }
    var installHint by remember { mutableStateOf<String?>(null) }
    // 已下载待安装的安装包：从专用目录扫描得到，而不是只存在内存里。
    // 这样用户取消系统安装、退出设置页甚至杀进程后，回来仍能直接「重新安装」，无需重复下载。
    var pendingApk by remember { mutableStateOf<java.io.File?>(null) }
    var pendingTag by remember { mutableStateOf<String?>(null) }

    /** 重新扫描专用目录：找到可重装的包；目录里没有则说明安装已完成或从未下载，顺手清理多余安装包 */
    fun refreshPendingApk() {
        val current = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        val found = com.example.multitimealarm.util.UpdateDownloader.findPendingApk(context, current)
        pendingApk = found
        pendingTag = found?.let { com.example.multitimealarm.util.UpdateDownloader.versionFromFileName(it.name) }
        if (found == null && installHint != null) {
            installHint = "已安装 v${current ?: versionName}，安装包已自动清理"
        }
    }

    // 进入页面先扫描一次（例如从系统安装器返回、或上次退出时留下了未安装的包）
    LaunchedEffect(Unit) { refreshPendingApk() }

    // SAF 备份/恢复
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    // JSON 构建 + 文件读写全部放 IO 线程，避免大文件卡主线程
                    withContext(Dispatchers.IO) {
                        val json = onExportJson()
                        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("无法写入文件")
                    }
                }.onSuccess { statusText = "备份成功" }
                    .onFailure { statusText = "备份失败：${it.message}" }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.readBytes().toString(Charsets.UTF_8)
                        } ?: throw IllegalStateException("无法读取文件")
                    }
                }.onSuccess { json -> pendingRestoreJson = json }
                    .onFailure { statusText = "读取备份失败：${it.message}" }
            }
        }
    }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    var batteryIgnoring by remember { mutableStateOf(false) }
    var fullScreenAllowed by remember { mutableStateOf(true) }
    // Android 12 起精确闹钟许可可能被用户关闭（更新应用时系统也会重新校验），需要展示给用户
    var exactAlarmAllowed by remember { mutableStateOf(true) }

    // 每次回到此页面时刷新授权状态（含从系统设置返回）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                ringtoneName = com.example.multitimealarm.util.RingtoneStore.displayName(context)
                if (Build.VERSION.SDK_INT >= 31) {
                    exactAlarmAllowed = alarmManager.canScheduleExactAlarms()
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    fullScreenAllowed = context.getSystemService(NotificationManager::class.java)
                        .canUseFullScreenIntent()
                }
                // 从"安装未知应用"授权页返回：已授权则自动继续之前挂起的安装
                pendingInstallFile?.let { file ->
                    if (com.example.multitimealarm.util.UpdateDownloader.canInstallApk(context)) {
                        pendingInstallFile = null
                        installHint = "正在调起安装程序…"
                        com.example.multitimealarm.util.UpdateDownloader.installApk(context, file)
                    }
                }
                // 重新扫描安装包目录：
                // - 安装已完成 → 目录中的包版本不再高于当前版本，会被清掉并提示"已安装"
                // - 安装被取消 → 保留唯一一份，界面据此显示「重新安装」，不必重新下载
                refreshPendingApk()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ============ 通用 ============
            SettingSection("通用") {
                SettingItem(
                    icon = Icons.Filled.Palette,
                    iconTint = SettingTint.Violet,
                    title = "主题颜色",
                    subtitle = "浅色 / 暗色 / 跟随系统",
                    value = themeLabel(themeMode),
                    onClick = { showThemeDialog = true },
                )
                // 跟随壁纸取色：Android 12+ 才有系统取色接口
                if (ThemeStore.dynamicColorSupported) {
                    SettingDivider()
                    SettingItem(
                        icon = Icons.Filled.ColorLens,
                        iconTint = SettingTint.Blue,
                        title = "跟随壁纸取色",
                        subtitle = when {
                            dynamicColorAvailable -> "使用系统壁纸取色"
                            wallpaperColorAvailable -> "系统未内置动态取色，将直接读取壁纸主色生成配色"
                            else -> "当前系统无法读取壁纸颜色，开启后仍使用品牌配色"
                        },
                        trailing = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = onDynamicColorChange,
                            )
                        },
                        onClick = { onDynamicColorChange(!dynamicColor) },
                    )
                }
                SettingDivider()
                SettingItem(
                    icon = Icons.Filled.MusicNote,
                    iconTint = SettingTint.Orange,
                    title = "铃声设置",
                    subtitle = "闹钟到点响铃时播放的声音",
                    value = ringtoneName,
                    onClick = {
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
            }

            // ============ 权限 ============
            SettingSection("权限") {
                // Android 12+ 精确闹钟可能被系统或用户关闭（应用更新后系统还会重新校验），
                // 关掉之后闹钟只能走非精确通道、可能延迟数分钟
                if (Build.VERSION.SDK_INT >= 31) {
                    SettingItem(
                        icon = Icons.Filled.AlarmOn,
                        iconTint = SettingTint.Orange,
                        title = "精确闹钟",
                        subtitle = if (exactAlarmAllowed) "已授权，闹钟按设定时间精确触发"
                        else "未授权，系统可能延迟几分钟才触发，点击前往授权",
                        value = if (exactAlarmAllowed) "已允许" else "未允许",
                        valueColor = if (exactAlarmAllowed) SettingTint.Green
                        else MaterialTheme.colorScheme.error,
                        onClick = {
                            if (!exactAlarmAllowed) {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure { openAppDetails(context) }
                            }
                        },
                    )
                    SettingDivider()
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    SettingItem(
                        icon = Icons.Filled.Fullscreen,
                        iconTint = SettingTint.Pink,
                        title = "全屏弹窗权限",
                        subtitle = "点击前往授权，到点直接弹出全屏响铃页",
                        value = if (fullScreenAllowed) "已允许" else "未允许",
                        valueColor = if (fullScreenAllowed) SettingTint.Green else MaterialTheme.colorScheme.error,
                        onClick = {
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
                    SettingDivider()
                }
                SettingItem(
                    icon = Icons.Filled.Layers,
                    iconTint = SettingTint.Indigo,
                    title = "后台弹窗权限",
                    subtitle = "国产系统在后台弹出响铃页需要此权限",
                    value = if (Settings.canDrawOverlays(context)) "已允许" else "未允许",
                    valueColor = if (Settings.canDrawOverlays(context)) SettingTint.Green
                    else MaterialTheme.colorScheme.error,
                    onClick = {
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
                SettingDivider()
                SettingItem(
                    icon = Icons.Filled.BatterySaver,
                    iconTint = SettingTint.Teal,
                    title = "忽略电池优化",
                    subtitle = "防止省电策略在休眠时拦截闹钟提醒",
                    value = if (batteryIgnoring) "已忽略" else "未忽略",
                    valueColor = if (batteryIgnoring) SettingTint.Green else MaterialTheme.colorScheme.error,
                    onClick = {
                        if (batteryIgnoring) {
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
                SettingDivider()
                SettingItem(
                    icon = Icons.Filled.Autorenew,
                    iconTint = SettingTint.Amber,
                    title = "自启动设置",
                    subtitle = "小米/华为等系统需允许后台自启动，否则重启后闹钟可能丢失",
                    onClick = {
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
            }
            Text(
                text = "国产定制系统（MIUI/EMUI/ColorOS 等）的后台管控可能拦截闹钟广播。" +
                    "建议完成\"忽略电池优化\"并在自启动设置中允许本应用。",
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ============ 数据 ============
            SettingSection("数据") {
                SettingItem(
                    icon = Icons.Filled.Backup,
                    iconTint = SettingTint.Green,
                    title = "备份闹钟",
                    subtitle = "导出全部闹钟为备份文件",
                    onClick = {
                        val name = "duoshi_backup_" +
                            java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json"
                        runCatching { backupLauncher.launch(name) }
                    },
                )
                SettingDivider()
                SettingItem(
                    icon = Icons.Filled.Restore,
                    iconTint = SettingTint.Blue,
                    title = "恢复闹钟",
                    subtitle = "从备份文件恢复，将覆盖当前全部闹钟",
                    onClick = {
                        runCatching {
                            restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    },
                )
                SettingDivider()
                SettingItem(
                    icon = Icons.Filled.DeleteForever,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "清除全部闹钟",
                    titleColor = MaterialTheme.colorScheme.error,
                    subtitle = "删除所有闹钟任务及提醒时间，不可恢复",
                    onClick = { showClearConfirm = true },
                )
            }

            // ============ 关于 ============
            SettingSection("关于") {
                SettingItem(
                    icon = Icons.Filled.Info,
                    iconTint = SettingTint.Grey,
                    title = "关于多时闹钟",
                    subtitle = "版本信息、检查更新与开源地址",
                    value = "v$versionName",
                    onClick = { showAboutDialog = true },
                )
            }

            statusText?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingRestoreJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            title = { Text("恢复闹钟") },
            text = { Text("将从备份文件恢复闹钟，并覆盖当前全部闹钟。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val json2 = json
                    pendingRestoreJson = null
                    onRestoreJson(json2) { count, err ->
                        statusText = if (err == null) "已恢复 $count 个闹钟" else "恢复失败：$err"
                    }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreJson = null }) { Text("取消") }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除全部闹钟") },
            text = { Text("将删除所有闹钟任务及其提醒时间，且不可恢复。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearAll { count -> statusText = "已清除 $count 个闹钟" }
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
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

    if (showAboutDialog) {
        var updateStatus by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        var releaseJson by remember { mutableStateOf<String?>(null) }
        var foundNewTag by remember { mutableStateOf<String?>(null) }
        var downloading by remember { mutableStateOf(false) }
        var downloadPct by remember { mutableStateOf(0) }
        var downloadError by remember { mutableStateOf<String?>(null) }

        /** 调起系统安装器；未授权"安装未知应用"时先引导授权，返回后自动续装 */
        fun installPending(file: java.io.File) {
            if (com.example.multitimealarm.util.UpdateDownloader.canInstallApk(context)) {
                installHint = "正在调起安装程序…"
                com.example.multitimealarm.util.UpdateDownloader.installApk(context, file)
            } else {
                installHint = "请在接下来页面允许「安装未知应用」，返回后自动继续安装"
                pendingInstallFile = file
                if (!com.example.multitimealarm.util.UpdateDownloader.requestInstallPermission(context)) {
                    openAppDetails(context)
                }
            }
        }

        fun startDownload() {
            val json = releaseJson ?: return
            if (downloading) return
            downloading = true
            downloadPct = 0
            downloadError = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val asset = com.example.multitimealarm.util.UpdateDownloader.pickApkAsset(json)
                            ?: throw IllegalStateException("未找到适合本机的安装包")
                        val file = com.example.multitimealarm.util.UpdateDownloader.downloadApk(
                            context, asset.third, asset.second
                        ) { pct -> downloadPct = pct }
                        file to asset.first
                    }
                }
                downloading = false
                result.onSuccess { (file, tag) ->
                    pendingApk = file
                    pendingTag = tag
                    // 目录里只保留刚下载的这一份（downloadApk 开始时已清空，这里再兜一层）
                    com.example.multitimealarm.util.UpdateDownloader.cleanupOldApks(context, file)
                    installPending(file)
                }
                    .onFailure { downloadError = it.message?.take(80) ?: "下载失败" }
            }
        }

        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于多时闹钟") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Text(
                        "一个可设置多个提醒时间的闹钟应用。支持每天 / 按星期 / 每月 / 间隔循环 / 仅一次提醒，" +
                            "一个闹钟可绑定多个时间点，深度适配国产系统后台管控。",
                        fontSize = 14.sp,
                    )
                    Text("当前版本：v$versionName", fontSize = 13.sp)
                    val pending = pendingApk
                    if (downloading) {
                        Text(
                            "下载更新中 $downloadPct%",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { downloadPct / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (pending != null) {
                        // 已下载但未装成功（用户取消安装、授权后返回等）：直接重装，不再重新下载
                        Text(
                            installHint ?: "安装包已下载（${pending.name}），无需重新下载",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { installPending(pending) }) {
                            Text("重新安装")
                        }
                        // 目录里是旧版本、远端已有更新时，额外给一个下载入口
                        foundNewTag?.let { tag ->
                            if (com.example.multitimealarm.util.UpdateDownloader
                                    .isNewerVersion(tag, pendingTag ?: "0")
                            ) {
                                TextButton(onClick = { startDownload() }) {
                                    Text("下载新版本 v$tag")
                                }
                            }
                        }
                    } else {
                        installHint?.let {
                            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        if (foundNewTag != null) {
                            TextButton(onClick = { startDownload() }) {
                                Text("下载更新")
                            }
                        }
                        downloadError?.let {
                            Text(
                                "下载失败：$it",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    updateStatus?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = if (checking) "检测中…" else "检测更新",
                        fontSize = 13.sp,
                        color = if (checking) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!checking) {
                                    checking = true
                                    updateStatus = "正在检测更新…"
                                    scope.launch {
                                        val remote = withContext(Dispatchers.IO) {
                                            runCatching {
                                                com.example.multitimealarm.util.UpdateDownloader.fetchLatestReleaseJson()
                                            }
                                        }
                                        checking = false
                                        remote.onSuccess { json ->
                                            releaseJson = json
                                            val latest = runCatching {
                                                JSONObject(json).getString("tag_name").removePrefix("v")
                                            }.getOrDefault("?")
                                            if (com.example.multitimealarm.util.UpdateDownloader.isNewerVersion(latest, versionName)) {
                                                foundNewTag = latest
                                                updateStatus = "发现新版本 v$latest"
                                            } else {
                                                foundNewTag = null
                                                updateStatus = "当前已是最新版本 v$versionName"
                                            }
                                        }.onFailure {
                                            Log.e("UpdateCheck", "检测更新失败", it)
                                            val reason = it.message?.take(60) ?: "网络不可用"
                                            updateStatus = "检测失败：$reason"
                                        }
                                    }
                                }
                            },
                    )
                    Text(
                        text = "开源地址",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(REPO_PAGE))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("关闭") }
            },
        )
    }
}
