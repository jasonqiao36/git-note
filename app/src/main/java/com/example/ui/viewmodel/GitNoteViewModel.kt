package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ConfigStore
import com.example.data.model.AppUpdateConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.model.BrowserTab
import com.example.data.model.BrowserTabOrder
import com.example.data.model.BrowserViewMode
import com.example.data.model.RecentFileEntry
import com.example.data.model.RecentFilter
import com.example.data.model.RepoConfig
import com.example.data.model.RepoTreeNode
import com.example.data.model.SaveResult
import com.example.data.model.ThemeMode
import com.example.data.model.UpdateDownloadState
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.FileNotFoundOnRemoteException
import com.example.data.repository.GitHubNotesRepository
import com.example.ui.screens.ancestorDirectoriesOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CreateDialogType {
    NOTE,
    FOLDER
}

data class OpenNoteState(
    val path: String,
    val sha: String? = null,
    val originalContent: String = "",
    val draftContent: String = "",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isNewFile: Boolean = false,
    val errorMessage: String? = null,
    val hasConflict: Boolean = false
) {
    val fileName: String get() = path.substringAfterLast('/')
    val parentDir: String get() = if ('/' in path) path.substringBeforeLast('/') else ""
    val isMarkdown: Boolean
        get() = fileName.endsWith(".md", ignoreCase = true) ||
            fileName.endsWith(".markdown", ignoreCase = true) ||
            fileName.endsWith(".mdx", ignoreCase = true)
    val hasUnsavedChanges: Boolean
        get() = !isLoading && (isNewFile || draftContent != originalContent)
}

sealed interface PendingDiscardAction {
    data object CloseNote : PendingDiscardAction
    data class OpenOtherFile(val path: String) : PendingDiscardAction
    data class CreateNewNote(val path: String) : PendingDiscardAction
}

data class GitNoteUiState(
    val isInitializing: Boolean = true,
    val repoConfig: RepoConfig? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoOpenLastNote: Boolean = true,
    val showSetupScreen: Boolean = false,
    val isConnecting: Boolean = false,
    val setupError: String? = null,

    // Tree & browsing
    val allNodes: List<RepoTreeNode> = emptyList(),
    val isLoadingTree: Boolean = false,
    val treeError: String? = null,
    val currentDirectory: String = "",
    val browserTab: BrowserTab = BrowserTab.FILES,
    val browserTabOrder: BrowserTabOrder = BrowserTabOrder.FILES_FIRST,
    val browserViewMode: BrowserViewMode = BrowserViewMode.FOLDER,
    val expandedDirectories: Set<String> = emptySet(),
    val recentFilter: RecentFilter = RecentFilter.ALL,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",

    // Recents & active note
    val recentFiles: List<RecentFileEntry> = emptyList(),
    val openNote: OpenNoteState? = null,

    // Dialogs & sheets
    val showRecentSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val createDialogType: CreateDialogType? = null,
    val isCreatingFolder: Boolean = false,
    val createError: String? = null,
    val pendingDiscardAction: PendingDiscardAction? = null,
    val snackbarMessage: String? = null,

    // App updates (GitHub Releases)
    val appUpdateConfig: AppUpdateConfig? = null,
    val isCheckingUpdate: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
    val updateDownloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    val showUpdateConfigDialog: Boolean = false,
    val updateConfigError: String? = null
) {
    val currentDirectoryEntries: List<RepoTreeNode>
        get() = allNodes.filter { node ->
            node.parentPath == currentDirectory &&
                (node.isDirectory || !node.name.equals(".gitkeep", ignoreCase = true))
        }

    val searchResults: List<RepoTreeNode>
        get() {
            val q = searchQuery.trim()
            if (q.isEmpty()) return emptyList()
            val lower = q.lowercase()
            return allNodes
                .asSequence()
                .filter { !it.isDirectory && !it.name.equals(".gitkeep", ignoreCase = true) }
                .filter { it.name.lowercase().contains(lower) || it.path.lowercase().contains(lower) }
                .sortedWith(
                    compareByDescending<RepoTreeNode> { it.name.lowercase().startsWith(lower) }
                        .thenByDescending { it.name.lowercase().contains(lower) }
                        .thenBy { it.path.lowercase() }
                )
                .toList()
        }
}

