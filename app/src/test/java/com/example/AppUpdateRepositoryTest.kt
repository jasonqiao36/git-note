package com.example

import com.example.data.model.GitHubReleaseAsset
import com.example.data.repository.AppUpdateRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {

    @Test
    fun comparesSemanticVersionTagsCorrectly() {
        assertTrue(AppUpdateRepository.isReleaseNewer("v1.1.0", "1.0", 1))
        assertTrue(AppUpdateRepository.isReleaseNewer("1.0.1", "1.0", 1))
        assertFalse(AppUpdateRepository.isReleaseNewer("v1.0.0", "1.0", 1))
        assertFalse(AppUpdateRepository.isReleaseNewer("v0.9.5", "1.0", 1))
    }

    @Test
    fun comparesGitHubActionsBuildTagsAgainstVersionCode() {
        assertTrue(AppUpdateRepository.isReleaseNewer("build-12", "1.0", 1))
        assertFalse(AppUpdateRepository.isReleaseNewer("build-1", "1.0", 1))
    }

    @Test
    fun picksPreferredApkMatchingBuildVariant() {
        val assets = listOf(
            GitHubReleaseAsset(
                id = 1L,
                url = "https://api.github.com/repos/o/r/releases/assets/1",
                name = "notes.txt",
                size = 120L,
                browserDownloadUrl = "https://example.com/notes.txt"
            ),
            GitHubReleaseAsset(
                id = 2L,
                url = "https://api.github.com/repos/o/r/releases/assets/2",
                name = "GitNote-v1.1-release.apk",
                size = 10_000_000L,
                browserDownloadUrl = "https://example.com/release.apk"
            ),
            GitHubReleaseAsset(
                id = 3L,
                url = "https://api.github.com/repos/o/r/releases/assets/3",
                name = "GitNote-v1.1-debug.apk",
                size = 12_000_000L,
                browserDownloadUrl = "https://example.com/debug.apk"
            )
        )
        assertEquals(
            "GitNote-v1.1-debug.apk",
            AppUpdateRepository.pickPreferredApkAsset(assets, isDebugBuild = true)?.name
        )
        assertEquals(
            "GitNote-v1.1-release.apk",
            AppUpdateRepository.pickPreferredApkAsset(assets, isDebugBuild = false)?.name
        )
    }

    @Test
    fun sanitizesApkFileNameSafely() {
        assertEquals(
            "GitNote-v1.2.apk",
            AppUpdateRepository.sanitizeApkFileName("../../GitNote-v1.2.apk")
        )
        assertEquals(
            "GitNote_build_10.apk",
            AppUpdateRepository.sanitizeApkFileName("GitNote build 10")
        )
    }

    @Test
    fun validatesApkZipMagicHeader() {
        val validApk = File.createTempFile("valid_test", ".apk")
        val invalidHtml = File.createTempFile("invalid_test", ".apk")
        try {
            validApk.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00))
            invalidHtml.writeText("<html><body>404 Not Found</body></html>")
            assertTrue(AppUpdateRepository.isValidApkHeader(validApk))
            assertFalse(AppUpdateRepository.isValidApkHeader(invalidHtml))
        } finally {
            validApk.delete()
            invalidHtml.delete()
        }
    }
}
