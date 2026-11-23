package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.GitHubApiService
import com.example.data.model.AppUpdateConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.model.GitHubReleaseAsset
import com.example.data.model.GitHubReleaseResponse
import com.squareup.moshi.Moshi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppUpdateRepository {

    private val moshi: Moshi = Moshi.Builder().build()

    private fun buildService(token: String): GitHubApiService {
        val authInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Cache-Control", "no-cache")
            if (token.isNotBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }

    fun parseRepoInput(repoInput: String): Pair<String, String>? {
        val cleaned = repoInput.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split('/')
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return parts[0].trim() to parts[1].trim()
    }

    suspend fun checkLatestRelease(
        config: AppUpdateConfig,
        fallbackToken: String
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val effectiveToken = config.token.trim().ifBlank { fallbackToken.trim() }
        val service = buildService(effectiveToken)

        try {
            val latestResponse = service.getLatestRelease(config.owner, config.repo)
            val release: GitHubReleaseResponse? = when {
                latestResponse.isSuccessful -> latestResponse.body()
                latestResponse.code() == 404 -> {
                    // Fallback to listing releases (covers prereleases or build-* tags)
                    val listResponse = service.listReleases(config.owner, config.repo, perPage = 5)
                    if (listResponse.isSuccessful) {
                        listResponse.body()?.firstOrNull { !it.draft }
                    } else {
                        return@withContext Result.failure(
                            IOException(mapUpdateHttpError(listResponse.code(), effectiveToken.isBlank()))
                        )
                    }
                }
                else -> {
                    return@withContext Result.failure(
                        IOException(mapUpdateHttpError(latestResponse.code(), effectiveToken.isBlank()))
                    )
                }
            }

            if (release == null) {
                return@withContext Result.failure(IOException("该仓库尚未发布任何 GitHub Release"))
            }

            val apkAsset = pickPreferredApkAsset(release.assets, isDebugBuild = BuildConfig.DEBUG)
            val isNewer = isReleaseNewer(
                remoteTag = release.tagName,
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE
            )
            val fallbackUrl = "https://github.com/${config.owner}/${config.repo}/releases/tag/${release.tagName}"
            val safeReleaseUrl = release.htmlUrl.trim().takeIf {
                it.startsWith("https://github.com/")
            } ?: fallbackUrl

            val apkApiUrl = apkAsset?.let { asset ->
                val rawUrl = asset.url.trim()
                when {
                    rawUrl.startsWith("https://api.github.com/") -> rawUrl
                    asset.id > 0L -> "https://api.github.com/repos/${config.owner}/${config.repo}/releases/assets/${asset.id}"
                    else -> null
                }
            }

            Result.success(
                AppUpdateInfo(
                    tagName = release.tagName,
                    releaseName = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                    releaseNotes = release.body.orEmpty().trim(),
                    releaseUrl = safeReleaseUrl,
                    publishedAt = release.publishedAt.orEmpty(),
                    apkAssetId = apkAsset?.id?.takeIf { it > 0L },
                    apkAssetName = apkAsset?.name,
                    apkApiUrl = apkApiUrl,
                    apkSizeBytes = apkAsset?.size ?: 0L,
                    isNewer = isNewer
                )
            )
        } catch (e: IOException) {
            Result.failure(IOException("网络异常，无法连接 GitHub 检查更新"))
        } catch (e: Exception) {
            Result.failure(IOException("检查更新失败：${e.localizedMessage ?: "未知错误"}"))
        }
    }

    /**
     * Downloads a Release APK asset from either a private or public GitHub repository.
     *
     * Private repository assets MUST be requested via the GitHub REST API asset endpoint with
     * `Accept: application/octet-stream` and `Authorization: Bearer <token>`. GitHub responds with
     * a 302 redirect to a pre-signed object storage URL (`objects.githubusercontent.com`), which
     * must be followed WITHOUT the `Authorization` header (otherwise S3/Azure returns HTTP 400).
     */
    suspend fun downloadApkAsset(
        cacheDir: File,
        config: AppUpdateConfig,
        fallbackToken: String,
        updateInfo: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val assetUrl = updateInfo.apkApiUrl
            ?: updateInfo.apkAssetId?.let { id ->
                "https://api.github.com/repos/${config.owner}/${config.repo}/releases/assets/$id"
            }
            ?: return@withContext Result.failure(IOException("当前 Release 未附带可下载的 APK 安装包"))

        val effectiveToken = config.token.trim().ifBlank { fallbackToken.trim() }

        val updatesDir = File(cacheDir, "updates").apply { mkdirs() }
        // Remove previous update APKs and stale partial files
        updatesDir.listFiles()?.forEach { file ->
            if (file.isFile) runCatching { file.delete() }
        }

        val safeFileName = sanitizeApkFileName(updateInfo.apkAssetName ?: "GitNote-${updateInfo.tagName}.apk")
        val targetFile = File(updatesDir, safeFileName)
        val partFile = File(updatesDir, "$safeFileName.part")

        val noRedirectClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val downloadClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        try {
            val apiRequestBuilder = Request.Builder()
                .url(assetUrl)
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
            if (effectiveToken.isNotBlank()) {
                apiRequestBuilder.header("Authorization", "Bearer $effectiveToken")
            }

            val firstResponse = noRedirectClient.newCall(apiRequestBuilder.build()).execute()
            val finalResponse = if (firstResponse.code in setOf(301, 302, 303, 307, 308)) {
                val redirectUrl = firstResponse.header("Location")
                firstResponse.close()
                if (redirectUrl.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("GitHub 未返回有效的下载重定向地址"))
                }
                // Follow pre-signed storage URL WITHOUT Authorization header
                val cdnRequest = Request.Builder()
                    .url(redirectUrl)
                    .get()
                    .build()
                downloadClient.newCall(cdnRequest).execute()
            } else {
                firstResponse
            }

            finalResponse.use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException(mapDownloadHttpError(response.code, effectiveToken.isBlank()))
                    )
                }

                val body = response.body
                    ?: return@withContext Result.failure(IOException("下载响应为空，请重试"))

                val contentLength = body.contentLength().takeIf { it > 0L } ?: updateInfo.apkSizeBytes
                var downloaded = 0L
                var lastReportTime = 0L
                onProgress(0L, contentLength)

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastReportTime >= 80L || (contentLength > 0L && downloaded >= contentLength)) {
                                lastReportTime = now
                                onProgress(downloaded, contentLength)
                            }
                        }
                        output.flush()
                    }
                }

                if (downloaded <= 0L || !isValidApkHeader(partFile)) {
                    runCatching { partFile.delete() }
                    return@withContext Result.failure(
                        IOException("下载的文件不是有效的 APK 安装包，请检查仓库 Token 权限或 Release 附件")
                    )
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }
                onProgress(downloaded, if (contentLength > 0L) contentLength else downloaded)
                Result.success(targetFile)
            }
        } catch (e: CancellationException) {
            runCatching { partFile.delete() }
            throw e
        } catch (e: IOException) {
            runCatching { partFile.delete() }
            Result.failure(IOException("下载中断或网络异常：${e.localizedMessage ?: "请重试"}"))
        } catch (e: Exception) {
            runCatching { partFile.delete() }
            Result.failure(IOException("下载安装包失败：${e.localizedMessage ?: "未知错误"}"))
        }
    }

    private fun mapUpdateHttpError(code: Int, tokenEmpty: Boolean): String = when (code) {
        401 -> "更新源 Token 无效或已过期，请检查配置"
        403 -> "权限不足，请确认 Token 具备该私有仓库的读取权限"
        404 -> if (tokenEmpty) {
            "找不到该仓库或 Release（私有仓库需要配置 Token）"
        } else {
            "找不到该仓库或尚未创建任何 Release"
        }
        else -> "检查更新失败（HTTP $code）"
    }

    private fun mapDownloadHttpError(code: Int, tokenEmpty: Boolean): String = when (code) {
        401 -> "下载鉴权失败：Token 无效或已过期"
        403 -> "无权限下载该私有仓库附件，请检查 Token 的 Contents 读取权限"
        404 -> if (tokenEmpty) {
            "无法下载附件（私有仓库需要配置具备读取权限的 Token）"
        } else {
            "找不到对应的 APK 附件，可能已被删除"
        }
        else -> "下载安装包失败（HTTP $code）"
    }

    companion object {
        internal fun sanitizeApkFileName(rawName: String): String {
            val base = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
            val safe = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val nonEmpty = safe.ifBlank { "gitnote-update.apk" }
            return if (nonEmpty.endsWith(".apk", ignoreCase = true)) nonEmpty else "$nonEmpty.apk"
        }

        internal fun isValidApkHeader(file: File): Boolean {
            if (!file.exists() || file.length() < 4L) return false
            val header = ByteArray(4)
            return runCatching {
                FileInputStream(file).use { it.read(header) } == 4 &&
                    header[0] == 0x50.toByte() && // 'P'
                    header[1] == 0x4B.toByte() && // 'K'
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            }.getOrDefault(false)
        }

        internal fun pickPreferredApkAsset(
            assets: List<GitHubReleaseAsset>,
            isDebugBuild: Boolean
        ): GitHubReleaseAsset? {
            val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            if (apks.isEmpty()) return null
            val preferredHint = if (isDebugBuild) "debug" else "release"
            return apks.firstOrNull { it.name.contains(preferredHint, ignoreCase = true) }
                ?: apks.first()
        }

        internal fun isReleaseNewer(
            remoteTag: String,
            currentVersionName: String,
            currentVersionCode: Int
        ): Boolean {
            val normalizedTag = remoteTag.trim()
            if (normalizedTag.isEmpty()) return false

            // Handle GitHub Actions tags like "build-12"
            val buildMatch = Regex("^build[-_]?(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(normalizedTag)
            if (buildMatch != null) {
                val remoteBuildNum = buildMatch.groupValues[1].toIntOrNull() ?: return false
                return remoteBuildNum > currentVersionCode
            }

            // Handle semantic version tags like "v1.2.0", "1.0.3-beta"
            val remoteNums = extractVersionNumbers(normalizedTag)
            val currentNums = extractVersionNumbers(currentVersionName)
            if (remoteNums.isEmpty() || currentNums.isEmpty()) {
                return normalizedTag.removePrefix("v").removePrefix("V") !=
                    currentVersionName.removePrefix("v").removePrefix("V")
            }

            val maxLen = maxOf(remoteNums.size, currentNums.size)
            for (i in 0 until maxLen) {
                val r = remoteNums.getOrElse(i) { 0 }
                val c = currentNums.getOrElse(i) { 0 }
                if (r != c) return r > c
            }
            return false
        }

        private fun extractVersionNumbers(raw: String): List<Int> {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            if (cleaned.isEmpty()) return emptyList()
            val parts = cleaned.split('.')
            if (parts.any { it.toIntOrNull() == null }) return emptyList()
            return parts.map { it.toInt() }
        }
    }
}
