package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.GitHubApiService
import com.example.data.model.AppUpdateConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.model.GitHubArtifactItem
import com.example.data.model.GitHubReleaseAsset
import com.example.data.model.GitHubReleaseResponse
import com.squareup.moshi.Moshi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
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
                .header("User-Agent", "GitNote-Android")
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
            val releasesResponse = service.listReleases(config.owner, config.repo, perPage = 15)
            val releasesCode = releasesResponse.code()
            val nonDraftReleases = if (releasesResponse.isSuccessful) {
                releasesResponse.body().orEmpty().filter { !it.draft }
            } else {
                emptyList()
            }
            val bestRelease = pickBestRelease(nonDraftReleases, isDebugBuild = BuildConfig.DEBUG)

            val artifactsResponse = runCatching {
                service.listActionsArtifacts(config.owner, config.repo, perPage = 20)
            }.getOrNull()
            val artifactsCode = artifactsResponse?.code()
            val bestArtifact = if (artifactsResponse?.isSuccessful == true) {
                pickPreferredArtifact(artifactsResponse.body()?.artifacts.orEmpty())
            } else {
                null
            }

            if (bestRelease == null && bestArtifact == null) {
                val message = when {
                    !releasesResponse.isSuccessful -> {
                        mapUpdateHttpError(releasesCode, effectiveToken.isBlank())
                    }
                    artifactsCode == 401 -> "更新源 Token 无效或已过期，请检查配置"
                    artifactsCode == 403 -> {
                        "该仓库尚未发布 GitHub Release；若需检查 Actions 构建产物，请为 Token 开启 Actions 只读权限"
                    }
                    else -> {
                        "该仓库尚未发布任何 GitHub Release，也未找到可用的 Actions APK 构建产物"
                    }
                }
                return@withContext Result.failure(IOException(message))
            }

            val useArtifact = shouldPreferArtifactOverRelease(
                release = bestRelease,
                artifact = bestArtifact,
                isDebugBuild = BuildConfig.DEBUG
            )

            if (useArtifact && bestArtifact != null) {
                return@withContext Result.success(
                    buildArtifactUpdateInfo(
                        config = config,
                        artifact = bestArtifact,
                        hasToken = effectiveToken.isNotBlank()
                    )
                )
            }

            val release = bestRelease!!
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
                    publishedAt = release.publishedAt ?: release.createdAt.orEmpty(),
                    apkAssetId = apkAsset?.id?.takeIf { it > 0L },
                    apkAssetName = apkAsset?.name,
                    apkApiUrl = apkApiUrl,
                    apkSizeBytes = apkAsset?.size ?: 0L,
                    isNewer = isNewer,
                    isZipArchive = false,
                    requiresTokenForDownload = false
                )
            )
        } catch (e: IOException) {
            Result.failure(IOException("网络异常，无法连接 GitHub 检查更新"))
        } catch (e: Exception) {
            Result.failure(IOException("检查更新失败：${e.localizedMessage ?: "未知错误"}"))
        }
    }

    private fun buildArtifactUpdateInfo(
        config: AppUpdateConfig,
        artifact: GitHubArtifactItem,
        hasToken: Boolean
    ): AppUpdateInfo {
        val buildNum = parseArtifactBuildNumber(artifact.name)
        val tagName = if (buildNum != null) "build-$buildNum" else artifact.name
        val displayName = if (buildNum != null) {
            "GitHub Actions Build #$buildNum"
        } else {
            artifact.name
        }
        val runId = artifact.workflowRun?.id?.takeIf { it > 0L }
        val actionUrl = if (runId != null) {
            "https://github.com/${config.owner}/${config.repo}/actions/runs/$runId"
        } else {
            "https://github.com/${config.owner}/${config.repo}/actions"
        }
        val branchInfo = artifact.workflowRun?.headBranch?.takeIf { it.isNotBlank() }
        val notes = buildString {
            append("来自 GitHub Actions 构建产物（${artifact.name}.zip）。")
            if (branchInfo != null) {
                append("\n分支：$branchInfo")
            }
            if (!hasToken) {
                append("\n\n下载 Actions 产物需要配置具备 Actions 只读权限的 Token；也可点击「浏览器查看」手动下载。")
            }
        }
        val apiUrl = artifact.archiveDownloadUrl.trim().takeIf {
            it.startsWith("https://api.github.com/")
        } ?: "https://api.github.com/repos/${config.owner}/${config.repo}/actions/artifacts/${artifact.id}/zip"

        val isNewer = if (buildNum != null) {
            buildNum > BuildConfig.VERSION_CODE
        } else {
            isReleaseNewer(
                remoteTag = tagName,
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE
            )
        }

        return AppUpdateInfo(
            tagName = tagName,
            releaseName = displayName,
            releaseNotes = notes,
            releaseUrl = actionUrl,
            publishedAt = artifact.createdAt.orEmpty(),
            apkAssetId = artifact.id.takeIf { it > 0L },
            apkAssetName = "${artifact.name}.zip",
            apkApiUrl = apiUrl,
            apkSizeBytes = artifact.sizeInBytes,
            isNewer = isNewer,
            isZipArchive = true,
            requiresTokenForDownload = !hasToken
        )
    }

    /**
     * Downloads a Release APK asset or Actions Artifact zip from a private or public GitHub repository.
     *
     * Private repository assets MUST be requested via the GitHub REST API endpoint with
     * `Authorization: Bearer <token>`. GitHub responds with a 302 redirect to a pre-signed object
     * storage URL, which must be followed WITHOUT the `Authorization` header (otherwise S3/Azure
     * returns HTTP 400).
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
                if (updateInfo.isZipArchive) {
                    "https://api.github.com/repos/${config.owner}/${config.repo}/actions/artifacts/$id/zip"
                } else {
                    "https://api.github.com/repos/${config.owner}/${config.repo}/releases/assets/$id"
                }
            }
            ?: return@withContext Result.failure(IOException("当前构建未附带可下载的 APK 安装包"))

        val effectiveToken = config.token.trim().ifBlank { fallbackToken.trim() }
        if (updateInfo.isZipArchive && effectiveToken.isBlank()) {
            return@withContext Result.failure(
                IOException("下载 Actions 构建产物需要在更新源中配置具备 Actions 只读权限的 Token")
            )
        }

        val updatesDir = File(cacheDir, "updates").apply { mkdirs() }
        // Remove previous update APKs and stale partial files
        updatesDir.listFiles()?.forEach { file ->
            if (file.isFile) runCatching { file.delete() }
        }

        val safeFileName = sanitizeApkFileName(updateInfo.apkAssetName ?: "GitNote-${updateInfo.tagName}.apk")
        val targetFile = File(updatesDir, safeFileName)
        val partFile = File(
            updatesDir,
            if (updateInfo.isZipArchive) "artifact_download.zip.part" else "$safeFileName.part"
        )

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
            val acceptHeader = if (updateInfo.isZipArchive) {
                "application/vnd.github+json"
            } else {
                "application/octet-stream"
            }
            val apiRequestBuilder = Request.Builder()
                .url(assetUrl)
                .header("Accept", acceptHeader)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "GitNote-Android")
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
                        IOException(
                            mapDownloadHttpError(
                                code = response.code,
                                tokenEmpty = effectiveToken.isBlank(),
                                isZipArchive = updateInfo.isZipArchive
                            )
                        )
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
                        IOException("下载的文件不是有效的 APK 或压缩包，请检查仓库 Token 权限")
                    )
                }

                val finalApkFile = if (updateInfo.isZipArchive) {
                    val extracted = extractApkFromZip(
                        zipFile = partFile,
                        outputDir = updatesDir,
                        isDebugBuild = BuildConfig.DEBUG
                    )
                    runCatching { partFile.delete() }
                    if (extracted == null || !isValidApkHeader(extracted)) {
                        runCatching { extracted?.delete() }
                        return@withContext Result.failure(
                            IOException("Actions 构建产物压缩包中未包含有效的 APK 安装包")
                        )
                    }
                    extracted
                } else {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (!partFile.renameTo(targetFile)) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }
                    targetFile
                }

                onProgress(downloaded, if (contentLength > 0L) contentLength else downloaded)
                Result.success(finalApkFile)
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
        403 -> "权限不足，请确认 Token 具备该仓库的读取权限"
        404 -> if (tokenEmpty) {
            "找不到该仓库（私有仓库需要配置 Token）"
        } else {
            "找不到该仓库，请检查 owner/repo 是否填写正确"
        }
        else -> "检查更新失败（HTTP $code）"
    }

    private fun mapDownloadHttpError(
        code: Int,
        tokenEmpty: Boolean,
        isZipArchive: Boolean
    ): String = when (code) {
        401 -> "下载鉴权失败：Token 无效或已过期"
        403 -> if (isZipArchive) {
            "无权限下载 Actions 构建产物，请确认 Token 具备 Actions 只读权限"
        } else {
            "无权限下载该私有仓库附件，请检查 Token 的 Contents 读取权限"
        }
        404 -> if (tokenEmpty) {
            "无法下载附件（私有仓库需要配置具备读取权限的 Token）"
        } else {
            "找不到对应的构建产物或附件，可能已过期或被删除"
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

        internal fun pickBestRelease(
            releases: List<GitHubReleaseResponse>,
            isDebugBuild: Boolean
        ): GitHubReleaseResponse? {
            val nonDraft = releases.filter { !it.draft }
            if (nonDraft.isEmpty()) return null

            var best = nonDraft.first()
            for (candidate in nonDraft.drop(1)) {
                val candidateBuild = parseBuildTagNumber(candidate.tagName)
                val bestBuild = parseBuildTagNumber(best.tagName)
                val candidateIsNewer = when {
                    candidateBuild != null && bestBuild != null -> candidateBuild > bestBuild
                    else -> isTagStrictlyNewer(candidate.tagName, best.tagName)
                }
                if (candidateIsNewer) {
                    best = candidate
                } else if (candidate.tagName == best.tagName &&
                    pickPreferredApkAsset(best.assets, isDebugBuild) == null &&
                    pickPreferredApkAsset(candidate.assets, isDebugBuild) != null
                ) {
                    best = candidate
                }
            }
            return best
        }

        internal fun parseArtifactBuildNumber(artifactName: String): Int? {
            val trimmed = artifactName.trim()
            val match = Regex("^(?:GitNote[-_])?APK[-_]?(\\d+)$", RegexOption.IGNORE_CASE)
                .matchEntire(trimmed)
                ?: Regex(".*?[-_](\\d+)$").matchEntire(trimmed)
            return match?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        internal fun pickPreferredArtifact(artifacts: List<GitHubArtifactItem>): GitHubArtifactItem? {
            val candidates = artifacts.filter { item ->
                !item.expired &&
                    (item.name.contains("apk", ignoreCase = true) ||
                        item.name.startsWith("GitNote", ignoreCase = true))
            }
            if (candidates.isEmpty()) return null
            return candidates.maxWithOrNull(
                compareBy<GitHubArtifactItem>(
                    { parseArtifactBuildNumber(it.name) ?: -1 },
                    { parseIsoTimestampMillis(it.createdAt) ?: 0L },
                    { it.id }
                )
            )
        }

        internal fun shouldPreferArtifactOverRelease(
            release: GitHubReleaseResponse?,
            artifact: GitHubArtifactItem?,
            isDebugBuild: Boolean
        ): Boolean {
            if (artifact == null) return false
            if (release == null) return true

            val artifactBuild = parseArtifactBuildNumber(artifact.name)
            val releaseBuild = parseBuildTagNumber(release.tagName)
            if (artifactBuild != null && releaseBuild != null) {
                if (artifactBuild > releaseBuild) return true
                if (artifactBuild < releaseBuild) return false
                // Same build number: only use artifact if release has no APK attachment
                return pickPreferredApkAsset(release.assets, isDebugBuild) == null
            }

            val artifactTime = parseIsoTimestampMillis(artifact.createdAt)
            val releaseTime = parseIsoTimestampMillis(release.publishedAt ?: release.createdAt)
            if (artifactTime != null && releaseTime != null) {
                // On the same CI run, Release is published a few seconds after Artifact upload.
                // Prefer Artifact only if it is clearly from a newer run (> 2 minutes later).
                return artifactTime > releaseTime + 120_000L
            }

            return pickPreferredApkAsset(release.assets, isDebugBuild) == null
        }

        internal fun extractApkFromZip(
            zipFile: File,
            outputDir: File,
            isDebugBuild: Boolean
        ): File? {
            if (!zipFile.exists()) return null
            outputDir.mkdirs()
            val extractedFiles = mutableListOf<Pair<String, File>>()
            val buffer = ByteArray(16 * 1024)
            var index = 0

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    try {
                        if (!entry.isDirectory) {
                            val rawName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                            if (rawName.endsWith(".apk", ignoreCase = true)) {
                                val safeName = sanitizeApkFileName(rawName)
                                val tempOut = File(outputDir, "extracted_${index++}_$safeName")
                                FileOutputStream(tempOut).use { fos ->
                                    while (true) {
                                        val read = zis.read(buffer)
                                        if (read == -1) break
                                        fos.write(buffer, 0, read)
                                    }
                                    fos.flush()
                                }
                                extractedFiles.add(safeName to tempOut)
                            }
                        }
                    } finally {
                        zis.closeEntry()
                    }
                }
            }

            if (extractedFiles.isEmpty()) return null
            val preferredHint = if (isDebugBuild) "debug" else "release"
            val chosen = extractedFiles.firstOrNull { (name, _) ->
                name.contains(preferredHint, ignoreCase = true)
            } ?: extractedFiles.first()

            extractedFiles.forEach { (_, file) ->
                if (file != chosen.second) {
                    runCatching { file.delete() }
                }
            }

            val finalFile = File(outputDir, chosen.first)
            if (finalFile.exists()) {
                finalFile.delete()
            }
            return if (chosen.second.renameTo(finalFile)) {
                finalFile
            } else {
                chosen.second.copyTo(finalFile, overwrite = true)
                chosen.second.delete()
                finalFile
            }
        }

        internal fun isReleaseNewer(
            remoteTag: String,
            currentVersionName: String,
            currentVersionCode: Int
        ): Boolean {
            val normalizedTag = remoteTag.trim()
            if (normalizedTag.isEmpty()) return false

            // Handle GitHub Actions tags like "build-12"
            val remoteBuildNum = parseBuildTagNumber(normalizedTag)
            if (remoteBuildNum != null) {
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

        private fun isTagStrictlyNewer(candidateTag: String, baselineTag: String): Boolean {
            val candidateNums = extractVersionNumbers(candidateTag)
            val baselineNums = extractVersionNumbers(baselineTag)
            if (candidateNums.isEmpty() || baselineNums.isEmpty()) return false
            val maxLen = maxOf(candidateNums.size, baselineNums.size)
            for (i in 0 until maxLen) {
                val r = candidateNums.getOrElse(i) { 0 }
                val c = baselineNums.getOrElse(i) { 0 }
                if (r != c) return r > c
            }
            return false
        }

        private fun parseBuildTagNumber(tag: String): Int? {
            val match = Regex("^build[-_]?(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(tag.trim())
            return match?.groupValues?.getOrNull(1)?.toIntOrNull()
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

        internal fun parseIsoTimestampMillis(iso: String?): Long? {
            val raw = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            )
            for (pattern in patterns) {
                val parsed = runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                        isLenient = false
                    }.parse(raw)?.time
                }.getOrNull()
                if (parsed != null) return parsed
            }
            return null
        }
    }
}
