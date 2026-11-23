package com.example.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class TableAlignment {
    Start,
    Center,
    End
}

internal data class MarkdownTableData(
    val headers: List<String>,
    val alignments: List<TableAlignment>,
    val rows: List<List<String>>
)

internal data class ParsedTableResult(
    val table: MarkdownTableData,
    val nextIndex: Int
)

private val DELIMITER_CELL_REGEX = Regex("^:?-{1,}:?$")

internal fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val headerLine = lines[index].trim()
    val delimiterLine = lines[index + 1].trim()
    if (headerLine.isEmpty() || delimiterLine.isEmpty()) return false
    if (!headerLine.contains('|')) return false
    if (!delimiterLine.contains('|') && !headerLine.startsWith('|')) return false

    val headerCells = splitTableRow(headerLine)
    val delimiterCells = splitTableRow(delimiterLine)
    if (headerCells.isEmpty() || delimiterCells.isEmpty()) return false
    if (delimiterCells.size != headerCells.size) return false

    return delimiterCells.all { cell ->
        val trimmed = cell.trim()
        trimmed.isNotEmpty() && DELIMITER_CELL_REGEX.matches(trimmed)
    }
}

internal fun parseMarkdownTable(lines: List<String>, startIndex: Int): ParsedTableResult? {
    if (!isTableStart(lines, startIndex)) return null

    val headers = splitTableRow(lines[startIndex].trim())
    val delimiterCells = splitTableRow(lines[startIndex + 1].trim())
    val alignments = delimiterCells.map { parseColumnAlignment(it) }
    val colCount = headers.size

    val bodyRows = mutableListOf<List<String>>()
    var i = startIndex + 2
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()
        if (trimmed.isEmpty() ||
            !trimmed.contains('|') ||
            trimmed.startsWith("```") ||
            trimmed.startsWith("#") ||
            trimmed.startsWith(">")
        ) {
            break
        }
        val parsedCells = splitTableRow(trimmed)
        val normalizedRow = List(colCount) { col ->
            parsedCells.getOrNull(col) ?: ""
        }
        bodyRows.add(normalizedRow)
        i++
    }

    return ParsedTableResult(
        table = MarkdownTableData(
            headers = headers,
            alignments = alignments,
            rows = bodyRows
        ),
        nextIndex = i
    )
}

internal fun splitTableRow(line: String): List<String> {
    var working = line.trim()
    if (working.startsWith('|')) {
        working = working.substring(1)
    }
    if (working.endsWith('|') && !isEscapedAt(working, working.lastIndex)) {
        working = working.dropLast(1)
    }

    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var inInlineCode = false
    var i = 0
    while (i < working.length) {
        val ch = working[i]
        if (ch == '\\' && i + 1 < working.length && working[i + 1] == '|') {
            current.append('|')
            i += 2
            continue
        }
        if (ch == '`') {
            inInlineCode = !inInlineCode
            current.append(ch)
            i++
            continue
        }
        if (ch == '|' && !inInlineCode) {
            cells.add(current.toString().trim())
            current.clear()
            i++
            continue
        }
        current.append(ch)
        i++
    }
    cells.add(current.toString().trim())
    return cells
}

private fun isEscapedAt(text: String, index: Int): Boolean {
    var backslashes = 0
    var pos = index - 1
    while (pos >= 0 && text[pos] == '\\') {
        backslashes++
        pos--
    }
    return backslashes % 2 == 1
}

private fun parseColumnAlignment(delimiterCell: String): TableAlignment {
    val trimmed = delimiterCell.trim()
    val startsColon = trimmed.startsWith(':')
    val endsColon = trimmed.endsWith(':') && trimmed.length >= 2
    return when {
        startsColon && endsColon -> TableAlignment.Center
        endsColon -> TableAlignment.End
        else -> TableAlignment.Start
    }
}

private class TableLayoutMetrics {
    var colWidths: IntArray = IntArray(0)
    var rowHeights: IntArray = IntArray(0)
}

