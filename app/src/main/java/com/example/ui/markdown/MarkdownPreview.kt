package com.example.ui.markdown

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.RepoConfig
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalPaperColors
import com.example.ui.theme.NewsreaderFontFamily
import kotlinx.coroutines.delay

@Composable
fun MarkdownPreview(
    markdown: String,
    onToggleTaskLine: (Int) -> Unit,
    modifier: Modifier = Modifier,
    currentNotePath: String = "",
    repoConfig: RepoConfig? = null,
    availableFilePaths: List<String> = emptyList(),
    onOpenRelativeNote: (String) -> Unit = {},
    onLinkFeedback: (String) -> Unit = {}
) {
    val parsedDoc = remember(markdown) { parseMarkdownDocument(markdown) }
    val paperColors = LocalPaperColors.current
    val colorScheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    val palette = remember(paperColors.codeBackground, colorScheme.primary, colorScheme.tertiaryContainer) {
        MarkdownInlinePalette(
            codeBackground = paperColors.codeBackground,
            linkColor = colorScheme.primary,
            highlightBackground = colorScheme.tertiaryContainer.copy(alpha = 0.65f)
        )
    }

    val handleLinkNavigation: (String) -> Unit = remember(currentNotePath, availableFilePaths, uriHandler) {
        { rawTarget: String ->
            val action = classifyLinkAction(
                rawTarget = rawTarget,
                currentNotePath = currentNotePath,
                availableFilePaths = availableFilePaths
            )
            when (action.type) {
                ParsedLinkTargetType.EXTERNAL_URL -> {
                    runCatching { uriHandler.openUri(action.target) }
                        .onFailure { onLinkFeedback("无法打开链接：${action.target}") }
                    Unit
                }
                ParsedLinkTargetType.INTERNAL_NOTE -> {
                    val matched = availableFilePaths.firstOrNull {
                        it.equals(action.target, ignoreCase = true)
                    } ?: action.target
                    if (availableFilePaths.isEmpty() || availableFilePaths.any { it.equals(matched, ignoreCase = true) }) {
                        onOpenRelativeNote(matched)
                    } else {
                        onLinkFeedback("未在仓库中找到笔记：${action.target}")
                    }
                }
                ParsedLinkTargetType.ANCHOR_ONLY -> {
                    onLinkFeedback("文内锚点跳转：${action.target}")
                }
            }
        }
    }

    SelectionContainer {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("markdown_preview_root"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (parsedDoc.frontmatter.isNotEmpty()) {
                FrontmatterCard(entries = parsedDoc.frontmatter)
            }

            MarkdownBlockList(
                blocks = parsedDoc.blocks,
                palette = palette,
                referenceLinks = parsedDoc.referenceLinks,
                currentNotePath = currentNotePath,
                repoConfig = repoConfig,
                onToggleTaskLine = onToggleTaskLine,
                onNavigateLink = handleLinkNavigation
            )

            if (parsedDoc.footnotes.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    thickness = 1.dp,
                    color = colorScheme.outlineVariant
                )
                FootnotesCard(
                    footnotes = parsedDoc.footnotes,
                    palette = palette,
                    referenceLinks = parsedDoc.referenceLinks,
                    onNavigateLink = handleLinkNavigation
                )
            }
        }
    }
}

