package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.RepoConfig
import com.example.ui.markdown.MarkdownPreview
import com.example.ui.theme.EditorMonoTextStyle
import com.example.ui.viewmodel.OpenNoteState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    note: OpenNoteState,
    showBackButton: Boolean,
    hasRecentFiles: Boolean,
    repoConfig: RepoConfig?,
    availableFilePaths: List<String>,
    onBack: () -> Unit,
    onToggleEditMode: () -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleTaskLine: (Int) -> Unit,
    onOpenRelativeNote: (String) -> Unit,
    onLinkFeedback: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onOpenRecentSwitcher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    // Keep TextFieldValue in sync with note path and external changes (like reload/task toggle)
    var editorValue by remember(note.path) {
        mutableStateOf(TextFieldValue(note.draftContent, TextRange(note.draftContent.length)))
    }
    LaunchedEffect(note.draftContent, note.isLoading) {
        if (!note.isLoading && editorValue.text != note.draftContent) {
            val safeOffset = editorValue.selection.end.coerceIn(0, note.draftContent.length)
            editorValue = TextFieldValue(note.draftContent, TextRange(safeOffset))
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.background,
                        titleContentColor = colorScheme.onBackground
                    ),
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("note_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.note_back)
                                )
                            }
                        }
                    },
                    title = {
                        // Tappable title pill to open Recent Files Quick Switcher effortlessly
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onOpenRecentSwitcher)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("note_title_recent_switcher")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = note.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.note_switch_recent),
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (note.hasUnsavedChanges) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(colorScheme.primary)
                                    )
                                    Text(
                                        text = stringResource(R.string.note_unsaved),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = note.parentDir.ifEmpty { stringResource(R.string.browser_root) },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (hasRecentFiles && !note.hasUnsavedChanges && !note.isEditMode) {
                            IconButton(
                                onClick = onOpenRecentSwitcher,
                                modifier = Modifier.testTag("note_recent_history_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = stringResource(R.string.note_switch_recent),
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!note.isLoading && note.errorMessage == null && note.isMarkdown) {
                            IconButton(
                                onClick = onToggleEditMode,
                                modifier = Modifier.testTag("note_toggle_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (note.isEditMode) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                                    contentDescription = if (note.isEditMode) {
                                        stringResource(R.string.note_mode_preview)
                                    } else {
                                        stringResource(R.string.note_mode_edit)
                                    },
                                    tint = colorScheme.primary
                                )
                            }
                        }

                        if (note.hasUnsavedChanges || note.isSaving) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Button(
                                onClick = onSave,
                                enabled = !note.isSaving,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 0.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .height(36.dp)
                                    .testTag("note_save_button")
                            ) {
                                if (note.isSaving) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.note_save),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                )
                HorizontalDivider(thickness = 1.dp, color = colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            if (!note.isLoading && note.errorMessage == null && note.isEditMode) {
                MarkdownQuickSymbolBar(
                    onInsertSnippet = { prefix, suffix ->
                        val updated = applyMarkdownInsertion(editorValue, prefix, suffix)
                        editorValue = updated
                        onDraftChange(updated.text)
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                note.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }
                note.errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = note.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.error
                            )
                            OutlinedButton(
                                onClick = onRetry,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("note_retry_button")
                            ) {
                                Text(stringResource(R.string.browser_retry))
                            }
                        }
                    }
                }
                else -> {
                    AnimatedContent(
                        targetState = note.isEditMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "note_mode_transition",
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) { isEditing ->
                        if (isEditing) {
                            PlainTextEditorPane(
                                value = editorValue,
                                onValueChange = { newVal ->
                                    editorValue = newVal
                                    onDraftChange(newVal.text)
                                }
                            )
                        } else {
                            MarkdownReadingPane(
                                content = note.draftContent,
                                currentNotePath = note.path,
                                repoConfig = repoConfig,
                                availableFilePaths = availableFilePaths,
                                onToggleTaskLine = onToggleTaskLine,
                                onOpenRelativeNote = onOpenRelativeNote,
                                onLinkFeedback = onLinkFeedback,
                                onTapEmptyToEdit = onToggleEditMode
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownReadingPane(
    content: String,
    currentNotePath: String,
    repoConfig: RepoConfig?,
    availableFilePaths: List<String>,
    onToggleTaskLine: (Int) -> Unit,
    onOpenRelativeNote: (String) -> Unit,
    onLinkFeedback: (String) -> Unit,
    onTapEmptyToEdit: () -> Unit
) {
    val scrollState = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 20.dp)
            .navigationBarsPadding()
    ) {
        if (content.isBlank()) {
            Surface(
                color = colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onTapEmptyToEdit)
            ) {
                Text(
                    text = stringResource(R.string.note_empty_preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            MarkdownPreview(
                markdown = content,
                onToggleTaskLine = onToggleTaskLine,
                currentNotePath = currentNotePath,
                repoConfig = repoConfig,
                availableFilePaths = availableFilePaths,
                onOpenRelativeNote = onOpenRelativeNote,
                onLinkFeedback = onLinkFeedback
            )
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun PlainTextEditorPane(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val scrollState = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainerLowest)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = stringResource(R.string.note_editor_placeholder),
                style = EditorMonoTextStyle,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = EditorMonoTextStyle.copy(color = colorScheme.onBackground),
            cursorBrush = SolidColor(colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .testTag("note_editor_textfield")
        )
    }
}

private data class MdShortcut(
    val label: String,
    val prefix: String,
    val suffix: String = ""
)

@Composable
private fun MarkdownQuickSymbolBar(
    onInsertSnippet: (prefix: String, suffix: String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shortcuts = remember {
        listOf(
            MdShortcut("#", "# "),
            MdShortcut("##", "## "),
            MdShortcut("• 列表", "- "),
            MdShortcut("☐ 待办", "- [ ] "),
            MdShortcut("粗体", "**", "**"),
            MdShortcut("链接", "[", "](https://)"),
            MdShortcut("代码", "`", "`"),
            MdShortcut("引用", "> "),
            MdShortcut("代码块", "```\n", "\n```"),
            MdShortcut("分割线", "\n---\n")
        )
    }

    Surface(
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                shortcuts.forEach { item ->
                    Surface(
                        color = colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onInsertSnippet(item.prefix, item.suffix) }
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun applyMarkdownInsertion(
    current: TextFieldValue,
    prefix: String,
    suffix: String
): TextFieldValue {
    val text = current.text
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    val selected = text.substring(start, end)
    val replacement = "$prefix$selected$suffix"
    val newText = text.substring(0, start) + replacement + text.substring(end)
    val newCursor = if (selected.isEmpty()) {
        start + prefix.length
    } else {
        start + replacement.length
    }
    return TextFieldValue(text = newText, selection = TextRange(newCursor))
}
