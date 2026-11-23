package com.example.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalPaperColors
import com.example.ui.theme.NewsreaderFontFamily

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class BlockQuote(val lines: List<String>) : MdBlock
    data class BulletItem(val indent: Int, val text: String) : MdBlock
    data class OrderedItem(val indent: Int, val number: String, val text: String) : MdBlock
    data class TaskItem(val indent: Int, val lineIndex: Int, val checked: Boolean, val text: String) : MdBlock
    data class Table(val data: MarkdownTableData) : MdBlock
    data object HorizontalRule : MdBlock
}

@Composable
fun MarkdownPreview(
    markdown: String,
    onToggleTaskLine: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val paperColors = LocalPaperColors.current
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> HeadingView(block)
                is MdBlock.Paragraph -> RichMarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onBackground),
                    codeBg = paperColors.codeBackground,
                    linkColor = colorScheme.primary
                )
                is MdBlock.CodeBlock -> CodeBlockView(block, paperColors.codeBackground)
                is MdBlock.BlockQuote -> BlockQuoteView(
                    lines = block.lines,
                    barColor = paperColors.quoteBar,
                    bgColor = colorScheme.surfaceContainerLow,
                    codeBg = paperColors.codeBackground,
                    linkColor = colorScheme.primary
                )
                is MdBlock.BulletItem -> BulletItemView(block, paperColors.codeBackground, colorScheme.primary)
                is MdBlock.OrderedItem -> OrderedItemView(block, paperColors.codeBackground, colorScheme.primary)
                is MdBlock.TaskItem -> TaskItemView(
                    block = block,
                    codeBg = paperColors.codeBackground,
                    linkColor = colorScheme.primary,
                    onToggle = { onToggleTaskLine(block.lineIndex) }
                )
                is MdBlock.Table -> MarkdownTableView(
                    table = block.data,
                    codeBg = paperColors.codeBackground,
                    linkColor = colorScheme.primary
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

@Composable
private fun HeadingView(block: MdBlock.Heading) {
    val colorScheme = MaterialTheme.colorScheme
    val paperColors = LocalPaperColors.current
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
        else -> MaterialTheme.typography.titleMedium.copy(
            color = colorScheme.onBackground
        )
    }
    Column(modifier = Modifier.padding(top = if (block.level <= 2) 10.dp else 6.dp)) {
        RichMarkdownText(
            text = block.text,
            style = style,
            codeBg = paperColors.codeBackground,
            linkColor = colorScheme.primary
        )
        if (block.level == 1) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock, codeBg: Color) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(codeBg)
            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        if (block.language.isNotBlank()) {
            Text(
                text = block.language,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
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
    lines: List<String>,
    barColor: Color,
    bgColor: Color,
    codeBg: Color,
    linkColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .background(bgColor)
            .drawBehind {
                drawLine(
                    color = barColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 3.5.dp.toPx()
                )
            }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { line ->
            RichMarkdownText(
                text = line,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                ),
                codeBg = codeBg,
                linkColor = linkColor
            )
        }
    }
}

@Composable
private fun BulletItemView(block: MdBlock.BulletItem, codeBg: Color, linkColor: Color) {
    val indentDp = (block.indent.coerceAtMost(4) * 16).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(end = 10.dp)
        )
        RichMarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            codeBg = codeBg,
            linkColor = linkColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OrderedItemView(block: MdBlock.OrderedItem, codeBg: Color, linkColor: Color) {
    val indentDp = (block.indent.coerceAtMost(4) * 16).dp
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
            codeBg = codeBg,
            linkColor = linkColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TaskItemView(
    block: MdBlock.TaskItem,
    codeBg: Color,
    linkColor: Color,
    onToggle: () -> Unit
) {
    val indentDp = (block.indent.coerceAtMost(4) * 16).dp
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (block.checked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = if (block.checked) "已完成任务" else "未完成任务",
            tint = if (block.checked) colorScheme.primary else colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        RichMarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (block.checked) colorScheme.onSurfaceVariant else colorScheme.onBackground,
                textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None
            ),
            codeBg = codeBg,
            linkColor = linkColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Suppress("DEPRECATION")
@Composable
internal fun RichMarkdownText(
    text: String,
    style: TextStyle,
    codeBg: Color,
    linkColor: Color,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, codeBg, linkColor) {
        parseInlineMarkdown(text, codeBg, linkColor)
    }
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    runCatching { uriHandler.openUri(annotation.item) }
                }
        }
    )
}

