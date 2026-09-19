package com.example.multitimealarm

import com.example.multitimealarm.util.UpdateDownloader
import org.junit.Assert.assertFalse
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
}