@Composable
internal fun MarkdownTableView(
    table: MarkdownTableData,
    codeBg: Color,
    linkColor: Color,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val shape = RoundedCornerShape(12.dp)

    val headerBg = colorScheme.surfaceContainerHigh
    val zebraBg = colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
    val cellBorderColor = colorScheme.outlineVariant.copy(alpha = 0.75f)
    val headerDividerColor = colorScheme.outline.copy(alpha = 0.45f)

    val colCount = table.headers.size
    if (colCount == 0) return
    val totalRows = 1 + table.rows.size

    val minColWidthPx = with(density) { 76.dp.roundToPx() }
    val maxColWidthPx = with(density) { 240.dp.roundToPx() }
    val metrics = remember(table) { TableLayoutMetrics() }

    val headerStyle = MaterialTheme.typography.titleSmall.copy(
        fontWeight = FontWeight.SemiBold,
        color = colorScheme.onSurface
    )
    val cellStyle = MaterialTheme.typography.bodyMedium.copy(
        color = colorScheme.onBackground
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(colorScheme.surfaceContainerLowest)
            .border(1.dp, colorScheme.outlineVariant, shape)
            .testTag("markdown_table")
    ) {
        val containerWidthPx = constraints.maxWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Layout(
                modifier = Modifier.drawBehind {
                    val colWidths = metrics.colWidths
                    val rowHeights = metrics.rowHeights
                    if (colWidths.isEmpty() || rowHeights.isEmpty()) return@drawBehind

                    val totalWidth = size.width
                    val totalHeight = size.height
                    val hairlinePx = 0.75.dp.toPx()
                    val headerRulePx = 1.25.dp.toPx()

                    var y = 0f
                    for (r in rowHeights.indices) {
                        val rowH = rowHeights[r].toFloat()
                        if (r == 0) {
                            drawRect(
                                color = headerBg,
                                topLeft = Offset(0f, y),
                                size = Size(totalWidth, rowH)
                            )
                        } else if (r % 2 == 0) {
                            drawRect(
                                color = zebraBg,
                                topLeft = Offset(0f, y),
                                size = Size(totalWidth, rowH)
                            )
                        }

                        if (r < rowHeights.lastIndex) {
                            val lineY = y + rowH
                            drawLine(
                                color = if (r == 0) headerDividerColor else cellBorderColor,
                                start = Offset(0f, lineY),
                                end = Offset(totalWidth, lineY),
                                strokeWidth = if (r == 0) headerRulePx else hairlinePx
                            )
                        }
                        y += rowH
                    }

                    var x = 0f
                    for (c in 0 until colWidths.lastIndex) {
                        x += colWidths[c].toFloat()
                        drawLine(
                            color = cellBorderColor,
                            start = Offset(x, 0f),
                            end = Offset(x, totalHeight),
                            strokeWidth = hairlinePx
                        )
                    }
                },
                content = {
                    // Row 0: Headers
                    for (c in 0 until colCount) {
                        val textAlign = table.alignments.getOrElse(c) { TableAlignment.Start }.toTextAlign()
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            RichMarkdownText(
                                text = table.headers[c],
                                style = headerStyle.copy(textAlign = textAlign),
                                codeBg = codeBg,
                                linkColor = linkColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Rows 1..N: Body
                    for (row in table.rows) {
                        for (c in 0 until colCount) {
                            val textAlign = table.alignments.getOrElse(c) { TableAlignment.Start }.toTextAlign()
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                RichMarkdownText(
                                    text = row.getOrElse(c) { "" },
                                    style = cellStyle.copy(textAlign = textAlign),
                                    codeBg = codeBg,
                                    linkColor = linkColor,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            ) { measurables, _ ->
                // 1. Compute natural column widths from intrinsic widths
                val colWidths = IntArray(colCount) { minColWidthPx }
                for (r in 0 until totalRows) {
                    for (c in 0 until colCount) {
                        val idx = r * colCount + c
                        if (idx < measurables.size) {
                            val intrinsic = measurables[idx].maxIntrinsicWidth(Constraints.Infinity)
                            colWidths[c] = max(colWidths[c], intrinsic).coerceIn(minColWidthPx, maxColWidthPx)
                        }
                    }
                }

                // 2. If table is narrower than available container width, expand columns proportionally
                val naturalTotalWidth = colWidths.sum()
                if (containerWidthPx != Constraints.Infinity &&
                    naturalTotalWidth in 1 until containerWidthPx
                ) {
                    val extra = containerWidthPx - naturalTotalWidth
                    var distributed = 0
                    for (c in 0 until colCount) {
                        val share = if (c == colCount - 1) {
                            extra - distributed
                        } else {
                            (extra.toFloat() * colWidths[c] / naturalTotalWidth).roundToInt()
                        }
                        colWidths[c] += share
                        distributed += share
                    }
                }

                // 3. Measure each cell with its exact column width and compute row heights
                val rowHeights = IntArray(totalRows)
                val placeables = ArrayList<androidx.compose.ui.layout.Placeable>(measurables.size)
                for (r in 0 until totalRows) {
                    var maxRowHeight = 0
                    for (c in 0 until colCount) {
                        val idx = r * colCount + c
                        val cellConstraints = Constraints(
                            minWidth = colWidths[c],
                            maxWidth = colWidths[c],
                            minHeight = 0,
                            maxHeight = Constraints.Infinity
                        )
                        val placeable = measurables[idx].measure(cellConstraints)
                        placeables.add(placeable)
                        maxRowHeight = max(maxRowHeight, placeable.height)
                    }
                    rowHeights[r] = maxRowHeight
                }

                metrics.colWidths = colWidths
                metrics.rowHeights = rowHeights

                val totalWidth = colWidths.sum()
                val totalHeight = rowHeights.sum()

                layout(totalWidth, totalHeight) {
                    var y = 0
                    for (r in 0 until totalRows) {
                        var x = 0
                        val rowH = rowHeights[r]
                        for (c in 0 until colCount) {
                            val placeable = placeables[r * colCount + c]
                            val cellY = if (r == 0) {
                                y + (rowH - placeable.height) / 2
                            } else {
                                y
                            }
                            placeable.placeRelative(x = x, y = cellY)
                            x += colWidths[c]
                        }
                        y += rowH
                    }
                }
            }
        }
    }
}

private fun TableAlignment.toTextAlign(): TextAlign = when (this) {
    TableAlignment.Start -> TextAlign.Start
    TableAlignment.Center -> TextAlign.Center
    TableAlignment.End -> TextAlign.End
}
