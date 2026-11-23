package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class BrowserViewMode {
    FOLDER,
    TREE
}

enum class BrowserTab {
    FILES,
    RECENT
}

enum class BrowserTabOrder {
    FILES_FIRST,
    RECENT_FIRST
}

enum class RecentFilter {
    ALL,
    EDITED
}

data class RepoConfig(
    val owner: String,
    val repo: String,
    val branch: String,
    val token: String
) {
    val fullName: String get() = "$owner/$repo"
}

data class RepoTreeNode(
    val name: String,
    val path: String,
    val parentPath: String,
    val isDirectory: Boolean,
    val sha: String = "",
    val size: Long = 0L
) {
    val isMarkdown: Boolean
        get() = !isDirectory && (
            name.endsWith(".md", ignoreCase = true) ||
                name.endsWith(".markdown", ignoreCase = true) ||
                name.endsWith(".mdx", ignoreCase = true)
            )
}

@JsonClass(generateAdapter = true)
data class RecentFileEntry(
    val path: String,
    val lastOpenedAt: Long,
    val wasEdited: Boolean = false,
    val isPinned: Boolean = false
) {
    val fileName: String get() = path.substringAfterLast('/')
    val parentDir: String get() = if ('/' in path) path.substringBeforeLast('/') else ""
}

data class NoteDocument(
    val path: String,
    val sha: String,
    val content: String
)

sealed interface SaveResult {
    data class Success(val newSha: String) : SaveResult
    data object Conflict : SaveResult
    data class Error(val message: String) : SaveResult
}

// --- GitHub REST API v3 DTOs ---

@JsonClass(generateAdapter = true)
data class RepoResponse(
    val name: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "default_branch") val defaultBranch: String,
    @Json(name = "private") val isPrivate: Boolean = true,
    val permissions: RepoPermissions? = null
)

@JsonClass(generateAdapter = true)
data class RepoPermissions(
    val admin: Boolean = false,
    val push: Boolean = false,
    val pull: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitTreeResponse(
    val sha: String,
    val tree: List<GitTreeItem> = emptyList(),
    val truncated: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitTreeItem(
    val path: String,
    val mode: String? = null,
    val type: String, // "blob" or "tree"
    val sha: String,
    val size: Long? = null
)

@JsonClass(generateAdapter = true)
data class FileContentResponse(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0L,
    val content: String? = null,
    val encoding: String? = null,
    val type: String = "file"
)

@JsonClass(generateAdapter = true)
data class PutFileRequest(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String? = null
)

@JsonClass(generateAdapter = true)
data class PutFileResponse(
    val content: PutFileContentRef? = null
)

@JsonClass(generateAdapter = true)
data class PutFileContentRef(
    val name: String,
    val path: String,
    val sha: String
)

data class AppUpdateConfig(
    val owner: String,
    val repo: String,
    val token: String = "",
    val autoCheckOnLaunch: Boolean = true
) {
    val fullName: String get() = "$owner/$repo"
}

data class AppUpdateInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val publishedAt: String,
    val apkAssetId: Long?,
    val apkAssetName: String?,
    val apkApiUrl: String?,
    val apkSizeBytes: Long,
    val isNewer: Boolean
)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState {
        val progress: Float?
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data class ReadyToInstall(
        val apkFilePath: String,
        val fileSizeBytes: Long
    ) : UpdateDownloadState

    data class Error(
        val message: String
    ) : UpdateDownloadState
}

@JsonClass(generateAdapter = true)
data class GitHubReleaseResponse(
    @Json(name = "tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseAsset(
    val id: Long = 0L,
    val url: String = "",
    val name: String = "",
    val size: Long = 0L,
    @Json(name = "browser_download_url") val browserDownloadUrl: String = ""
)
