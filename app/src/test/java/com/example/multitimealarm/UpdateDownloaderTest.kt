package com.example.multitimealarm

import com.example.multitimealarm.util.UpdateDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDownloaderTest {

    @Test
    fun isNewerVersion_comparesNumerically() {
        assertTrue(UpdateDownloader.isNewerVersion("1.1.8", "1.1.7"))
        assertTrue(UpdateDownloader.isNewerVersion("1.2", "1.1.9"))
        assertTrue(UpdateDownloader.isNewerVersion("2.0", "1.9.9"))
        assertFalse(UpdateDownloader.isNewerVersion("1.1.7", "1.1.8"))
        assertFalse(UpdateDownloader.isNewerVersion("1.1.8", "1.1.8"))
    }

    @Test
    fun isNewerVersion_handlesNonNumericSegments() {
        assertTrue(UpdateDownloader.isNewerVersion("1.1.9-beta", "1.1.8"))
        // 非数字段按 0 处理：1.1.beta(=1.1.0) 不高于 1.1.0
        assertFalse(UpdateDownloader.isNewerVersion("1.1.beta", "1.1.0"))
    }

    @Test
    fun installFinished_trueWhenCurrentReachesTarget() {
        assertTrue(UpdateDownloader.installFinished("v1.1.8", "1.1.8"))
        assertTrue(UpdateDownloader.installFinished("v1.1.8", "1.2.0"))
        assertTrue(UpdateDownloader.installFinished("1.1.8", "1.1.8"))
    }

    @Test
    fun installFinished_falseWhenInstallCanceledOrMissing() {
        // 用户取消安装：当前版本仍低于目标版本，保留安装包
        assertFalse(UpdateDownloader.installFinished("v1.1.8", "1.1.7"))
        assertFalse(UpdateDownloader.installFinished(null, "1.1.8"))
        assertFalse(UpdateDownloader.installFinished("v1.1.8", null))
        assertFalse(UpdateDownloader.installFinished("", ""))
    }

    @Test
    fun versionFromFileName_parsesReleaseAssetNames() {
        assertEquals("1.2.2", UpdateDownloader.versionFromFileName("MultiTimeAlarm-v1.2.2-arm64-v8a.apk"))
        assertEquals("1.2.2", UpdateDownloader.versionFromFileName("MultiTimeAlarm-1.2.2-arm64-v8a.apk"))
        assertEquals(
            "1.2.2",
            UpdateDownloader.versionFromFileName("MultiTimeAlarm-v1.2.2-arm64-v8a-release-unsigned.apk"),
        )
        // 大小写不敏感，兼容旧资产命名
        assertEquals("1.2.2", UpdateDownloader.versionFromFileName("multitimealarm-V1.2.2-x.apk"))
    }

    @Test
    fun versionFromFileName_returnsNullForUnknownNames() {
        assertNull(UpdateDownloader.versionFromFileName("random.apk"))
        assertNull(UpdateDownloader.versionFromFileName("MultiTimeAlarm-latest-arm64-v8a.apk"))
        assertNull(UpdateDownloader.versionFromFileName(""))
    }

    @Test
    fun pickPendingFileName_usesRealDeviceDirectory() {
        // 真机上实际堆积过的文件（v1.2.0 / 1.2.1 / 1.2.2 各一份），外加两个更高版本
        val files = listOf(
            "MultiTimeAlarm-v1.2.0-arm64-v8a.apk",
            "MultiTimeAlarm-v1.2.1-arm64-v8a.apk",
            "MultiTimeAlarm-v1.2.2-arm64-v8a.apk",
            "MultiTimeAlarm-v9.9.8-arm64-v8a.apk",
            "MultiTimeAlarm-v9.9.9-arm64-v8a.apk",
        )
        // 当前 1.2.2：应挑出最高的可重装版本
        assertEquals("MultiTimeAlarm-v9.9.9-arm64-v8a.apk", UpdateDownloader.pickPendingFileName(files, "1.2.2"))
        // 当前已是 9.9.9：没有更高版本，目录里的包都会被当作过期清理
        assertNull(UpdateDownloader.pickPendingFileName(files, "9.9.9"))
    }

    @Test
    fun pickPendingFileName_returnsNullWhenNothingInstallable() {
        assertNull(UpdateDownloader.pickPendingFileName(listOf("MultiTimeAlarm-v1.2.2-arm64-v8a.apk"), "1.2.2"))
        assertNull(UpdateDownloader.pickPendingFileName(emptyList(), "1.2.2"))
        assertNull(UpdateDownloader.pickPendingFileName(listOf("MultiTimeAlarm-v1.2.3-arm64-v8a.apk"), null))
    }

    @Test
    fun pickPendingFileName_prefersHighestVersionNotFileNameOrder() {
        val files = listOf(
            "MultiTimeAlarm-v1.9.9-arm64-v8a.apk",
            "MultiTimeAlarm-v1.10.0-arm64-v8a.apk",
        )
        assertEquals("MultiTimeAlarm-v1.10.0-arm64-v8a.apk", UpdateDownloader.pickPendingFileName(files, "1.2.2"))
    }
}
