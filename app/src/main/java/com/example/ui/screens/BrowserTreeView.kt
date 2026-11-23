package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.RepoTreeNode

data class FlatTreeRow(
    val node: RepoTreeNode,
    val depth: Int,
    val isExpanded: Boolean,
    val descendantFileCount: Int
)

internal fun ancestorDirectoriesOf(path: String): Set<String> {
    val clean = path.trim('/')
    if (clean.isEmpty()) return emptySet()
    val parts = clean.split('/').filter { it.isNotEmpty() }
    val result = LinkedHashSet<String>(parts.size)
    var acc = ""
    for (part in parts) {
        acc = if (acc.isEmpty()) part else "$acc/$part"
        result.add(acc)
    }
    return result
}

internal fun buildVisibleTreeRows(
    allNodes: List<RepoTreeNode>,
    expandedDirectories: Set<String>
): List<FlatTreeRow> {
    if (allNodes.isEmpty()) return emptyList()

    val visibleNodes = allNodes.filter { node ->
        node.isDirectory || !node.name.equals(".gitkeep", ignoreCase = true)
    }

    val childrenByParent = visibleNodes
        .groupBy { it.parentPath }
        .mapValues { (_, list) ->
            list.sortedWith(
                compareByDescending<RepoTreeNode> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        }

    // Count total files under every directory so users immediately see folder weight
    val fileCountByDir = HashMap<String, Int>()
    for (node in visibleNodes) {
        if (!node.isDirectory && node.parentPath.isNotEmpty()) {
            for (ancestor in ancestorDirectoriesOf(node.parentPath)) {
                fileCountByDir[ancestor] = (fileCountByDir[ancestor] ?: 0) + 1
            }
        }
    }

    val result = ArrayList<FlatTreeRow>(visibleNodes.size)

    fun visit(parentPath: String, depth: Int) {
        val children = childrenByParent[parentPath].orEmpty()
        for (child in children) {
            if (child.isDirectory) {
                val expanded = child.path in expandedDirectories
                result.add(
                    FlatTreeRow(
                        node = child,
                        depth = depth,
                        isExpanded = expanded,
                        descendantFileCount = fileCountByDir[child.path] ?: 0
                    )
                )
                if (expanded) {
                    visit(child.path, depth + 1)
                }
            } else {
                result.add(
                    FlatTreeRow(
                        node = child,
                        depth = depth,
                        isExpanded = false,
                        descendantFileCount = 0
                    )
                )
            }
        }
    }

    visit("", 0)
    return result
}

@Composable
internal fun BrowserTreeView(
    allNodes: List<RepoTreeNode>,
    expandedDirectories: Set<String>,
    currentDirectory: String,
    activeNotePath: String?,
    onToggleDirectory: (String) -> Unit,
    onCollapseAll: () -> Unit,
    onExpandTopLevel: () -> Unit,
    onOpenFile: (String) -> Unit,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val rows = remember(allNodes, expandedDirectories) {
        buildVisibleTreeRows(allNodes, expandedDirectories)
    }
    val totalFiles = remember(allNodes) {
        allNodes.count { !it.isDirectory && !it.name.equals(".gitkeep", ignoreCase = true) }
    }
    val totalDirs = remember(allNodes) {
        allNodes.count { it.isDirectory }
    }

    if (rows.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            EmptyFolderView(onCreateNote = onCreateNote)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("browser_tree_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "tree_toolbar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp, start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${totalDirs} 个目录 · ${totalFiles} 篇文件",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (totalDirs > 0) {
                    val hasExpanded = expandedDirectories.isNotEmpty()
                    Surface(
                        color = colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (hasExpanded) onCollapseAll() else onExpandTopLevel()
                            }
                            .testTag("browser_tree_toggle_all_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (hasExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (hasExpanded) R.string.browser_tree_collapse_all
                                    else R.string.browser_tree_expand_top
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        items(rows, key = { "tree_row_${it.node.path}" }) { row ->
            val isSelectedFile = !row.node.isDirectory && row.node.path == activeNotePath
            val isFocusedDir = row.node.isDirectory && row.node.path == currentDirectory && currentDirectory.isNotEmpty()
            TreeIndentRow(
                row = row,
                isSelectedFile = isSelectedFile,
                isFocusedDir = isFocusedDir,
                onClick = {
                    if (row.node.isDirectory) {
                        onToggleDirectory(row.node.path)
                    } else {
                        onOpenFile(row.node.path)
                    }
                }
            )
        }
    }
}

@Composable
private fun TreeIndentRow(
    row: FlatTreeRow,
    isSelectedFile: Boolean,
    isFocusedDir: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val visualDepth = row.depth.coerceAtMost(6)
    val indentStepDp = 18.dp
    val guideColor = colorScheme.outlineVariant
    val arrowRotation by animateFloatAsState(
        targetValue = if (row.isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "tree_arrow_rotation"
    )

    val containerBg = when {
        isSelectedFile -> colorScheme.primaryContainer
        isFocusedDir -> colorScheme.surfaceContainerHigh
        row.node.isDirectory && row.depth == 0 -> colorScheme.surfaceContainerLowest
        else -> colorScheme.background
    }
    val borderColor = when {
        isSelectedFile -> colorScheme.primary
        isFocusedDir -> colorScheme.primary.copy(alpha = 0.45f)
        row.node.isDirectory && row.depth == 0 -> colorScheme.outlineVariant
        else -> colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (visualDepth > 0) {
                    val stepPx = indentStepDp.toPx()
                    val strokePx = 1.dp.toPx()
                    for (level in 0 until visualDepth) {
                        val lineX = level * stepPx + 10.dp.toPx()
                        drawLine(
                            color = guideColor,
                            start = Offset(lineX, 0f),
                            end = Offset(lineX, size.height),
                            strokeWidth = strokePx
                        )
                    }
                }
            }
            .padding(start = indentStepDp * visualDepth)
    ) {
        Surface(
            color = containerBg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .testTag("tree_indent_item_${row.node.path}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (row.node.isDirectory) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = if (row.isExpanded) "收起目录" else "展开目录",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotation)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (row.isExpanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(22.dp))
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = if (isSelectedFile) colorScheme.primary else colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = row.node.name,
                    style = if (row.node.isDirectory) {
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (isSelectedFile) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (row.node.isDirectory && row.descendantFileCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.surfaceContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = row.descendantFileCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
