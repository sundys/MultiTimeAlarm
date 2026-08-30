package com.example.multitimealarm.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/**
 * 闹钟铃声配置存储。
 * 存储值：null = 系统默认；"silent" = 静音（仅振动）；其他 = 铃声 URI 字符串。
 */
object RingtoneStore {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_RINGTONE = "alarm_ringtone"
    const val SILENT = "silent"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** pickedUri 为 null 表示用户在系统选择器中选择了"静音" */
    fun save(context: Context, pickedUri: Uri?) {
        val value = pickedUri?.toString() ?: SILENT
        prefs(context).edit().putString(KEY_RINGTONE, value).apply()
    }

    private fun loadRaw(context: Context): String? =
        prefs(context).getString(KEY_RINGTONE, null)

    /** 解析为可播放的铃声 URI；静音返回 null */
    fun resolveUri(context: Context): Uri? = when (val raw = loadRaw(context)) {
        SILENT -> null
        null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> runCatching { Uri.parse(raw) }.getOrNull()
    }

    /** 当前铃声的展示名 */
    fun displayName(context: Context): String = when (val raw = loadRaw(context)) {
        SILENT -> "静音（仅振动）"
        null -> "系统默认"
        else -> runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(raw))?.getTitle(context)
        }.getOrNull() ?: "自定义铃声"
    }
}
