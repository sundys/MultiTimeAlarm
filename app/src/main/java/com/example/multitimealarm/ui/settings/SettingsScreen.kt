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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/** 主题模式：0=跟随系统，1=浅色，2=暗色 */
const val THEME_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

private const val REPO_PAGE = "https://github.com/sundys/MultiTimeAlarm"
private const val RELEASES_PAGE = "https://github.com/sundys/MultiTimeAlarm/releases"

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

/** 分类小标题 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 版本号比较：remote > current 返回 true（按数值段比较） */
private fun isNewerVersion(remote: String, current: String): Boolean {
    val parse = { s: String -> s.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 } }
    val a = parse(remote)
    val b = parse(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
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

    // SAF 备份/恢复
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = onExportJson()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法写入文件")
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
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("无法读取文件")
                }.onSuccess { json -> pendingRestoreJson = json }
                    .onFailure { statusText = "读取备份失败：${it.message}" }
            }
        }
    }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var batteryIgnoring by remember { mutableStateOf(false) }
    var fullScreenAllowed by remember { mutableStateOf(true) }

    // 每次回到此页面时刷新授权状态（含从系统设置返回）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ============ 通用 ============
            SectionHeader("通用")
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

            // ============ 权限 ============
            SectionHeader("权限")
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

            // ============ 数据 ============
            SectionHeader("数据")
            ListItem(
                headlineContent = { Text("备份闹钟") },
                supportingContent = { Text("导出全部闹钟为备份文件") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val name = "duoshi_backup_" +
                            java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json"
                        runCatching { backupLauncher.launch(name) }
                    },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("恢复闹钟") },
                supportingContent = { Text("从备份文件恢复，将覆盖当前全部闹钟") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("清除全部闹钟", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("删除所有闹钟任务及提醒时间，不可恢复") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showClearConfirm = true },
            )
            HorizontalDivider()

            // ============ 关于 ============
            ListItem(
                headlineContent = {
                    Text("关于", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true },
            )
            HorizontalDivider()

            statusText?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
        val versionName = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "1.0"
        }
        var updateStatus by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        var releaseJson by remember { mutableStateOf<String?>(null) }
        var foundNewTag by remember { mutableStateOf<String?>(null) }
        var downloading by remember { mutableStateOf(false) }
        var downloadPct by remember { mutableStateOf(0) }
        var downloadedPath by remember { mutableStateOf<String?>(null) }
        var downloadError by remember { mutableStateOf<String?>(null) }

        fun startDownload() {
            val json = releaseJson ?: return
            if (downloading) return
            downloading = true
            downloadPct = 0
            downloadError = null
            downloadedPath = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val asset = com.example.multitimealarm.util.UpdateDownloader.pickApkAsset(json)
                            ?: throw IllegalStateException("未找到适合本机的安装包")
                        com.example.multitimealarm.util.UpdateDownloader.downloadApk(
                            context, asset.third, asset.second
                        ) { pct -> downloadPct = pct }
                    }
                }
                downloading = false
                result.onSuccess { path -> downloadedPath = path }
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
                    Text(
                        text = "开源地址：$REPO_PAGE",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(REPO_PAGE))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                    )
                    if (foundNewTag != null) {
                        when {
                            downloading -> {
                                Text("下载更新中 $downloadPct%", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { downloadPct / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            downloadedPath != null -> Text(
                                "已下载到 $downloadedPath，打开文件管理器点击安装即可升级",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            downloadError != null -> Text(
                                "下载失败：$downloadError",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                            else -> TextButton(onClick = { startDownload() }) {
                                Text("下载更新")
                            }
                        }
                    }
                    updateStatus?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(
                        onClick = {
                            if (checking) return@TextButton
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
                                    if (isNewerVersion(latest, versionName)) {
                                        foundNewTag = latest
                                        downloadedPath = null
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
                        },
                    ) {
                        Text(if (checking) "检测中…" else "检测更新")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("关闭") }
            },
        )
    }
}
