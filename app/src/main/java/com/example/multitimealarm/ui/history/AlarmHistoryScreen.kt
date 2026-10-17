package com.example.multitimealarm.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multitimealarm.data.AlarmHistoryEntity
import com.example.multitimealarm.ui.components.AppCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RING_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日")

private fun formatAt(epochMillis: Long, formatter: DateTimeFormatter): String =
    formatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun scheduledText(entry: AlarmHistoryEntity): String =
    if (entry.second == 0) {
        String.format(Locale.US, "%02d:%02d", entry.hour, entry.minute)
    } else {
        String.format(Locale.US, "%02d:%02d:%02d", entry.hour, entry.minute, entry.second)
    }

/** 响铃历史：每次真实到点触发记录一条，便于回看是否漏掉提醒 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmHistoryScreen(
    history: List<AlarmHistoryEntity>,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("响铃历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空记录")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            EmptyHistory(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(history, key = { it.id }) { entry ->
                    HistoryCard(entry)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空响铃历史") },
            text = { Text("将删除全部响铃记录，且不可恢复。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClear()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HistoryCard(entry: AlarmHistoryEntity) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：实际响铃时间
            Text(
                text = formatAt(entry.triggeredAt, RING_TIME_FORMAT),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.taskName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "计划 ${scheduledText(entry)} · ${formatAt(entry.triggeredAt, DATE_FORMAT)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "还没有响铃记录\n闹钟到点触发后会记录在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}
