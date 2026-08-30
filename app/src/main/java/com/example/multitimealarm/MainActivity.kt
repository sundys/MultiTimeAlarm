package com.example.multitimealarm

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multitimealarm.ui.AlarmViewModel
import com.example.multitimealarm.ui.edit.AlarmEditScreen
import com.example.multitimealarm.ui.list.AlarmListPage
import com.example.multitimealarm.ui.list.NapDialog
import com.example.multitimealarm.ui.list.NapListPage
import com.example.multitimealarm.ui.settings.SettingsScreen
import com.example.multitimealarm.ui.settings.THEME_DARK
import com.example.multitimealarm.ui.settings.THEME_LIGHT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 编辑页"新建闹钟"模式的哨兵 id（0 和正数是真实任务 id，null 表示列表页） */
private const val NEW_TASK_ID = -1L
private const val PREFS_NAME = "app_settings"
private const val KEY_THEME_MODE = "theme_mode"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MultiTimeAlarmTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlarmApp()
                }
            }
        }
    }
}

/** 主题包装：按用户设置（跟随系统/浅色/暗色）选择配色 */
@Composable
private fun MultiTimeAlarmTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(prefs.getInt(KEY_THEME_MODE, 0)) }

    val dark = when (themeMode) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    // 状态栏 + 底部导航栏跟随应用内主题（基础主题只能跟随系统，这里覆盖应用内切换）
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var ctx: Context? = view.context
            while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
            (ctx as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                val barColor = if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                window.statusBarColor = barColor
                window.navigationBarColor = barColor
            }
        }
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
    ) {
        AlarmApp(
            themeMode = themeMode,
            onThemeModeChange = { mode ->
                themeMode = mode
                prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmApp(
    viewModel: AlarmViewModel = viewModel(),
    themeMode: Int = 0,
    onThemeModeChange: (Int) -> Unit = {},
) {
    // 导航状态：列表(null) / 新建(-1) / 编辑(>0)；设置页；菜单
    val editingTaskId = remember { MutableStateFlow<Long?>(null) }
    val editing by editingTaskId.collectAsState()
    val allTasks by viewModel.tasks.collectAsState()
    val alarmTasks = allTasks.filter { !it.task.isNap }
    val napTasks = allTasks.filter { it.task.isNap }

    var showSettings by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showNapDialog by remember { mutableStateOf(false) }
    var alarmListScrolling by remember { mutableStateOf(false) }
    var napListScrolling by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()

    // Android 13+ 申请通知权限（安静发起，无需横幅）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showSettings) {
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onBack = { showSettings = false },
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
        topBar = {
            TopAppBar(
                title = { Text("多时闹钟") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = {
                                menuOpen = false
                                showSettings = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (pagerState.currentPage == 0) {
                ScrollingFab(
                    hidden = alarmListScrolling,
                    contentDescription = "新建闹钟",
                    onClick = { editingTaskId.value = NEW_TASK_ID },
                )
            } else {
                ScrollingFab(
                    hidden = napListScrolling,
                    contentDescription = "新建小憩",
                    onClick = { showNapDialog = true },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = { tabPositions ->
                    CapsuleIndicator(tabPositions, pagerState.currentPage)
                },
            ) {
                listOf("闹钟", "小憩").forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(label) },
                    )
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
                        onToggleTime = viewModel::setTimeEnabled,
                        onDeleteTask = viewModel::deleteTask,
                        onEditTask = { editingTaskId.value = it },
                    )
                } else {
                    NapListPage(
                        naps = napTasks,
                        onScrollingChanged = { napListScrolling = it },
                        onDeleteTask = viewModel::deleteTask,
                        onEditTask = { editingTaskId.value = it },
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

/** 胶囊形 Tab 指示器：选中项底部的圆角短条，颜色随主题 */
@Composable
private fun CapsuleIndicator(
    tabPositions: List<TabPosition>,
    selectedIndex: Int,
) {
    Box(
        modifier = Modifier
            .tabIndicatorOffset(tabPositions[selectedIndex])
            .wrapContentSize(Alignment.BottomCenter)
            .padding(bottom = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** 圆形 + 号浮动按钮：列表滚动时隐藏，停下后显示 */
@Composable
private fun ScrollingFab(
    hidden: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        label = "fabAlpha",
    )
    FloatingActionButton(
        onClick = { if (!hidden) onClick() },
        modifier = Modifier.alpha(alpha),
    ) {
        Icon(Icons.Default.Add, contentDescription = contentDescription)
    }
}
