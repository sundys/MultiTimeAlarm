package com.example.multitimealarm.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 检测更新与 APK 下载、调起系统安装器 */
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
     * 下载 APK 到应用专属目录（getExternalFilesDir，无需存储权限，
     * FileProvider 可直接分享给系统安装器，国产 ROM 兼容性最好）。
     * 中途失败删除半截文件后换镜像重试。返回下载完成的文件。
     */
    fun downloadApk(
        context: Context,
        fileUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit,
    ): File {
        val urls = listOf(
            "https://gh-proxy.com/$fileUrl",
            "https://gh.halonice.com/$fileUrl",
            "https://gh.slw.im/$fileUrl",
            fileUrl,
        )
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        var lastError: Exception? = null
        for (url in urls) {
            val file = File(dir, fileName)
            try {
                val conn = open(url, 10000, 60000)
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        copyWithProgress(input, out, total, onProgress)
                    }
                }
                return file
            } catch (e: Exception) {
                // 中途失败删除半截文件，避免换镜像重试后堆积残留
                runCatching { file.delete() }
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    /** 是否已获得"安装未知应用"授权（本应用 minSdk 26，恒定适用） */
    fun canInstallApk(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * 跳转系统的"安装未知应用"授权页（MIUI/ColorOS 等为每应用独立开关）。
     * 返回是否成功跳转；失败时调用方应兜底打开应用详情页。
     */
    fun requestInstallPermission(context: Context): Boolean =
        runCatching {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)

    /** 调起系统安装器安装 APK（未授权时由调用方先引导授权） */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        // 优先标准安装动作；个别 ROM 移除了该处理器时回退到通用查看动作
        val launched = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(uri, APK_MIME)
                    addFlags(flags)
                }
            )
        }.isSuccess
        if (!launched) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, APK_MIME)
                        addFlags(flags)
                    }
                )
            }
        }
    }

    /** 版本比较：a > b 返回 true（按数值段比较） */
    fun isNewerVersion(a: String, b: String): Boolean {
        val parse = { s: String -> s.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 } }
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /**
     * 安装是否已完成（可清理安装包）：当前版本已达到下载的目标版本。
     * 用户取消安装时当前版本低于目标版本，返回 false，保留安装包以便重试。
     */
    fun installFinished(targetTag: String?, currentVersion: String?): Boolean {
        if (targetTag.isNullOrBlank() || currentVersion.isNullOrBlank()) return false
        return !isNewerVersion(targetTag.removePrefix("v").trim(), currentVersion.trim())
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
