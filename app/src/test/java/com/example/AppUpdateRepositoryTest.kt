package com.example

import com.example.data.model.GitHubArtifactItem
import com.example.data.model.GitHubReleaseAsset
import com.example.data.model.GitHubReleaseResponse
import com.example.data.repository.AppUpdateRepository
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun parsesAndPicksLatestNonExpiredActionsArtifact() {
        assertEquals(12, AppUpdateRepository.parseArtifactBuildNumber("GitNote-APK-12"))
        assertEquals(9, AppUpdateRepository.parseArtifactBuildNumber("GitNote-APK-9"))

        val artifacts = listOf(
            GitHubArtifactItem(id = 10L, name = "GitNote-APK-10", expired = false, createdAt = "2026-11-24T10:00:00Z"),
            GitHubArtifactItem(id = 12L, name = "GitNote-APK-12", expired = false, createdAt = "2026-11-24T11:02:22Z"),
            GitHubArtifactItem(id = 15L, name = "GitNote-APK-15", expired = true, createdAt = "2026-11-24T12:00:00Z")
        )
        assertEquals("GitNote-APK-12", AppUpdateRepository.pickPreferredArtifact(artifacts)?.name)
    }

    @Test
    fun prefersReleaseOnSameBuildAndArtifactWhenNewer() {
        val release12 = GitHubReleaseResponse(
            tagName = "build-12",
            publishedAt = "2026-11-24T11:02:25Z",
            assets = listOf(GitHubReleaseAsset(id = 1L, name = "GitNote-build-12-debug.apk"))
        )
        val artifact12 = GitHubArtifactItem(
            id = 100L,
            name = "GitNote-APK-12",
            createdAt = "2026-11-24T11:02:20Z"
        )
        val artifact13 = GitHubArtifactItem(
            id = 101L,
            name = "GitNote-APK-13",
            createdAt = "2026-11-24T11:15:00Z"
        )

        assertTrue(AppUpdateRepository.shouldPreferArtifactOverRelease(null, artifact12, isDebugBuild = true))
        assertFalse(AppUpdateRepository.shouldPreferArtifactOverRelease(release12, artifact12, isDebugBuild = true))
        assertTrue(AppUpdateRepository.shouldPreferArtifactOverRelease(release12, artifact13, isDebugBuild = true))
    }

    @Test
    fun extractsPreferredApkSafelyFromArtifactZip() {
        val tempDir = createTempDirCompat("apk_zip_test")
        val zipFile = File(tempDir, "GitNote-APK-12.zip")
        val outDir = File(tempDir, "out")
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("../../GitNote-build-12-debug.apk"))
                zos.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("GitNote-build-12-release.apk"))
                zos.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x02))
                zos.closeEntry()
            }

            val extractedDebug = AppUpdateRepository.extractApkFromZip(zipFile, outDir, isDebugBuild = true)
            assertNotNull(extractedDebug)
            assertEquals("GitNote-build-12-debug.apk", extractedDebug!!.name)
            assertEquals(outDir.canonicalPath, extractedDebug.parentFile?.canonicalPath)
            assertTrue(AppUpdateRepository.isValidApkHeader(extractedDebug))
        } finally {
            tempDir.deleteRecursively()
        }
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

    private fun createTempDirCompat(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "${prefix}_${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }
}
