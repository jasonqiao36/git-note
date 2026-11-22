package com.example.data.repository

import android.net.Uri
import android.util.Base64
import com.example.data.api.GitHubApiService
import com.example.data.model.GitTreeItem
import com.example.data.model.NoteDocument
import com.example.data.model.PutFileRequest
import com.example.data.model.RepoConfig
import com.example.data.model.RepoTreeNode
import com.example.data.model.SaveResult
import com.squareup.moshi.Moshi
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class GitHubNotesRepository {

    private val moshi: Moshi = Moshi.Builder().build()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedService: GitHubApiService? = null

    private fun serviceFor(token: String): GitHubApiService {
        if (cachedToken == token && cachedService != null) {
            return cachedService!!
        }
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Cache-Control", "no-cache")
                .build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(GitHubApiService::class.java).also {
            cachedToken = token
            cachedService = it
        }
    }

    suspend fun verifyAndResolveConfig(
        repoInput: String,
        tokenInput: String,
        branchInput: String
    ): Result<RepoConfig> = withContext(Dispatchers.IO) {
        val cleanedRepo = repoInput.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleanedRepo.split("/")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("仓库格式请填写 owner/repo，例如 octocat/notes"))
        }
        val owner = parts[0].trim()
        val repo = parts[1].trim()
        val token = tokenInput.trim()
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("请输入 GitHub Personal Access Token"))
        }

        try {
            val response = serviceFor(token).getRepository(owner, repo)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return@withContext Result.failure(IOException("无法解析 GitHub 仓库响应"))
                val resolvedBranch = branchInput.trim().ifBlank { body.defaultBranch.ifBlank { "main" } }
                Result.success(
                    RepoConfig(
                        owner = owner,
                        repo = repo,
                        branch = resolvedBranch,
                        token = token
                    )
                )
            } else {
                Result.failure(IOException(mapHttpError(response.code(), "仓库或权限验证失败")))
            }
        } catch (e: IOException) {
            Result.failure(IOException("网络连接失败，请检查网络设置后重试"))
        } catch (e: Exception) {
            Result.failure(IOException("连接异常：${e.localizedMessage ?: "未知错误"}"))
        }
    }

    suspend fun fetchFullTree(config: RepoConfig): Result<List<RepoTreeNode>> = withContext(Dispatchers.IO) {
        try {
            val response = serviceFor(config.token).getGitTree(
                owner = config.owner,
                repo = config.repo,
                branch = encodePathSegments(config.branch),
                recursive = 1
            )
            if (response.isSuccessful) {
                val items = response.body()?.tree.orEmpty()
                Result.success(buildTreeNodes(items))
            } else if (response.code() == 409) {
                // Brand-new empty GitHub repository has no commits yet
                Result.success(emptyList())
            } else {
                Result.failure(IOException(mapHttpError(response.code(), "加载目录树失败")))
            }
        } catch (e: IOException) {
            Result.failure(IOException("无法连接到 GitHub，请检查网络"))
        } catch (e: Exception) {
            Result.failure(IOException("拉取目录失败：${e.localizedMessage ?: "未知错误"}"))
        }
    }

    suspend fun fetchFileContent(config: RepoConfig, path: String): Result<NoteDocument> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = encodePathSegments(path)
            val response = serviceFor(config.token).getFileContent(
                owner = config.owner,
                repo = config.repo,
                path = encodedPath,
                ref = config.branch
            )
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return@withContext Result.failure(IOException("文件内容为空"))
                val decoded = decodeGitHubBase64(body.content)
                Result.success(
                    NoteDocument(
                        path = body.path,
                        sha = body.sha,
                        content = decoded
                    )
                )
            } else if (response.code() == 404) {
                Result.failure(FileNotFoundOnRemoteException("文件在远端仓库中已不存在"))
            } else {
                Result.failure(IOException(mapHttpError(response.code(), "读取笔记失败")))
            }
        } catch (e: IOException) {
            Result.failure(IOException("网络错误，无法读取文件"))
        } catch (e: Exception) {
            Result.failure(IOException("读取文件异常：${e.localizedMessage ?: "未知错误"}"))
        }
    }

    suspend fun saveFile(
        config: RepoConfig,
        path: String,
        content: String,
        existingSha: String?,
        isNewFile: Boolean
    ): SaveResult = withContext(Dispatchers.IO) {
        try {
            val encodedPath = encodePathSegments(path)
            val encodedBody = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val commitMessage = if (isNewFile) "Create $path" else "Update $path"
            val request = PutFileRequest(
                message = commitMessage,
                content = encodedBody,
                sha = if (isNewFile) null else existingSha,
                branch = config.branch
            )
            val response = serviceFor(config.token).putFileContent(
                owner = config.owner,
                repo = config.repo,
                path = encodedPath,
                request = request
            )
            when {
                response.isSuccessful -> {
                    val newSha = response.body()?.content?.sha.orEmpty()
                    SaveResult.Success(newSha)
                }
                response.code() == 409 -> SaveResult.Conflict
                response.code() == 422 && isNewFile -> SaveResult.Error("同名文件已存在，请换一个文件名")
                else -> SaveResult.Error(mapHttpError(response.code(), "保存失败"))
            }
        } catch (e: IOException) {
            SaveResult.Error("网络中断，未能提交到 GitHub")
        } catch (e: Exception) {
            SaveResult.Error("保存异常：${e.localizedMessage ?: "未知错误"}")
        }
    }

    suspend fun overwriteFileWithLatestSha(
        config: RepoConfig,
        path: String,
        content: String
    ): SaveResult = withContext(Dispatchers.IO) {
        val latestResult = fetchFileContent(config, path)
        val latestSha = latestResult.getOrNull()?.sha
        saveFile(
            config = config,
            path = path,
            content = content,
            existingSha = latestSha,
            isNewFile = latestSha == null
        )
    }

    suspend fun createFolder(config: RepoConfig, folderPath: String): SaveResult = withContext(Dispatchers.IO) {
        val cleanFolder = folderPath.trim('/')
        val gitkeepPath = "$cleanFolder/.gitkeep"
        try {
            val encodedPath = encodePathSegments(gitkeepPath)
            val request = PutFileRequest(
                message = "Create folder $cleanFolder",
                content = "",
                sha = null,
                branch = config.branch
            )
            val response = serviceFor(config.token).putFileContent(
                owner = config.owner,
                repo = config.repo,
                path = encodedPath,
                request = request
            )
            when {
                response.isSuccessful -> SaveResult.Success(response.body()?.content?.sha.orEmpty())
                response.code() == 422 -> SaveResult.Error("该文件夹已存在")
                else -> SaveResult.Error(mapHttpError(response.code(), "创建文件夹失败"))
            }
        } catch (e: IOException) {
            SaveResult.Error("网络错误，无法创建文件夹")
        } catch (e: Exception) {
            SaveResult.Error("创建文件夹异常：${e.localizedMessage ?: "未知错误"}")
        }
    }

    private fun buildTreeNodes(items: List<GitTreeItem>): List<RepoTreeNode> {
        val nodeMap = LinkedHashMap<String, RepoTreeNode>()

        for (item in items) {
            val cleanPath = item.path.trim('/')
            if (cleanPath.isEmpty()) continue
            val isDir = item.type == "tree"
            val name = cleanPath.substringAfterLast('/')
            val parent = if ('/' in cleanPath) cleanPath.substringBeforeLast('/') else ""

            // Ensure all parent directory nodes exist even if API omits intermediate trees
            ensureParentDirs(parent, nodeMap)

            nodeMap[cleanPath] = RepoTreeNode(
                name = name,
                path = cleanPath,
                parentPath = parent,
                isDirectory = isDir,
                sha = item.sha,
                size = item.size ?: 0L
            )
        }

        return nodeMap.values.sortedWith(
            compareByDescending<RepoTreeNode> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun ensureParentDirs(parentPath: String, nodeMap: MutableMap<String, RepoTreeNode>) {
        if (parentPath.isEmpty()) return
        val segments = parentPath.split('/')
        var acc = ""
        for (segment in segments) {
            val prev = acc
            acc = if (acc.isEmpty()) segment else "$acc/$segment"
            if (!nodeMap.containsKey(acc)) {
                nodeMap[acc] = RepoTreeNode(
                    name = segment,
                    path = acc,
                    parentPath = prev,
                    isDirectory = true
                )
            }
        }
    }

    private fun encodePathSegments(path: String): String {
        return path.trim('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment -> Uri.encode(segment) }
    }

    private fun decodeGitHubBase64(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val sanitized = raw.replace("\n", "").replace("\r", "").trim()
        if (sanitized.isEmpty()) return ""
        val bytes = Base64.decode(sanitized, Base64.DEFAULT)
        return String(bytes, Charsets.UTF_8)
    }

    private fun mapHttpError(code: Int, fallback: String): String = when (code) {
        401 -> "Token 无效或已过期，请在设置中重新配置"
        403 -> "权限不足（请确认 Token 已开启该仓库的 Contents 读写权限）"
        404 -> "找不到该仓库、分支或文件，请检查名称是否正确"
        409 -> "检测到远端冲突，请刷新后重试"
        422 -> "请求参数无效或目标已存在"
        else -> "$fallback（HTTP $code）"
    }
}

class FileNotFoundOnRemoteException(message: String) : IOException(message)
