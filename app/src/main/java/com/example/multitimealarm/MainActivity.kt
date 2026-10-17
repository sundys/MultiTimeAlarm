package com.example.multitimealarm

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multitimealarm.ui.AlarmViewModel
import com.example.multitimealarm.ui.components.CategorySwitcher
import com.example.multitimealarm.ui.edit.AlarmEditScreen
import com.example.multitimealarm.ui.history.AlarmHistoryScreen
import com.example.multitimealarm.ui.list.AlarmListPage
import com.example.multitimealarm.ui.list.NapDialog
import com.example.multitimealarm.ui.list.NapListPage
import com.example.multitimealarm.ui.settings.SettingsScreen
import com.example.multitimealarm.ui.theme.BrandBlue
import com.example.multitimealarm.ui.theme.MultiTimeAlarmTheme
import com.example.multitimealarm.ui.theme.ThemeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 编辑页"新建闹钟"模式的哨兵 id（0 和正数是真实任务 id，null 表示列表页） */
private const val NEW_TASK_ID = -1L

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内容延伸到系统栏下，系统栏底色由页面自身 surface 承担
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            // 主题状态由 Activity 层持有，切换后整个界面即时重组
            var themeMode by remember { mutableStateOf(ThemeStore.themeMode(context)) }
            var dynamicColor by remember { mutableStateOf(ThemeStore.dynamicColor(context)) }

            MultiTimeAlarmTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlarmApp(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            ThemeStore.setThemeMode(context, mode)
                        },
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = { enabled ->
                            dynamicColor = enabled
                            ThemeStore.setDynamicColor(context, enabled)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmApp(
    viewModel: AlarmViewModel = viewModel(),
    themeMode: Int = 0,
    onThemeModeChange: (Int) -> Unit = {},
    dynamicColor: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {},
) {
    // 导航状态：列表(null) / 新建(-1) / 编辑(>0)；设置页
    val editingTaskId = remember { MutableStateFlow<Long?>(null) }
    val editing by editingTaskId.collectAsState()
    val allTasks by viewModel.tasks.collectAsState()
    val history by viewModel.history.collectAsState()
    val alarmTasks = allTasks.filter { !it.data.task.isNap }
    val napTasks = allTasks.filter { it.data.task.isNap }

    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showNapDialog by remember { mutableStateOf(false) }
    var alarmListScrolling by remember { mutableStateOf(false) }
    var napListScrolling by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()

    // 系统返回键：历史/设置/编辑页返回到首页，而不是退出应用
    BackHandler(enabled = showHistory || showSettings || editing != null) {
        when {
            showHistory -> showHistory = false
            showSettings -> showSettings = false
            else -> editingTaskId.value = null
        }
    }

    // Android 13+ 申请通知权限（安静发起，无需横幅；已授权或永久拒绝时不再重复发起）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val permContext = LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                permContext, Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showHistory) {
        AlarmHistoryScreen(
            history = history,
            onClear = viewModel::clearHistory,
            onBack = { showHistory = false },
        )
        return
    }

    if (showSettings) {
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            dynamicColor = dynamicColor,
            onDynamicColorChange = onDynamicColorChange,
            onBack = { showSettings = false },
            onClearAll = viewModel::clearAllAlarms,
            onExportJson = viewModel::exportAlarmsJson,
            onRestoreJson = { json, onResult -> viewModel.restoreAlarmsJson(json, onResult) },
        )
        return
    }

    if (editing != null) {
        AlarmEditScreen(
            taskId = editing?.takeIf { it > 0 },
            loadTask = { viewModel.loadTask(it) },
            onSave = { task, times, removed -> viewModel.saveTask(task, times, removed) },
            onBack = { editingTaskId.value = null },
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            val fabColor = if (dynamicColor) MaterialTheme.colorScheme.primary else BrandBlue
            if (pagerState.currentPage == 0) {
                ScrollingFab(
                    hidden = alarmListScrolling,
                    contentDescription = "新建闹钟",
                    containerColor = fabColor,
                    onClick = { editingTaskId.value = NEW_TASK_ID },
                )
            } else {
                ScrollingFab(
                    hidden = napListScrolling,
                    contentDescription = "新建小憩",
                    containerColor = fabColor,
                    onClick = { showNapDialog = true },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 顶部同一行：分类胶囊居中，设置入口靠右；不再显示应用名
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                CategorySwitcher(
                    labels = listOf("闹钟", "小憩"),
                    selectedIndex = pagerState.currentPage,
                    onSelect = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    modifier = Modifier.align(Alignment.Center),
                    // 开启动态取色时跟随主题主色；否则保持品牌蓝
                    selectedColor = if (dynamicColor) MaterialTheme.colorScheme.primary else BrandBlue,
                )
                IconButton(
                    onClick = { showHistory = true },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(Icons.Filled.History, contentDescription = "响铃历史")
                }
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (page == 0) {
                    AlarmListPage(
                        tasks = alarmTasks,
                        onScrollingChanged = { alarmListScrolling = it },
                        onToggleTask = viewModel::setTaskEnabled,
                        onDeleteTask = viewModel::deleteTask,
                        onEditTask = { editingTaskId.value = it },
                        onCreate = { editingTaskId.value = NEW_TASK_ID },
                    )
                } else {
                    NapListPage(
                        naps = napTasks,
                        onScrollingChanged = { napListScrolling = it },
                        onDeleteTask = viewModel::deleteTask,
                        onEditTask = { editingTaskId.value = it },
                        onToggleTask = viewModel::setTaskEnabled,
                        onCreate = { showNapDialog = true },
                    )
                }
            }
        }
    }

    if (showNapDialog) {
        NapDialog(
            onConfirm = { minutes ->
                showNapDialog = false
                viewModel.createNapTask(minutes)
            },
            onDismiss = { showNapDialog = false },
        )
    }
}

/** 圆形 + 号浮动按钮：列表滚动时隐藏，停下后显示 */
@Composable
private fun ScrollingFab(
    hidden: Boolean,
    contentDescription: String,
    containerColor: Color,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        label = "fabAlpha",
    )
    FloatingActionButton(
        onClick = { if (!hidden) onClick() },
        modifier = Modifier.alpha(alpha),
        shape = CircleShape,
        containerColor = containerColor,
        // 浅色主色（动态取色的暗色主题）下用深色图标保证可读性
        contentColor = if (containerColor.luminance() > 0.5f) Color(0xFF1A1C1E) else Color.White,
    ) {
        Icon(Icons.Default.Add, contentDescription = contentDescription)
    }
}
