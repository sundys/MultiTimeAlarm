package com.example.multitimealarm.util

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 检测更新与 APK 下载 */
object UpdateDownloader {

    private const val TAG = "UpdateDownloader"

    /** 检测更新地址：代理与直连依次回退 */
    private val CHECK_URLS = listOf(
        "https://gh-proxy.com/https://api.github.com/repos/sundys/MultiTimeAlarm/releases/latest",
        "https://gh.halonice.com/https://api.github.com/repos/sundys/MultiTimeAlarm/releases/latest",
        "https://gh.slw.im/https://api.github.com/repos/sundys/MultiTimeAlarm/releases/latest",
        "https://api.github.com/repos/sundys/MultiTimeAlarm/releases/latest",
    )

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** 依次尝试各地址，请求最新 Release 的原始 JSON */
    fun fetchLatestReleaseJson(): String {
        var lastError: Exception? = null
        for (url in CHECK_URLS) {
            try {
                val conn = open(url, 8000, 8000)
                conn.inputStream.use { input ->
                    return input.bufferedReader().readText()
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("无可用检测地址")
    }

    /**
     * 从 Release JSON 中选取适合当前设备的 APK。
     * 返回 (tag, 文件名, 下载地址)；无匹配返回 null。
     */
    fun pickApkAsset(releaseJson: String): Triple<String, String, String>? {
        val root = JSONObject(releaseJson)
        val tag = root.getString("tag_name")
        val prefer = if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) "arm64-v8a" else "armeabi-v7a"
        val assets = root.getJSONArray("assets")
        var fallback: Triple<String, String, String>? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            val url = asset.getString("browser_download_url")
            if (name.contains("unsigned")) continue
            if (name.contains(prefer)) return Triple(tag, name, url)
            if (name.contains("armeabi-v7a")) fallback = Triple(tag, name, url)
        }
        return fallback
    }

    /**
     * 下载 APK：
     * - Android 10+：写入系统 Download 目录（MediaStore，无需存储权限）
     * - Android 9 及以下：写入应用外部专属目录（无权限要求）
     * 返回下载完成后对用户展示的位置描述。
     */
    fun downloadApk(
        context: Context,
        fileUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit,
    ): String {
        val urls = listOf(
            "https://gh-proxy.com/$fileUrl",
            "https://gh.halonice.com/$fileUrl",
            "https://gh.slw.im/$fileUrl",
            fileUrl,
        )
        var lastError: Exception? = null
        for (url in urls) {
            var insertedUri: Uri? = null
            try {
                val conn = open(url, 10000, 60000)
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    if (Build.VERSION.SDK_INT >= 29) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, APK_MIME)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                        ) ?: throw IllegalStateException("无法创建下载文件")
                        insertedUri = uri
                        resolver.openOutputStream(uri)?.use { out ->
                            copyWithProgress(input, out, total, onProgress)
                        } ?: throw IllegalStateException("无法打开输出流")
                        return "Download/$fileName"
                    } else {
                        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            ?: context.filesDir
                        val file = File(dir, fileName)
                        file.outputStream().use { out ->
                            copyWithProgress(input, out, total, onProgress)
                        }
                        return "应用目录/${file.name}"
                    }
                }
            } catch (e: Exception) {
                // 中途失败删除半截文件，避免换镜像重试后 Download 目录堆积残留
                insertedUri?.let {
                    runCatching { context.contentResolver.delete(it, null, null) }
                }
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    private fun open(url: String, connectTimeout: Int, readTimeout: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeout
        conn.readTimeout = readTimeout
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "MultiTimeAlarm-App")
        return conn
    }

    private fun copyWithProgress(
        input: InputStream,
        out: OutputStream,
        total: Long,
        onProgress: (Int) -> Unit,
    ) {
        val buf = ByteArray(64 * 1024)
        var done = 0L
        var lastPct = -1
        while (true) {
            val read = input.read(buf)
            if (read == -1) break
            out.write(buf, 0, read)
            done += read
            if (total > 0) {
                val pct = (done * 100 / total).toInt()
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(pct)
                }
            }
        }
        onProgress(100)
    }
}