@Composable
private fun MarkdownBlockList(
    blocks: List<MdBlock>,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    currentNotePath: String,
    repoConfig: RepoConfig?,
    onToggleTaskLine: (Int) -> Unit,
    onNavigateLink: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val paperColors = LocalPaperColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> HeadingView(
                    block = block,
                    palette = palette,
                    referenceLinks = referenceLinks,
                    onNavigateLink = onNavigateLink
                )

                is MdBlock.Paragraph -> RichMarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onBackground),
                    palette = palette,
                    referenceLinks = referenceLinks,
                    onNavigateLink = onNavigateLink
                )

                is MdBlock.CodeBlock -> CodeBlockView(
                    block = block,
                    codeBg = paperColors.codeBackground
                )

                is MdBlock.BlockQuote -> BlockQuoteView(
                    block = block,
                    palette = palette,
                    referenceLinks = referenceLinks,
                    currentNotePath = currentNotePath,
                    repoConfig = repoConfig,
                    onToggleTaskLine = onToggleTaskLine,
                    onNavigateLink = onNavigateLink
                )

                is MdBlock.BulletItem -> BulletItemView(
                    block = block,
                    palette = palette,
                    referenceLinks = referenceLinks,
                    onNavigateLink = onNavigateLink
                )

                is MdBlock.OrderedItem -> OrderedItemView(
                    block = block,
                    palette = palette,
                    referenceLinks = referenceLinks,
                    onNavigateLink = onNavigateLink
                )

                is MdBlock.TaskItem -> TaskItemView(
                    block = block,
                    palette = palette,
                    referenceLinks = referenceLinks,
                    onNavigateLink = onNavigateLink,
                    onToggle = {
                        if (block.lineIndex >= 0) {
                            onToggleTaskLine(block.lineIndex)
                        }
                    }
                )

                is MdBlock.Table -> MarkdownTableView(
                    table = block.data,
                    palette = palette,
                    referenceLinks = referenceLinks,
                    onNavigateLink = onNavigateLink
                )

                is MdBlock.ImageBlock -> MarkdownImageView(
                    block = block,
                    currentNotePath = currentNotePath,
                    repoConfig = repoConfig,
                    onNavigateLink = onNavigateLink
                )

                is MdBlock.DetailsBlock -> DetailsBlockView(
                    block = block,
                    palette = palette,
                    referenceLinks = referenceLinks,
                    currentNotePath = currentNotePath,
                    repoConfig = repoConfig,
                    onToggleTaskLine = onToggleTaskLine,
                    onNavigateLink = onNavigateLink
                )

                is MdBlock.HorizontalRule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = colorScheme.outlineVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrontmatterCard(entries: List<Pair<String, String>>) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        color = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            entries.forEach { (key, value) ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceContainerLowest)
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.primary
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadingView(
    block: MdBlock.Heading,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    onNavigateLink: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val style = when (block.level) {
        1 -> TextStyle(
            fontFamily = NewsreaderFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 27.sp,
            lineHeight = 36.sp,
            color = colorScheme.onBackground
        )
        2 -> TextStyle(
            fontFamily = NewsreaderFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            color = colorScheme.onBackground
        )
        3 -> MaterialTheme.typography.titleLarge.copy(
            fontSize = 19.sp,
            color = colorScheme.onBackground
        )
        4 -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onBackground
        )
        else -> MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant
        )
    }
    Column(modifier = Modifier.padding(top = if (block.level <= 2) 10.dp else 6.dp)) {
        RichMarkdownText(
            text = block.text,
            style = style,
            palette = palette,
            referenceLinks = referenceLinks,
            onNavigateLink = onNavigateLink
        )
        if (block.level == 1) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = colorScheme.outlineVariant)
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock, codeBg: Color) {
    val colorScheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600L)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(codeBg)
            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = block.language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(block.code))
                    copied = true
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = if (copied) "已复制代码" else "复制代码",
                    tint = if (copied) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(end = 6.dp)
        ) {
            Text(
                text = block.code,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                    color = colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun BlockQuoteView(
    block: MdBlock.BlockQuote,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    currentNotePath: String,
    repoConfig: RepoConfig?,
    onToggleTaskLine: (Int) -> Unit,
    onNavigateLink: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val paperColors = LocalPaperColors.current

    val accentColor = when (block.calloutType) {
        CalloutType.NOTE -> colorScheme.primary
        CalloutType.TIP -> Color(0xFF2E7D32)
        CalloutType.IMPORTANT -> colorScheme.tertiary
        CalloutType.WARNING -> Color(0xFFD97706)
        CalloutType.CAUTION -> colorScheme.error
        null -> paperColors.quoteBar
    }
    val containerColor = when (block.calloutType) {
        null -> colorScheme.surfaceContainerLow
        else -> accentColor.copy(alpha = 0.08f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .background(containerColor)
            .drawBehind {
                drawLine(
                    color = accentColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 3.5.dp.toPx()
                )
            }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (block.calloutType != null) {
            val icon = when (block.calloutType) {
                CalloutType.NOTE -> Icons.Outlined.Info
                CalloutType.TIP -> Icons.Outlined.Lightbulb
                CalloutType.IMPORTANT -> Icons.Outlined.ReportProblem
                CalloutType.WARNING -> Icons.Outlined.WarningAmber
                CalloutType.CAUTION -> Icons.Outlined.ErrorOutline
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = block.calloutTitle ?: block.calloutType.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor
                )
            }
        }
        if (block.blocks.isNotEmpty()) {
            MarkdownBlockList(
                blocks = block.blocks,
                palette = palette,
                referenceLinks = referenceLinks,
                currentNotePath = currentNotePath,
                repoConfig = repoConfig,
                onToggleTaskLine = onToggleTaskLine,
                onNavigateLink = onNavigateLink
            )
        }
    }
}

@Composable
private fun BulletItemView(
    block: MdBlock.BulletItem,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    onNavigateLink: (String) -> Unit
) {
    val indentDp = (block.indent.coerceAtMost(5) * 16).dp
    val bulletChar = when (block.indent % 3) {
        0 -> "•"
        1 -> "◦"
        else -> "▪"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = bulletChar,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(end = 10.dp)
        )
        RichMarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            palette = palette,
            referenceLinks = referenceLinks,
            onNavigateLink = onNavigateLink,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OrderedItemView(
    block: MdBlock.OrderedItem,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    onNavigateLink: (String) -> Unit
) {
    val indentDp = (block.indent.coerceAtMost(5) * 16).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${block.number}.",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(end = 8.dp)
        )
        RichMarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            palette = palette,
            referenceLinks = referenceLinks,
            onNavigateLink = onNavigateLink,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TaskItemView(
    block: MdBlock.TaskItem,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    onNavigateLink: (String) -> Unit,
    onToggle: () -> Unit
) {
    val indentDp = (block.indent.coerceAtMost(5) * 16).dp
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = block.lineIndex >= 0,
                    role = Role.Checkbox,
                    onClick = onToggle
                )
                .padding(top = 3.dp, end = 8.dp, bottom = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (block.checked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (block.checked) "已完成任务" else "未完成任务",
                tint = if (block.checked) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        RichMarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (block.checked) colorScheme.onSurfaceVariant else colorScheme.onBackground,
                textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None
            ),
            palette = palette,
            referenceLinks = referenceLinks,
            onNavigateLink = onNavigateLink,
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp)
        )
    }
}

