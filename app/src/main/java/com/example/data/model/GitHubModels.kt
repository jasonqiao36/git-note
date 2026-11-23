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
