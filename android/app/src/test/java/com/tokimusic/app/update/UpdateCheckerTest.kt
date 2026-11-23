package com.tokimusic.app.update

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 第三轮第 15 项：版本比较、release 解析与检查流程的回归测试。 */
class UpdateCheckerTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun compareVersionsNewer() {
        assertTrue(compareVersions("0.1.6", "0.1.7") > 0)
        assertTrue(compareVersions("0.1.9", "0.1.10") > 0)
        assertTrue(compareVersions("0.1.6", "0.2.0") > 0)
        assertTrue(compareVersions("0.1.6", "v0.1.7") > 0)
    }

    @Test fun compareVersionsSameOrOlder() {
        assertEquals(0, compareVersions("0.1.6", "0.1.6"))
        assertEquals(0, compareVersions("v0.1.6", "0.1.6"))
        // -aN/-bN 后缀不参与比较（预发布与正式版同号时不提示更新）。
        assertEquals(0, compareVersions("0.1.6", "0.1.6-b1"))
        assertTrue(compareVersions("0.1.7", "0.1.6") < 0)
    }

    private fun sampleJson(tag: String) = """
        {
          "tag_name": "$tag",
          "body": "1 Android 修复了播放器问题",
          "assets": [
            {"name": "TokiMusic-${tag.removePrefix("v")}.apk", "browser_download_url": "https://example.com/TokiMusic.apk", "size": 27832027},
            {"name": "TokiMusic-${tag.removePrefix("v")}.aab", "browser_download_url": "https://example.com/TokiMusic.aab", "size": 29827677}
          ]
        }
    """.trimIndent()

    @Test fun parseLatestReleaseFindsApk() {
        val release = parseLatestRelease(sampleJson("v0.1.7"))!!
        assertEquals("0.1.7", release.version)
        assertEquals("1 Android 修复了播放器问题", release.notes)
        assertEquals("https://example.com/TokiMusic.apk", release.apkUrl)
    }

    @Test fun parseLatestReleaseCapturesApkSize() {
        val release = parseLatestRelease(sampleJson("v0.1.7"))!!
        assertEquals(27_832_027L, release.apkSizeBytes)
    }

    @Test fun parseLatestReleaseWithoutApkAsset() {
        val release = parseLatestRelease("""{"tag_name": "v0.1.7", "body": "", "assets": []}""")!!
        assertEquals("0.1.7", release.version)
        assertNull(release.apkUrl)
    }

    @Test fun checkForUpdateFindsNewVersion() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(sampleJson("v0.1.7")))
        val result = UpdateChecker.checkForUpdate("0.1.6", server.url("releases/latest").toString())
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals("0.1.7", (result as UpdateCheckResult.UpdateAvailable).release.version)
    }

    @Test fun checkForUpdateUpToDate() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(sampleJson("v0.1.6")))
        val result = UpdateChecker.checkForUpdate("0.1.6", server.url("releases/latest").toString())
        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test fun checkForUpdateFailedOnServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        val result = UpdateChecker.checkForUpdate("0.1.6", server.url("releases/latest").toString())
        assertEquals(UpdateCheckResult.Failed, result)
    }
}