@Composable
private fun MarkdownImageView(
    block: MdBlock.ImageBlock,
    currentNotePath: String,
    repoConfig: RepoConfig?,
    onNavigateLink: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val resolvedModel = remember(block.url, currentNotePath, repoConfig, context) {
        buildImageRequest(context, block.url, currentNotePath, repoConfig)
    }

    val isExternalImage = block.url.trim().startsWith("http://", ignoreCase = true) ||
        block.url.trim().startsWith("https://", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainerLow)
            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .then(if (isExternalImage) Modifier.clickable { onNavigateLink(block.url) } else Modifier)
    ) {
        SubcomposeAsyncImage(
            model = resolvedModel,
            contentDescription = block.alt.ifBlank { "图片" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 360.dp),
            error = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = block.alt.ifBlank { block.url },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
        val caption = block.title?.takeIf { it.isNotBlank() } ?: block.alt.takeIf { it.isNotBlank() }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DetailsBlockView(
    block: MdBlock.DetailsBlock,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    currentNotePath: String,
    repoConfig: RepoConfig?,
    onToggleTaskLine: (Int) -> Unit,
    onNavigateLink: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                RichMarkdownText(
                    text = block.summary,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface
                    ),
                    palette = palette,
                    referenceLinks = referenceLinks,
                    onNavigateLink = onNavigateLink,
                    modifier = Modifier.weight(1f)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                ) {
                    MarkdownBlockList(
                        blocks = block.blocks,
                        palette = palette,
                        referenceLinks = referenceLinks,
                        currentNotePath = currentNotePath,
                        repoConfig = repoConfig,
                        onToggleTaskLine = onToggleTaskLine,
                        onNavigateLink = onNavigateLink
                    )
                }
            }
        }
    }
}

@Composable
private fun FootnotesCard(
    footnotes: List<Pair<String, String>>,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    onNavigateLink: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        footnotes.forEach { (id, text) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "[$id]",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.primary
                )
                RichMarkdownText(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant),
                    palette = palette,
                    referenceLinks = referenceLinks,
                    onNavigateLink = onNavigateLink,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun RichMarkdownText(
    text: String,
    style: TextStyle,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    onNavigateLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text, palette, referenceLinks, onNavigateLink) {
        parseInlineMarkdown(
            text = text,
            palette = palette,
            referenceLinks = referenceLinks,
            onNavigateLink = onNavigateLink
        )
    }
    Text(
        text = annotated,
        style = style,
        modifier = modifier
    )
}

private fun buildImageRequest(
    context: android.content.Context,
    rawUrl: String,
    currentNotePath: String,
    repoConfig: RepoConfig?
): ImageRequest {
    val trimmed = rawUrl.trim()
    val builder = ImageRequest.Builder(context).crossfade(true)

    val isExternal = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)

    if (isExternal) {
        builder.data(trimmed)
        if (repoConfig != null && trimmed.contains("raw.githubusercontent.com/${repoConfig.owner}/${repoConfig.repo}/", ignoreCase = true)) {
            builder.addHeader("Authorization", "Bearer ${repoConfig.token}")
        }
    } else if (repoConfig != null) {
        val repoPath = resolveRepoRelativePath(currentNotePath, trimmed.substringBefore('#').substringBefore('?'))
        val encodedSegments = repoPath.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { Uri.encode(it) }
        val apiUrl = "https://api.github.com/repos/${repoConfig.owner}/${repoConfig.repo}/contents/$encodedSegments?ref=${Uri.encode(repoConfig.branch)}"
        builder.data(apiUrl)
        builder.addHeader("Authorization", "Bearer ${repoConfig.token}")
        builder.addHeader("Accept", "application/vnd.github.raw")
        builder.addHeader("X-GitHub-Api-Version", "2022-11-28")
    } else {
        builder.data(trimmed)
    }

    return builder.build()
}