private fun parseMarkdownBlocks(markdown: String): List<MdBlock> {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0

    while (i < lines.size) {
        val rawLine = lines[i]
        val trimmed = rawLine.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Fenced code block
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // consume closing ```
            blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        // Horizontal rule
        if (trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
            blocks.add(MdBlock.HorizontalRule)
            i++
            continue
        }

        // Heading
        val headingMatch = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2].trimEnd('#').trim()
            blocks.add(MdBlock.Heading(level, text))
            i++
            continue
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            blocks.add(MdBlock.BlockQuote(quoteLines))
            continue
        }

        // Task list item
        val taskMatch = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.*)$").find(rawLine)
        if (taskMatch != null) {
            val indent = taskMatch.groupValues[1].length / 2
            val checked = taskMatch.groupValues[2].equals("x", ignoreCase = true)
            val text = taskMatch.groupValues[3]
            blocks.add(MdBlock.TaskItem(indent, i, checked, text))
            i++
            continue
        }

        // Bullet list item
        val bulletMatch = Regex("^(\\s*)[-*+]\\s+(.*)$").find(rawLine)
        if (bulletMatch != null) {
            val indent = bulletMatch.groupValues[1].length / 2
            val text = bulletMatch.groupValues[2]
            blocks.add(MdBlock.BulletItem(indent, text))
            i++
            continue
        }

        // Ordered list item
        val orderedMatch = Regex("^(\\s*)(\\d+)\\.\\s+(.*)$").find(rawLine)
        if (orderedMatch != null) {
            val indent = orderedMatch.groupValues[1].length / 2
            val number = orderedMatch.groupValues[2]
            val text = orderedMatch.groupValues[3]
            blocks.add(MdBlock.OrderedItem(indent, number, text))
            i++
            continue
        }

        // GFM Table
        val parsedTable = parseMarkdownTable(lines, i)
        if (parsedTable != null) {
            blocks.add(MdBlock.Table(parsedTable.table))
            i = parsedTable.nextIndex
            continue
        }

        // Paragraph (group consecutive plain lines)
        val paraLines = mutableListOf(rawLine)
        i++
        while (i < lines.size) {
            val next = lines[i]
            val nextTrim = next.trim()
            if (nextTrim.isEmpty() ||
                nextTrim.startsWith("```") ||
                nextTrim.startsWith("#") ||
                nextTrim.startsWith(">") ||
                nextTrim.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) ||
                Regex("^(\\s*)[-*+]\\s+.*$").matches(next) ||
                Regex("^(\\s*)\\d+\\.\\s+.*$").matches(next) ||
                isTableStart(lines, i)
            ) {
                break
            }
            paraLines.add(next)
            i++
        }
        blocks.add(MdBlock.Paragraph(paraLines.joinToString("\n")))
    }

    return blocks
}

private fun parseInlineMarkdown(text: String, codeBg: Color, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        val n = text.length
        while (idx < n) {
            // Inline code: `...`
            if (text[idx] == '`') {
                val end = text.indexOf('`', idx + 1)
                if (end != -1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 14.sp,
                            background = codeBg
                        )
                    )
                    append(" ${text.substring(idx + 1, end)} ")
                    pop()
                    idx = end + 1
                    continue
                }
            }

            // Link: [label](url)
            if (text[idx] == '[') {
                val closeBracket = text.indexOf(']', idx + 1)
                if (closeBracket != -1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = text.substring(idx + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen).trim()
                        val startPos = length
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        append(label)
                        pop()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            addStringAnnotation(tag = "URL", annotation = url, start = startPos, end = length)
                        }
                        idx = closeParen + 1
                        continue
                    }
                }
            }

            // Bold: **...** or __...__
            if (idx + 1 < n && ((text[idx] == '*' && text[idx + 1] == '*') || (text[idx] == '_' && text[idx + 1] == '_'))) {
                val delimiter = text.substring(idx, idx + 2)
                val end = text.indexOf(delimiter, idx + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(idx + 2, end))
                    pop()
                    idx = end + 2
                    continue
                }
            }

            // Strikethrough: ~~...~~
            if (idx + 1 < n && text[idx] == '~' && text[idx + 1] == '~') {
                val end = text.indexOf("~~", idx + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(text.substring(idx + 2, end))
                    pop()
                    idx = end + 2
                    continue
                }
            }

            // Italic: *...*
            if (text[idx] == '*') {
                val end = text.indexOf('*', idx + 1)
                if (end != -1 && end > idx + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(idx + 1, end))
                    pop()
                    idx = end + 1
                    continue
                }
            }

            append(text[idx])
            idx++
        }
    }
}
