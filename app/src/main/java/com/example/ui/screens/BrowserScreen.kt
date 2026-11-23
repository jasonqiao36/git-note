package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BrowserTab
import com.example.data.model.BrowserTabOrder
import com.example.data.model.BrowserViewMode
import com.example.data.model.RecentFileEntry
import com.example.data.model.RecentFilter
import com.example.data.model.RepoConfig
import com.example.data.model.RepoTreeNode
import com.example.ui.viewmodel.CreateDialogType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    repoConfig: RepoConfig,
    allNodes: List<RepoTreeNode>,
    currentDirectory: String,
    entries: List<RepoTreeNode>,
    browserTab: BrowserTab,
    browserTabOrder: BrowserTabOrder,
    browserViewMode: BrowserViewMode,
    expandedDirectories: Set<String>,
    recentFiles: List<RecentFileEntry>,
    recentFilter: RecentFilter,
    activeNotePath: String?,
    isLoadingTree: Boolean,
    treeError: String?,
    isSearchActive: Boolean,
    searchQuery: String,
    searchResults: List<RepoTreeNode>,
    onSelectBrowserTab: (BrowserTab) -> Unit,
    onToggleBrowserViewMode: () -> Unit,
    onToggleExpandDirectory: (String) -> Unit,
    onCollapseAllDirectories: () -> Unit,
    onExpandTopLevelDirectories: () -> Unit,
    onRecentFilterChange: (RecentFilter) -> Unit,
    onTogglePinRecent: (String) -> Unit,
    onNavigateDirectory: (String) -> Unit,
    onNavigateUp: () -> Boolean,
    onOpenFile: (String) -> Unit,
    onRefresh: () -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenCreateDialog: (CreateDialogType) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var showFabMenu by remember { mutableStateOf(false) }

    val primaryTab = if (browserTabOrder == BrowserTabOrder.RECENT_FIRST) {
        BrowserTab.RECENT
    } else {
        BrowserTab.FILES
    }
    val canNavigateUpFolder = browserTab == BrowserTab.FILES &&
        browserViewMode == BrowserViewMode.FOLDER &&
        currentDirectory.isNotEmpty()

    BackHandler(enabled = isSearchActive || canNavigateUpFolder || browserTab != primaryTab) {
        when {
            isSearchActive -> onSetSearchActive(false)
            canNavigateUpFolder -> onNavigateUp()
            browserTab != primaryTab -> onSelectBrowserTab(primaryTab)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.background,
        topBar = {
            if (isSearchActive) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = { onSetSearchActive(false) }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.background,
                        titleContentColor = colorScheme.onBackground
                    ),
                    navigationIcon = {
                        if (canNavigateUpFolder) {
                            IconButton(
                                onClick = { onNavigateUp() },
                                modifier = Modifier.testTag("browser_up_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "返回上级目录"
                                )
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = if (canNavigateUpFolder) {
                                    currentDirectory.substringAfterLast('/')
                                } else {
                                    repoConfig.repo
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${repoConfig.owner} · ${repoConfig.branch}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (browserTab == BrowserTab.FILES) {
                            IconButton(
                                onClick = onToggleBrowserViewMode,
                                modifier = Modifier.testTag("browser_view_mode_toggle")
                            ) {
                                val isTree = browserViewMode == BrowserViewMode.TREE
                                Icon(
                                    imageVector = if (isTree) Icons.Outlined.ViewAgenda else Icons.Outlined.AccountTree,
                                    contentDescription = stringResource(
                                        if (isTree) R.string.browser_mode_switch_to_folder
                                        else R.string.browser_mode_switch_to_tree
                                    ),
                                    tint = if (isTree) colorScheme.primary else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { onSetSearchActive(true) },
                            modifier = Modifier.testTag("browser_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.browser_search)
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("browser_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.browser_refresh)
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("browser_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.browser_settings)
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSearchActive && browserTab == BrowserTab.FILES) {
                Box {
                    FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.testTag("browser_create_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "新建笔记或文件夹"
                        )
                    }
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false },
                        containerColor = colorScheme.surfaceContainerLowest
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_new_note)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.NoteAdd,
                                    contentDescription = null,
                                    tint = colorScheme.primary
                                )
                            },
                            onClick = {
                                showFabMenu = false
                                onOpenCreateDialog(CreateDialogType.NOTE)
                            },
                            modifier = Modifier.testTag("create_note_menu_item")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_new_folder)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = null,
                                    tint = colorScheme.secondary
                                )
                            },
                            onClick = {
                                showFabMenu = false
                                onOpenCreateDialog(CreateDialogType.FOLDER)
                            },
                            modifier = Modifier.testTag("create_folder_menu_item")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top segmented switcher: 「目录」 / 「最近」 (order configurable in Settings)
            AnimatedVisibility(visible = !isSearchActive) {
                BrowserTopSegmentedBar(
                    selectedTab = browserTab,
                    tabOrder = browserTabOrder,
                    viewMode = browserViewMode,
                    recentCount = recentFiles.size,
                    onSelectTab = onSelectBrowserTab
                )
            }

            // Breadcrumbs only in single-level folder mode when inside a subdirectory
            AnimatedVisibility(visible = !isSearchActive && canNavigateUpFolder) {
                BreadcrumbBar(
                    currentDirectory = currentDirectory,
                    onNavigateDirectory = onNavigateDirectory
                )
            }

            if (isSearchActive) {
                SearchResultsList(
                    query = searchQuery,
                    results = searchResults,
                    activeNotePath = activeNotePath,
                    onOpenFile = onOpenFile
                )
            } else {
                AnimatedContent(
                    targetState = browserTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "browser_tab_transition",
                    modifier = Modifier.fillMaxSize()
                ) { activeTab ->
                    when (activeTab) {
                        BrowserTab.FILES -> {
                            PullToRefreshBox(
                                isRefreshing = isLoadingTree && allNodes.isNotEmpty(),
                                onRefresh = onRefresh,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                when {
                                    isLoadingTree && allNodes.isEmpty() -> {
                                        LoadingSkeletonList()
                                    }
                                    treeError != null && allNodes.isEmpty() -> {
                                        ErrorStateView(message = treeError, onRetry = onRefresh)
                                    }
                                    browserViewMode == BrowserViewMode.TREE -> {
                                        BrowserTreeView(
                                            allNodes = allNodes,
                                            expandedDirectories = expandedDirectories,
                                            currentDirectory = currentDirectory,
                                            activeNotePath = activeNotePath,
                                            onToggleDirectory = onToggleExpandDirectory,
                                            onCollapseAll = onCollapseAllDirectories,
                                            onExpandTopLevel = onExpandTopLevelDirectories,
                                            onOpenFile = onOpenFile,
                                            onCreateNote = { onOpenCreateDialog(CreateDialogType.NOTE) }
                                        )
                                    }
                                    else -> {
                                        FolderEntriesList(
                                            entries = entries,
                                            activeNotePath = activeNotePath,
                                            onNavigateDirectory = onNavigateDirectory,
                                            onOpenFile = onOpenFile,
                                            onCreateNote = { onOpenCreateDialog(CreateDialogType.NOTE) }
                                        )
                                    }
                                }
                            }
                        }

                        BrowserTab.RECENT -> {
                            RecentTabView(
                                recentFiles = recentFiles,
                                filter = recentFilter,
                                activeNotePath = activeNotePath,
                                onFilterChange = onRecentFilterChange,
                                onOpenFile = onOpenFile,
                                onTogglePin = onTogglePinRecent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserTopSegmentedBar(
    selectedTab: BrowserTab,
    tabOrder: BrowserTabOrder,
    viewMode: BrowserViewMode,
    recentCount: Int,
    onSelectTab: (BrowserTab) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val filesLabel = stringResource(R.string.browser_tab_files)
    val recentLabel = if (recentCount > 0) {
        "${stringResource(R.string.browser_tab_recent)} ($recentCount)"
    } else {
        stringResource(R.string.browser_tab_recent)
    }
    val orderedTabs = if (tabOrder == BrowserTabOrder.RECENT_FIRST) {
        listOf(BrowserTab.RECENT, BrowserTab.FILES)
    } else {
        listOf(BrowserTab.FILES, BrowserTab.RECENT)
    }

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("browser_top_segmented_bar")
    ) {
        orderedTabs.forEachIndexed { index, tab ->
            val isFiles = tab == BrowserTab.FILES
            SegmentedButton(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = orderedTabs.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colorScheme.primaryContainer,
                    activeContentColor = colorScheme.onPrimaryContainer,
                    inactiveContainerColor = colorScheme.surfaceContainerLowest,
                    inactiveContentColor = colorScheme.onSurfaceVariant,
                    activeBorderColor = colorScheme.primary.copy(alpha = 0.55f),
                    inactiveBorderColor = colorScheme.outlineVariant
                ),
                icon = {
                    Icon(
                        imageVector = when {
                            !isFiles -> Icons.Outlined.History
                            viewMode == BrowserViewMode.TREE -> Icons.Outlined.AccountTree
                            else -> Icons.Outlined.Folder
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag(if (isFiles) "browser_tab_files" else "browser_tab_recent")
            ) {
                Text(
                    text = if (isFiles) filesLabel else recentLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FolderEntriesList(
    entries: List<RepoTreeNode>,
    activeNotePath: String?,
    onNavigateDirectory: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onCreateNote: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("browser_folder_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (entries.isEmpty()) {
            item(key = "empty_folder") {
                EmptyFolderView(onCreateNote = onCreateNote)
            }
        } else {
            items(entries, key = { it.path }) { node ->
                TreeNodeRow(
                    node = node,
                    isSelected = !node.isDirectory && node.path == activeNotePath,
                    showRelativePath = false,
                    onClick = {
                        if (node.isDirectory) {
                            onNavigateDirectory(node.path)
                        } else {
                            onOpenFile(node.path)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background),
        navigationIcon = {
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "关闭搜索"
                )
            }
        },
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.browser_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                    cursorBrush = SolidColor(colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("browser_search_input")
                )
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("browser_clear_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.browser_clear_search)
                    )
                }
            }
        }
    )
}

@Composable
private fun BreadcrumbBar(
    currentDirectory: String,
    onNavigateDirectory: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val segments = remember(currentDirectory) {
        currentDirectory.split('/').filter { it.isNotEmpty() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.browser_root),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onNavigateDirectory("") }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        var accPath = ""
        segments.forEachIndexed { index, seg ->
            accPath = if (accPath.isEmpty()) seg else "$accPath/$seg"
            val targetPath = accPath
            val isLast = index == segments.lastIndex
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = seg,
                style = MaterialTheme.typography.labelMedium,
                color = if (isLast) colorScheme.onSurface else colorScheme.primary,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isLast) { onNavigateDirectory(targetPath) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TreeNodeRow(
    node: RepoTreeNode,
    isSelected: Boolean,
    showRelativePath: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("tree_item_${node.path}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (node.isDirectory) colorScheme.primaryContainer.copy(alpha = 0.65f)
                        else colorScheme.surfaceContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (node.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                    contentDescription = if (node.isDirectory) "文件夹" else "文件",
                    tint = if (node.isDirectory) colorScheme.primary else colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showRelativePath && node.parentPath.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = node.parentPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (node.isDirectory) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    query: String,
    results: List<RepoTreeNode>,
    activeNotePath: String?,
    onOpenFile: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    if (query.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "输入关键词，瞬间检索整个仓库的笔记",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.browser_empty_search),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results, key = { "search_${it.path}" }) { node ->
            TreeNodeRow(
                node = node,
                isSelected = node.path == activeNotePath,
                showRelativePath = true,
                onClick = { onOpenFile(node.path) }
            )
        }
    }
}

@Composable
internal fun EmptyFolderView(onCreateNote: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = stringResource(R.string.browser_empty_folder),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onCreateNote,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("empty_folder_create_note_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.NoteAdd,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.browser_new_note))
        }
    }
}

@Composable
private fun LoadingSkeletonList() {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(7) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorScheme.surfaceContainer)
            )
        }
    }
}

@Composable
private fun ErrorStateView(message: String, onRetry: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.error
            )
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("browser_retry_button")
            ) {
                Text(stringResource(R.string.browser_retry))
            }
        }
    }
}
