package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BrowserTabOrder
import com.example.data.model.RecentFileEntry
import com.example.data.model.RepoConfig
import com.example.data.model.ThemeMode
import com.example.ui.theme.LocalPaperColors
import com.example.ui.viewmodel.CreateDialogType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentFilesBottomSheet(
    recentFiles: List<RecentFileEntry>,
    currentOpenPath: String?,
    showBrowseAllButton: Boolean,
    onSelectFile: (String) -> Unit,
    onBrowseAllFiles: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val paperColors = LocalPaperColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.recent_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = colorScheme.onSurface
            )

            if (recentFiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.recent_sheet_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentFiles, key = { "sheet_recent_${it.path}" }) { entry ->
                        val isCurrent = entry.path == currentOpenPath
                        Surface(
                            color = if (isCurrent) colorScheme.primaryContainer else colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) colorScheme.primary else colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectFile(entry.path) }
                                .testTag("recent_sheet_item_${entry.path}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = if (isCurrent) colorScheme.primary else colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append(entry.parentDir.ifEmpty { "根目录" })
                                            append(" · ")
                                            append(formatRelativeTime(entry.lastOpenedAt))
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isCurrent) {
                                    BadgePill(
                                        text = stringResource(R.string.recent_badge_current),
                                        bg = colorScheme.primary,
                                        fg = colorScheme.onPrimary
                                    )
                                } else if (entry.wasEdited) {
                                    BadgePill(
                                        text = stringResource(R.string.recent_badge_edited),
                                        bg = paperColors.badgeEditedBg,
                                        fg = paperColors.badgeEditedText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showBrowseAllButton) {
                OutlinedButton(
                    onClick = onBrowseAllFiles,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("recent_sheet_browse_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.recent_sheet_browse_all))
                }
            }
        }
    }
}

@Composable
private fun BadgePill(
    text: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    repoConfig: RepoConfig,
    themeMode: ThemeMode,
    browserTabOrder: BrowserTabOrder,
    autoOpenLastNote: Boolean,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectBrowserTabOrder: (BrowserTabOrder) -> Unit,
    onToggleAutoOpenLastNote: (Boolean) -> Unit,
    onReconfigure: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var confirmDisconnect by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = colorScheme.onSurface
            )

            // Theme selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant
                )
                val modes = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onSelectTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                        ) {
                            Text(text = label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Top segmented tab order selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_tab_order_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant
                )
                val tabOrders = listOf(
                    BrowserTabOrder.FILES_FIRST to stringResource(R.string.settings_tab_order_files_left),
                    BrowserTabOrder.RECENT_FIRST to stringResource(R.string.settings_tab_order_recent_left)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    tabOrders.forEachIndexed { index, (order, label) ->
                        SegmentedButton(
                            selected = browserTabOrder == order,
                            onClick = { onSelectBrowserTabOrder(order) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabOrders.size),
                            modifier = Modifier.testTag("settings_tab_order_${order.name.lowercase()}")
                        ) {
                            Text(text = label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Auto-open last viewed note on app launch toggle
            Surface(
                color = colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggleAutoOpenLastNote(!autoOpenLastNote) }
                    .testTag("settings_auto_open_last_row")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_auto_open_last_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_auto_open_last_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoOpenLastNote,
                        onCheckedChange = onToggleAutoOpenLastNote,
                        modifier = Modifier.testTag("settings_auto_open_last_switch")
                    )
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant)

            // Repo info & connection actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.settings_repo_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant
                )
                Surface(
                    color = colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = repoConfig.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "${stringResource(R.string.settings_branch_label)}：${repoConfig.branch}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onReconfigure,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_reconfigure_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SyncAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_reconfigure))
                }

                TextButton(
                    onClick = { confirmDisconnect = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_disconnect_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_disconnect))
                }
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            containerColor = colorScheme.surfaceContainerLowest,
            title = { Text(stringResource(R.string.settings_disconnect_confirm_title)) },
            text = { Text(stringResource(R.string.settings_disconnect_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisconnect = false
                        onDisconnect()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error),
                    modifier = Modifier.testTag("confirm_disconnect_button")
                ) {
                    Text("确认断开")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) {
                    Text(stringResource(R.string.note_cancel))
                }
            }
        )
    }
}

@Composable
fun CreateEntryDialog(
    type: CreateDialogType,
    currentDirectory: String,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var nameInput by remember(type) { mutableStateOf("") }
    val isNote = type == CreateDialogType.NOTE

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = colorScheme.surfaceContainerLowest,
        title = {
            Text(
                text = if (isNote) {
                    stringResource(R.string.dialog_create_note_title)
                } else {
                    stringResource(R.string.dialog_create_folder_title)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "位置：/${currentDirectory.ifEmpty { "" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = {
                        Text(
                            if (isNote) stringResource(R.string.dialog_create_note_hint)
                            else stringResource(R.string.dialog_create_folder_hint)
                        )
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = !errorMessage.isNullOrBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_entry_name_input")
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.error
                    )
                } else if (!isNote) {
                    Text(
                        text = stringResource(R.string.dialog_create_folder_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameInput) },
                enabled = !isLoading && nameInput.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("create_entry_confirm_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(stringResource(R.string.dialog_create_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.note_cancel))
            }
        }
    )
}

@Composable
fun UnsavedChangesDialog(
    onSaveFirst: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colorScheme.surfaceContainerLowest,
        title = { Text(stringResource(R.string.note_discard_title)) },
        text = { Text(stringResource(R.string.note_discard_message)) },
        confirmButton = {
            Button(
                onClick = onSaveFirst,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("unsaved_save_first_button")
            ) {
                Text(stringResource(R.string.note_discard_save_first))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onDiscard,
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error),
                    modifier = Modifier.testTag("unsaved_discard_button")
                ) {
                    Text(stringResource(R.string.note_discard_confirm))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.note_cancel))
                }
            }
        }
    )
}

@Composable
fun ConflictResolutionDialog(
    onReloadRemote: () -> Unit,
    onOverwriteRemote: () -> Unit,
    onCancel: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colorScheme.surfaceContainerLowest,
        title = { Text(stringResource(R.string.conflict_title)) },
        text = { Text(stringResource(R.string.conflict_message)) },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onReloadRemote,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("conflict_reload_button")
                ) {
                    Text(stringResource(R.string.conflict_reload))
                }
                OutlinedButton(
                    onClick = onOverwriteRemote,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("conflict_overwrite_button")
                ) {
                    Text(
                        text = stringResource(R.string.conflict_overwrite),
                        color = colorScheme.error
                    )
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.note_cancel))
                }
            }
        }
    )
}

private fun formatRelativeTime(timestampMs: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        hours < 24 -> "${hours} 小时前"
        days == 1L -> "昨天"
        days < 30 -> "${days} 天前"
        else -> "较早以前"
    }
}
