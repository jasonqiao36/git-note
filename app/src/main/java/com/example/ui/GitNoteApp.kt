package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.ConflictResolutionDialog
import com.example.ui.components.CreateEntryDialog
import com.example.ui.components.RecentFilesBottomSheet
import com.example.ui.components.SettingsBottomSheet
import com.example.ui.components.UnsavedChangesDialog
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.NoteScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.viewmodel.CreateDialogType
import com.example.ui.viewmodel.GitNoteUiState
import com.example.ui.viewmodel.GitNoteViewModel

@Composable
fun GitNoteApp(
    viewModel: GitNoteViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isInitializing -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.showSetupScreen || uiState.repoConfig == null -> {
                SetupScreen(
                    existingConfig = uiState.repoConfig,
                    isConnecting = uiState.isConnecting,
                    errorMessage = uiState.setupError,
                    onConnect = viewModel::connectRepository,
                    onCancel = if (uiState.repoConfig != null) viewModel::cancelSetupReconfigure else null
                )
            }
            else -> {
                MainWorkspace(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }

        // Global Snackbar positioned safely above system navigation bar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    // --- Modal Bottom Sheets & Dialogs ---

    if (uiState.showRecentSheet) {
        RecentFilesBottomSheet(
            recentFiles = uiState.recentFiles,
            currentOpenPath = uiState.openNote?.path,
            showBrowseAllButton = uiState.openNote != null,
            onSelectFile = { path -> viewModel.requestOpenFile(path) },
            onBrowseAllFiles = {
                viewModel.setShowRecentSheet(false)
                viewModel.requestCloseNote()
            },
            onDismiss = { viewModel.setShowRecentSheet(false) }
        )
    }

    val repoConfig = uiState.repoConfig
    if (uiState.showSettingsSheet && repoConfig != null) {
        SettingsBottomSheet(
            repoConfig = repoConfig,
            themeMode = uiState.themeMode,
            browserTabOrder = uiState.browserTabOrder,
            onSelectTheme = viewModel::setThemeMode,
            onSelectBrowserTabOrder = viewModel::setBrowserTabOrder,
            onReconfigure = viewModel::openReconfigureSetup,
            onDisconnect = viewModel::disconnectRepository,
            onDismiss = { viewModel.setShowSettingsSheet(false) }
        )
    }

    val createType = uiState.createDialogType
    if (createType != null) {
        CreateEntryDialog(
            type = createType,
            currentDirectory = uiState.currentDirectory,
            isLoading = uiState.isCreatingFolder,
            errorMessage = uiState.createError,
            onConfirm = { name ->
                when (createType) {
                    CreateDialogType.NOTE -> viewModel.confirmCreateNote(name)
                    CreateDialogType.FOLDER -> viewModel.confirmCreateFolder(name)
                }
            },
            onDismiss = viewModel::dismissCreateDialog
        )
    }

    if (uiState.pendingDiscardAction != null) {
        UnsavedChangesDialog(
            onSaveFirst = viewModel::saveAndContinuePendingAction,
            onDiscard = viewModel::confirmDiscardChanges,
            onCancel = viewModel::dismissDiscardDialog
        )
    }

    if (uiState.openNote?.hasConflict == true) {
        ConflictResolutionDialog(
            onReloadRemote = viewModel::resolveConflictByReloading,
            onOverwriteRemote = viewModel::resolveConflictByOverwriting,
            onCancel = viewModel::dismissConflictDialog
        )
    }
}

@Composable
private fun MainWorkspace(
    uiState: GitNoteUiState,
    viewModel: GitNoteViewModel
) {
    val repoConfig = uiState.repoConfig ?: return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 720.dp

        if (isExpanded) {
            // Canonical Adaptive List-Detail Layout for Tablets & Foldables
            Row(modifier = Modifier.fillMaxSize()) {
                BrowserScreen(
                    repoConfig = repoConfig,
                    allNodes = uiState.allNodes,
                    currentDirectory = uiState.currentDirectory,
                    entries = uiState.currentDirectoryEntries,
                    browserTab = uiState.browserTab,
                    browserTabOrder = uiState.browserTabOrder,
                    browserViewMode = uiState.browserViewMode,
                    expandedDirectories = uiState.expandedDirectories,
                    recentFiles = uiState.recentFiles,
                    recentFilter = uiState.recentFilter,
                    activeNotePath = uiState.openNote?.path,
                    isLoadingTree = uiState.isLoadingTree,
                    treeError = uiState.treeError,
                    isSearchActive = uiState.isSearchActive,
                    searchQuery = uiState.searchQuery,
                    searchResults = uiState.searchResults,
                    onSelectBrowserTab = viewModel::selectBrowserTab,
                    onToggleBrowserViewMode = viewModel::toggleBrowserViewMode,
                    onToggleExpandDirectory = viewModel::toggleExpandDirectory,
                    onCollapseAllDirectories = viewModel::collapseAllDirectories,
                    onExpandTopLevelDirectories = viewModel::expandTopLevelDirectories,
                    onRecentFilterChange = viewModel::setRecentFilter,
                    onTogglePinRecent = viewModel::togglePinRecentFile,
                    onNavigateDirectory = viewModel::navigateToDirectory,
                    onNavigateUp = viewModel::navigateUpDirectory,
                    onOpenFile = viewModel::requestOpenFile,
                    onRefresh = viewModel::refreshTree,
                    onSetSearchActive = viewModel::setSearchActive,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onOpenCreateDialog = viewModel::openCreateDialog,
                    onOpenSettings = { viewModel.setShowSettingsSheet(true) },
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                )
                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                val openNote = uiState.openNote
                if (openNote != null) {
                    NoteScreen(
                        note = openNote,
                        showBackButton = false,
                        hasRecentFiles = uiState.recentFiles.isNotEmpty(),
                        onBack = viewModel::requestCloseNote,
                        onToggleEditMode = viewModel::toggleEditMode,
                        onDraftChange = viewModel::updateDraftContent,
                        onToggleTaskLine = viewModel::toggleTaskLineInDraft,
                        onSave = { viewModel.saveCurrentNote() },
                        onRetry = viewModel::retryOpenCurrentNote,
                        onOpenRecentSwitcher = { viewModel.setShowRecentSheet(true) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                } else {
                    EmptyDetailPane(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            // Compact Handheld Single-Pane with smooth transition
            val hasOpenNote = uiState.openNote != null
            AnimatedContent(
                targetState = hasOpenNote,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "workspace_pane_transition",
                modifier = Modifier.fillMaxSize()
            ) { showingNote ->
                val openNote = uiState.openNote
                if (showingNote && openNote != null) {
                    NoteScreen(
                        note = openNote,
                        showBackButton = true,
                        hasRecentFiles = uiState.recentFiles.isNotEmpty(),
                        onBack = viewModel::requestCloseNote,
                        onToggleEditMode = viewModel::toggleEditMode,
                        onDraftChange = viewModel::updateDraftContent,
                        onToggleTaskLine = viewModel::toggleTaskLineInDraft,
                        onSave = { viewModel.saveCurrentNote() },
                        onRetry = viewModel::retryOpenCurrentNote,
                        onOpenRecentSwitcher = { viewModel.setShowRecentSheet(true) }
                    )
                } else {
                    BrowserScreen(
                        repoConfig = repoConfig,
                        allNodes = uiState.allNodes,
                        currentDirectory = uiState.currentDirectory,
                        entries = uiState.currentDirectoryEntries,
                        browserTab = uiState.browserTab,
                        browserTabOrder = uiState.browserTabOrder,
                        browserViewMode = uiState.browserViewMode,
                        expandedDirectories = uiState.expandedDirectories,
                        recentFiles = uiState.recentFiles,
                        recentFilter = uiState.recentFilter,
                        activeNotePath = null,
                        isLoadingTree = uiState.isLoadingTree,
                        treeError = uiState.treeError,
                        isSearchActive = uiState.isSearchActive,
                        searchQuery = uiState.searchQuery,
                        searchResults = uiState.searchResults,
                        onSelectBrowserTab = viewModel::selectBrowserTab,
                        onToggleBrowserViewMode = viewModel::toggleBrowserViewMode,
                        onToggleExpandDirectory = viewModel::toggleExpandDirectory,
                        onCollapseAllDirectories = viewModel::collapseAllDirectories,
                        onExpandTopLevelDirectories = viewModel::expandTopLevelDirectories,
                        onRecentFilterChange = viewModel::setRecentFilter,
                        onTogglePinRecent = viewModel::togglePinRecentFile,
                        onNavigateDirectory = viewModel::navigateToDirectory,
                        onNavigateUp = viewModel::navigateUpDirectory,
                        onOpenFile = viewModel::requestOpenFile,
                        onRefresh = viewModel::refreshTree,
                        onSetSearchActive = viewModel::setSearchActive,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onOpenCreateDialog = viewModel::openCreateDialog,
                        onOpenSettings = { viewModel.setShowSettingsSheet(true) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDetailPane(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = stringResource(R.string.note_select_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