class GitNoteViewModel(application: Application) : AndroidViewModel(application) {

    private val configStore = ConfigStore(application.applicationContext)
    private val repository = GitHubNotesRepository()
    private val updateRepository = AppUpdateRepository()
    private var updateDownloadJob: Job? = null

    private val _uiState = MutableStateFlow(GitNoteUiState())
    val uiState: StateFlow<GitNoteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            configStore.themeModeFlow.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            configStore.browserViewModeFlow.collect { viewMode ->
                _uiState.update { it.copy(browserViewMode = viewMode) }
            }
        }
        viewModelScope.launch {
            configStore.browserTabOrderFlow.collect { tabOrder ->
                _uiState.update { it.copy(browserTabOrder = tabOrder) }
            }
        }
        viewModelScope.launch {
            configStore.autoOpenLastNoteFlow.collect { autoOpen ->
                _uiState.update { it.copy(autoOpenLastNote = autoOpen) }
            }
        }
        viewModelScope.launch {
            configStore.appUpdateConfigFlow.collect { updateCfg ->
                _uiState.update { it.copy(appUpdateConfig = updateCfg) }
            }
        }
        viewModelScope.launch {
            configStore.recentFilesFlow.collect { recents ->
                _uiState.update { it.copy(recentFiles = recents) }
            }
        }
        viewModelScope.launch {
            val initialConfig = configStore.getInitialRepoConfig()
            val initialRecents = configStore.getInitialRecentFiles()
            val initialTabOrder = configStore.getInitialBrowserTabOrder()
            val initialAutoOpenLast = configStore.getInitialAutoOpenLastNote()
            val initialUpdateConfig = configStore.getInitialAppUpdateConfig()
            val defaultTab = if (initialTabOrder == BrowserTabOrder.RECENT_FIRST) {
                BrowserTab.RECENT
            } else {
                BrowserTab.FILES
            }
            if (initialConfig == null) {
                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        showSetupScreen = true,
                        repoConfig = null,
                        browserTabOrder = initialTabOrder,
                        browserTab = defaultTab,
                        autoOpenLastNote = initialAutoOpenLast,
                        appUpdateConfig = initialUpdateConfig
                    )
                }
            } else {
                val lastPath = initialRecents.firstOrNull()?.path
                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        showSetupScreen = false,
                        repoConfig = initialConfig,
                        recentFiles = initialRecents,
                        browserTabOrder = initialTabOrder,
                        browserTab = defaultTab,
                        autoOpenLastNote = initialAutoOpenLast,
                        appUpdateConfig = initialUpdateConfig
                    )
                }
                // Load full repository tree in background
                loadRepositoryTree(initialConfig)
                // Auto-open last viewed/edited file only when enabled in settings
                if (initialAutoOpenLast && !lastPath.isNullOrBlank()) {
                    openFileInternal(path = lastPath, isAutoOpenOnLaunch = true)
                }
                // Silently check GitHub Releases if update source is configured
                if (initialUpdateConfig != null && initialUpdateConfig.autoCheckOnLaunch) {
                    checkForUpdates(manual = false)
                }
            }
        }
    }

    fun connectRepository(repoInput: String, tokenInput: String, branchInput: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, setupError = null) }
            val result = repository.verifyAndResolveConfig(repoInput, tokenInput, branchInput)
            result.fold(
                onSuccess = { config ->
                    configStore.saveRepoConfig(config)
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            setupError = null,
                            repoConfig = config,
                            showSetupScreen = false,
                            currentDirectory = "",
                            expandedDirectories = emptySet(),
                            browserTab = BrowserTab.FILES,
                            isSearchActive = false,
                            searchQuery = "",
                            openNote = null,
                            allNodes = emptyList(),
                            snackbarMessage = "已连接 ${config.fullName}（${config.branch}）"
                        )
                    }
                    loadRepositoryTree(config)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            setupError = error.message ?: "无法连接仓库"
                        )
                    }
                }
            )
        }
    }

    fun cancelSetupReconfigure() {
        if (_uiState.value.repoConfig != null) {
            _uiState.update { it.copy(showSetupScreen = false, setupError = null) }
        }
    }

    fun refreshTree() {
        val config = _uiState.value.repoConfig ?: return
        loadRepositoryTree(config)
    }

    private fun loadRepositoryTree(config: RepoConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTree = true, treeError = null) }
            val result = repository.fetchFullTree(config)
            result.fold(
                onSuccess = { nodes ->
                    _uiState.update {
                        it.copy(
                            allNodes = nodes,
                            isLoadingTree = false,
                            treeError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingTree = false,
                            treeError = error.message ?: "拉取目录失败"
                        )
                    }
                }
            )
        }
    }

    fun navigateToDirectory(path: String) {
        val clean = path.trim('/')
        _uiState.update {
            it.copy(
                currentDirectory = clean,
                expandedDirectories = it.expandedDirectories + ancestorDirectoriesOf(clean),
                isSearchActive = false,
                searchQuery = ""
            )
        }
    }

    fun navigateUpDirectory(): Boolean {
        val current = _uiState.value.currentDirectory
        if (current.isEmpty()) return false
        val parent = if ('/' in current) current.substringBeforeLast('/') else ""
        _uiState.update { it.copy(currentDirectory = parent) }
        return true
    }

    fun selectBrowserTab(tab: BrowserTab) {
        _uiState.update {
            it.copy(
                browserTab = tab,
                isSearchActive = false,
                searchQuery = ""
            )
        }
    }

    fun toggleBrowserViewMode() {
        val nextMode = if (_uiState.value.browserViewMode == BrowserViewMode.FOLDER) {
            BrowserViewMode.TREE
        } else {
            BrowserViewMode.FOLDER
        }
        _uiState.update { state ->
            val syncedExpanded = if (nextMode == BrowserViewMode.TREE) {
                val activeDirAncestors = ancestorDirectoriesOf(state.currentDirectory)
                val activeNoteAncestors = state.openNote?.parentDir
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { ancestorDirectoriesOf(it) }
                    .orEmpty()
                state.expandedDirectories + activeDirAncestors + activeNoteAncestors
            } else {
                state.expandedDirectories
            }
            state.copy(
                browserViewMode = nextMode,
                expandedDirectories = syncedExpanded
            )
        }
        viewModelScope.launch {
            configStore.setBrowserViewMode(nextMode)
        }
    }

    fun toggleExpandDirectory(path: String) {
        val clean = path.trim('/')
        if (clean.isEmpty()) return
        _uiState.update { state ->
            val isCurrentlyExpanded = clean in state.expandedDirectories
            val updatedExpanded = if (isCurrentlyExpanded) {
                // Collapse this directory and any expanded descendants for clean visual feedback
                state.expandedDirectories.filterNot { it == clean || it.startsWith("$clean/") }.toSet()
            } else {
                state.expandedDirectories + ancestorDirectoriesOf(clean)
            }
            val updatedCurrentDir = if (isCurrentlyExpanded) {
                if (state.currentDirectory == clean || state.currentDirectory.startsWith("$clean/")) {
                    if ('/' in clean) clean.substringBeforeLast('/') else ""
                } else {
                    state.currentDirectory
                }
            } else {
                clean
            }
            state.copy(
                expandedDirectories = updatedExpanded,
                currentDirectory = updatedCurrentDir
            )
        }
    }

    fun collapseAllDirectories() {
        _uiState.update {
            it.copy(
                expandedDirectories = emptySet(),
                currentDirectory = ""
            )
        }
    }

    fun expandTopLevelDirectories() {
        _uiState.update { state ->
            val topDirs = state.allNodes
                .asSequence()
                .filter { it.isDirectory && it.parentPath.isEmpty() }
                .map { it.path }
                .toSet()
            state.copy(expandedDirectories = topDirs)
        }
    }

    fun setRecentFilter(filter: RecentFilter) {
        _uiState.update { it.copy(recentFilter = filter) }
    }

    fun togglePinRecentFile(path: String) {
        viewModelScope.launch {
            configStore.togglePinRecentFile(path)
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (active) it.searchQuery else ""
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun requestOpenFile(path: String) {
        val currentNote = _uiState.value.openNote
        if (currentNote != null && currentNote.path == path) {
            _uiState.update { it.copy(showRecentSheet = false) }
            return
        }
        if (currentNote != null && currentNote.hasUnsavedChanges) {
            _uiState.update {
                it.copy(
                    showRecentSheet = false,
                    pendingDiscardAction = PendingDiscardAction.OpenOtherFile(path)
                )
            }
        } else {
            _uiState.update { it.copy(showRecentSheet = false) }
            openFileInternal(path, isAutoOpenOnLaunch = false)
        }
    }

    fun requestCloseNote() {
        val currentNote = _uiState.value.openNote ?: return
        if (currentNote.hasUnsavedChanges) {
            _uiState.update { it.copy(pendingDiscardAction = PendingDiscardAction.CloseNote) }
        } else {
            _uiState.update { it.copy(openNote = null, showRecentSheet = false) }
        }
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(pendingDiscardAction = null) }
    }

    fun confirmDiscardChanges() {
        val action = _uiState.value.pendingDiscardAction ?: return
        _uiState.update { it.copy(pendingDiscardAction = null) }
        executePostDiscardAction(action)
    }

    fun saveAndContinuePendingAction() {
        val action = _uiState.value.pendingDiscardAction ?: return
        _uiState.update { it.copy(pendingDiscardAction = null) }
        saveCurrentNote(onSuccessAfterSave = { executePostDiscardAction(action) })
    }

    private fun executePostDiscardAction(action: PendingDiscardAction) {
        when (action) {
            PendingDiscardAction.CloseNote -> {
                _uiState.update { it.copy(openNote = null) }
            }
            is PendingDiscardAction.OpenOtherFile -> {
                openFileInternal(action.path, isAutoOpenOnLaunch = false)
            }
            is PendingDiscardAction.CreateNewNote -> {
                startNewNoteDraft(action.path)
            }
        }
    }

    private fun openFileInternal(path: String, isAutoOpenOnLaunch: Boolean) {
        val config = _uiState.value.repoConfig ?: return
        val normalized = path.trim('/')
        val defaultEditMode = !(
            normalized.endsWith(".md", ignoreCase = true) ||
                normalized.endsWith(".markdown", ignoreCase = true) ||
                normalized.endsWith(".mdx", ignoreCase = true)
            )

        _uiState.update {
            it.copy(
                openNote = OpenNoteState(
                    path = normalized,
                    isEditMode = defaultEditMode,
                    isLoading = true,
                    isNewFile = false
                )
            )
        }

        viewModelScope.launch {
            val result = repository.fetchFileContent(config, normalized)
            result.fold(
                onSuccess = { doc ->
                    configStore.recordRecentFile(doc.path, wasEdited = false)
                    _uiState.update { state ->
                        if (state.openNote?.path != normalized) return@update state
                        state.copy(
                            openNote = state.openNote.copy(
                                sha = doc.sha,
                                originalContent = doc.content,
                                draftContent = doc.content,
                                isLoading = false,
                                errorMessage = null,
                                hasConflict = false
                            )
                        )
                    }
                },
                onFailure = { error ->
                    if (error is FileNotFoundOnRemoteException && isAutoOpenOnLaunch) {
                        configStore.removeRecentFile(normalized)
                        _uiState.update {
                            it.copy(
                                openNote = null,
                                snackbarMessage = "上次打开的文件已不存在，已返回目录"
                            )
                        }
                    } else {
                        if (error is FileNotFoundOnRemoteException) {
                            configStore.removeRecentFile(normalized)
                        }
                        _uiState.update { state ->
                            if (state.openNote?.path != normalized) return@update state
                            state.copy(
                                openNote = state.openNote.copy(
                                    isLoading = false,
                                    errorMessage = error.message ?: "读取文件失败"
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    fun retryOpenCurrentNote() {
        val path = _uiState.value.openNote?.path ?: return
        openFileInternal(path, isAutoOpenOnLaunch = false)
    }

    fun toggleEditMode() {
        _uiState.update { state ->
            val note = state.openNote ?: return@update state
            state.copy(openNote = note.copy(isEditMode = !note.isEditMode))
        }
    }

    fun updateDraftContent(newContent: String) {
        _uiState.update { state ->
            val note = state.openNote ?: return@update state
            state.copy(openNote = note.copy(draftContent = newContent))
        }
    }

    fun toggleTaskLineInDraft(lineIndex: Int) {
        _uiState.update { state ->
            val note = state.openNote ?: return@update state
            val lines = note.draftContent.replace("\r\n", "\n").split("\n").toMutableList()
            if (lineIndex !in lines.indices) return@update state
            val line = lines[lineIndex]
            val taskRegex = Regex("^([\\t ]*(?:[-*+]|\\d+[.)])\\s+\\[)([ xX])(].*)$")
            val match = taskRegex.matchEntire(line) ?: return@update state
            val nextMark = if (match.groupValues[2] == " ") "x" else " "
            lines[lineIndex] = "${match.groupValues[1]}$nextMark${match.groupValues[3]}"
            state.copy(openNote = note.copy(draftContent = lines.joinToString("\n")))
        }
    }

    fun showSnackbarMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun saveCurrentNote(onSuccessAfterSave: (() -> Unit)? = null) {
        val config = _uiState.value.repoConfig ?: return
        val note = _uiState.value.openNote ?: return
        if (note.isSaving || note.isLoading) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(openNote = state.openNote?.copy(isSaving = true, hasConflict = false))
            }
            val result = repository.saveFile(
                config = config,
                path = note.path,
                content = note.draftContent,
                existingSha = note.sha,
                isNewFile = note.isNewFile
            )
            handleSaveResult(config, note, result, onSuccessAfterSave)
        }
    }

    fun resolveConflictByReloading() {
        val path = _uiState.value.openNote?.path ?: return
        _uiState.update { state ->
            state.copy(openNote = state.openNote?.copy(hasConflict = false))
        }
        openFileInternal(path, isAutoOpenOnLaunch = false)
    }

    fun resolveConflictByOverwriting() {
        val config = _uiState.value.repoConfig ?: return
        val note = _uiState.value.openNote ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(openNote = state.openNote?.copy(isSaving = true, hasConflict = false))
            }
            val result = repository.overwriteFileWithLatestSha(config, note.path, note.draftContent)
            handleSaveResult(config, note, result, null)
        }
    }

    fun dismissConflictDialog() {
        _uiState.update { state ->
            state.copy(openNote = state.openNote?.copy(hasConflict = false))
        }
    }

    private suspend fun handleSaveResult(
        config: RepoConfig,
        note: OpenNoteState,
        result: SaveResult,
        onSuccessAfterSave: (() -> Unit)?
    ) {
        when (result) {
            is SaveResult.Success -> {
                configStore.recordRecentFile(note.path, wasEdited = true)
                _uiState.update { state ->
                    val updatedNodes = if (note.isNewFile) {
                        addFileNodeIfMissing(state.allNodes, note.path, result.newSha)
                    } else {
                        state.allNodes
                    }
                    state.copy(
                        allNodes = updatedNodes,
                        openNote = state.openNote?.copy(
                            sha = result.newSha,
                            originalContent = note.draftContent,
                            isNewFile = false,
                            isSaving = false,
                            hasConflict = false
                        ),
                        snackbarMessage = "已保存并提交到 GitHub"
                    )
                }
                if (note.isNewFile) {
                    loadRepositoryTree(config)
                }
                onSuccessAfterSave?.invoke()
            }
            SaveResult.Conflict -> {
                _uiState.update { state ->
                    state.copy(
                        openNote = state.openNote?.copy(
                            isSaving = false,
                            hasConflict = true
                        )
                    )
                }
            }
            is SaveResult.Error -> {
                _uiState.update { state ->
                    state.copy(
                        openNote = state.openNote?.copy(isSaving = false),
                        snackbarMessage = result.message
                    )
                }
            }
        }
    }

    fun openCreateDialog(type: CreateDialogType) {
        _uiState.update { it.copy(createDialogType = type, createError = null) }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(createDialogType = null, createError = null) }
    }

    fun confirmCreateNote(rawName: String) {
        val trimmed = rawName.trim()
        val validationError = validateEntryName(trimmed)
        if (validationError != null) {
            _uiState.update { it.copy(createError = validationError) }
            return
        }
        val finalName = if ('.' in trimmed) trimmed else "$trimmed.md"
        val dir = _uiState.value.currentDirectory
        val fullPath = if (dir.isEmpty()) finalName else "$dir/$finalName"

        val exists = _uiState.value.allNodes.any { !it.isDirectory && it.path.equals(fullPath, ignoreCase = true) }
        if (exists) {
            _uiState.update { it.copy(createError = "当前目录下已存在同名文件") }
            return
        }

        _uiState.update { it.copy(createDialogType = null, createError = null) }

        val currentNote = _uiState.value.openNote
        if (currentNote != null && currentNote.hasUnsavedChanges) {
            _uiState.update {
                it.copy(pendingDiscardAction = PendingDiscardAction.CreateNewNote(fullPath))
            }
        } else {
            startNewNoteDraft(fullPath)
        }
    }

    private fun startNewNoteDraft(fullPath: String) {
        val baseTitle = fullPath.substringAfterLast('/').substringBeforeLast('.')
        val initialText = if (fullPath.endsWith(".md", ignoreCase = true)) "# $baseTitle\n\n" else ""
        _uiState.update {
            it.copy(
                openNote = OpenNoteState(
                    path = fullPath,
                    sha = null,
                    originalContent = "",
                    draftContent = initialText,
                    isEditMode = true,
                    isLoading = false,
                    isNewFile = true
                )
            )
        }
    }

    fun confirmCreateFolder(rawName: String) {
        val config = _uiState.value.repoConfig ?: return
        val trimmed = rawName.trim()
        val validationError = validateEntryName(trimmed)
        if (validationError != null) {
            _uiState.update { it.copy(createError = validationError) }
            return
        }
        val dir = _uiState.value.currentDirectory
        val fullFolderPath = if (dir.isEmpty()) trimmed else "$dir/$trimmed"

        val exists = _uiState.value.allNodes.any { it.isDirectory && it.path.equals(fullFolderPath, ignoreCase = true) }
        if (exists) {
            _uiState.update { it.copy(createError = "当前目录下已存在同名文件夹") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingFolder = true, createError = null) }
            when (val result = repository.createFolder(config, fullFolderPath)) {
                is SaveResult.Success -> {
                    _uiState.update { state ->
                        val updatedNodes = addDirectoryNodeIfMissing(state.allNodes, fullFolderPath)
                        state.copy(
                            allNodes = updatedNodes,
                            currentDirectory = fullFolderPath,
                            isCreatingFolder = false,
                            createDialogType = null,
                            createError = null,
                            snackbarMessage = "已创建文件夹 $trimmed"
                        )
                    }
                    loadRepositoryTree(config)
                }
                is SaveResult.Error -> {
                    _uiState.update {
                        it.copy(isCreatingFolder = false, createError = result.message)
                    }
                }
                SaveResult.Conflict -> {
                    _uiState.update {
                        it.copy(isCreatingFolder = false, createError = "文件夹已存在或发生冲突")
                    }
                }
            }
        }
    }

    fun setShowRecentSheet(show: Boolean) {
        _uiState.update { it.copy(showRecentSheet = show) }
    }

    fun setShowSettingsSheet(show: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = show) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            configStore.setThemeMode(mode)
        }
    }

    fun setBrowserTabOrder(order: BrowserTabOrder) {
        viewModelScope.launch {
            configStore.setBrowserTabOrder(order)
        }
    }

    fun setAutoOpenLastNote(enabled: Boolean) {
        viewModelScope.launch {
            configStore.setAutoOpenLastNote(enabled)
        }
    }

    fun openUpdateConfigDialog() {
        _uiState.update {
            it.copy(
                showUpdateConfigDialog = true,
                updateConfigError = null
            )
        }
    }

    fun dismissUpdateConfigDialog() {
        _uiState.update {
            it.copy(
                showUpdateConfigDialog = false,
                updateConfigError = null
            )
        }
    }

    fun saveUpdateConfig(repoInput: String, tokenInput: String, autoCheck: Boolean) {
        val parsed = updateRepository.parseRepoInput(repoInput)
        if (parsed == null) {
            _uiState.update { it.copy(updateConfigError = "仓库格式请填写 owner/repo，例如 yourname/gitnote") }
            return
        }
        val config = AppUpdateConfig(
            owner = parsed.first,
            repo = parsed.second,
            token = tokenInput.trim(),
            autoCheckOnLaunch = autoCheck
        )
        viewModelScope.launch {
            configStore.saveAppUpdateConfig(config)
            _uiState.update {
                it.copy(
                    appUpdateConfig = config,
                    showUpdateConfigDialog = false,
                    updateConfigError = null,
                    snackbarMessage = "已保存更新源 ${config.fullName}"
                )
            }
        }
    }

    fun clearUpdateConfig() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        viewModelScope.launch {
            configStore.clearAppUpdateConfig()
            _uiState.update {
                it.copy(
                    appUpdateConfig = null,
                    availableUpdate = null,
                    updateDownloadState = UpdateDownloadState.Idle,
                    showUpdateConfigDialog = false,
                    updateConfigError = null,
                    snackbarMessage = "已清除更新源配置"
                )
            }
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        val config = _uiState.value.appUpdateConfig
        if (config == null) {
            if (manual) {
                openUpdateConfigDialog()
            }
            return
        }
        if (_uiState.value.isCheckingUpdate) return

        val fallbackToken = _uiState.value.repoConfig?.token.orEmpty()
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true) }
            val result = updateRepository.checkLatestRelease(config, fallbackToken)
            result.fold(
                onSuccess = { info ->
                    if (info.isNewer || manual) {
                        _uiState.update {
                            it.copy(
                                isCheckingUpdate = false,
                                showSettingsSheet = if (info.isNewer) false else it.showSettingsSheet,
                                availableUpdate = if (info.isNewer) info else null,
                                updateDownloadState = if (info.isNewer) UpdateDownloadState.Idle else it.updateDownloadState,
                                snackbarMessage = if (!info.isNewer && manual) {
                                    "当前已是最新版本（远端最新：${info.tagName}）"
                                } else {
                                    null
                                }
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isCheckingUpdate = false) }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            snackbarMessage = if (manual) (error.message ?: "检查更新失败") else null
                        )
                    }
                }
            )
        }
    }

    fun startUpdateDownload() {
        val state = _uiState.value
        val config = state.appUpdateConfig ?: return
        val updateInfo = state.availableUpdate ?: return
        if (state.updateDownloadState is UpdateDownloadState.Downloading) return

        val fallbackToken = state.repoConfig?.token.orEmpty()
        val cacheDir = getApplication<Application>().cacheDir

        updateDownloadJob?.cancel()
        updateDownloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateDownloadState = UpdateDownloadState.Downloading(
                        downloadedBytes = 0L,
                        totalBytes = updateInfo.apkSizeBytes
                    )
                )
            }
            val result = updateRepository.downloadApkAsset(
                cacheDir = cacheDir,
                config = config,
                fallbackToken = fallbackToken,
                updateInfo = updateInfo,
                onProgress = { downloaded, total ->
                    _uiState.update {
                        it.copy(
                            updateDownloadState = UpdateDownloadState.Downloading(
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        )
                    }
                }
            )
            result.fold(
                onSuccess = { apkFile ->
                    _uiState.update {
                        it.copy(
                            updateDownloadState = UpdateDownloadState.ReadyToInstall(
                                apkFilePath = apkFile.absolutePath,
                                fileSizeBytes = apkFile.length()
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            updateDownloadState = UpdateDownloadState.Error(
                                message = error.message ?: "下载安装包失败"
                            )
                        )
                    }
                }
            )
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _uiState.update {
            it.copy(updateDownloadState = UpdateDownloadState.Idle)
        }
    }

    fun reportUpdateInstallError(message: String) {
        _uiState.update {
            it.copy(snackbarMessage = message)
        }
    }

    fun dismissUpdateAvailableDialog() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _uiState.update {
            it.copy(
                availableUpdate = null,
                updateDownloadState = UpdateDownloadState.Idle
            )
        }
    }

    fun openReconfigureSetup() {
        _uiState.update {
            it.copy(
                showSettingsSheet = false,
                showSetupScreen = true,
                setupError = null
            )
        }
    }

    fun disconnectRepository() {
        viewModelScope.launch {
            configStore.clearRepoConfig()
            _uiState.update {
                GitNoteUiState(
                    isInitializing = false,
                    themeMode = it.themeMode,
                    autoOpenLastNote = it.autoOpenLastNote,
                    browserTabOrder = it.browserTabOrder,
                    browserViewMode = it.browserViewMode,
                    appUpdateConfig = it.appUpdateConfig,
                    showSetupScreen = true
                )
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun validateEntryName(name: String): String? {
        if (name.isBlank()) return "名称不能为空"
        if (name == "." || name == "..") return "名称不合法"
        if (name.contains('/') || name.contains('\\')) return "名称不能包含斜杠 / 或 \\"
        return null
    }

    private fun addFileNodeIfMissing(nodes: List<RepoTreeNode>, path: String, sha: String): List<RepoTreeNode> {
        if (nodes.any { it.path == path }) return nodes
        val name = path.substringAfterLast('/')
        val parent = if ('/' in path) path.substringBeforeLast('/') else ""
        val newNode = RepoTreeNode(name = name, path = path, parentPath = parent, isDirectory = false, sha = sha)
        return (nodes + newNode).sortedWith(
            compareByDescending<RepoTreeNode> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    private fun addDirectoryNodeIfMissing(nodes: List<RepoTreeNode>, path: String): List<RepoTreeNode> {
        if (nodes.any { it.path == path }) return nodes
        val name = path.substringAfterLast('/')
        val parent = if ('/' in path) path.substringBeforeLast('/') else ""
        val newNode = RepoTreeNode(name = name, path = path, parentPath = parent, isDirectory = true)
        return (nodes + newNode).sortedWith(
            compareByDescending<RepoTreeNode> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }
}
