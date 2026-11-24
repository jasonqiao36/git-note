package com.example.ui.markdown

internal enum class CalloutType(val title: String) {
    NOTE("Note"),
    TIP("Tip"),
    IMPORTANT("Important"),
    WARNING("Warning"),
    CAUTION("Caution")
}

internal data class ReferenceLinkDef(
    val url: String,
    val title: String? = null
)

internal data class ParsedMarkdownDocument(
    val frontmatter: List<Pair<String, String>> = emptyList(),
    val blocks: List<MdBlock> = emptyList(),
    val referenceLinks: Map<String, ReferenceLinkDef> = emptyMap(),
    val footnotes: List<Pair<String, String>> = emptyList()
)

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class BlockQuote(
        val blocks: List<MdBlock>,
        val calloutType: CalloutType? = null,
        val calloutTitle: String? = null
    ) : MdBlock
    data class BulletItem(val indent: Int, val text: String) : MdBlock
    data class OrderedItem(val indent: Int, val number: String, val text: String) : MdBlock
    data class TaskItem(val indent: Int, val lineIndex: Int, val checked: Boolean, val text: String) : MdBlock
    data class Table(val data: MarkdownTableData) : MdBlock
    data class ImageBlock(val alt: String, val url: String, val title: String? = null) : MdBlock
    data class DetailsBlock(val summary: String, val blocks: List<MdBlock>) : MdBlock
    data object HorizontalRule : MdBlock
}

private val ATX_HEADING_REGEX = Regex("^\\s{0,3}(#{1,6})(?:\\s+(.*)|\\s*)$")
private val SETEXT_H1_REGEX = Regex("^\\s{0,3}=+\\s*$")
private val SETEXT_H2_REGEX = Regex("^\\s{0,3}-+\\s*$")
private val HR_REGEX = Regex("^\\s{0,3}(?:-\\s*-\\s*-+|\\*\\s*\\*\\s*\\*+|_\\s*_\\s*_+)\\s*$")
private val FENCE_START_REGEX = Regex("^\\s{0,3}(`{3,}|~{3,})(.*)$")
private val TASK_LIST_REGEX = Regex("^([\\t ]*)(?:[-*+]|\\d+[.)])\\s+\\[([ xX])](?:\\s+(.*)|\\s*)$")
private val BULLET_LIST_REGEX = Regex("^([\\t ]*)[-*+]\\s+(.*)$")
private val ORDERED_LIST_REGEX = Regex("^([\\t ]*)(\\d{1,9})[.)]\\s+(.*)$")
private val BLOCKQUOTE_LINE_REGEX = Regex("^\\s{0,3}>\\s?(.*)$")
private val CALLOUT_HEADER_REGEX = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)](?:\\s+(.*))?$", RegexOption.IGNORE_CASE)
private val REF_DEF_REGEX = Regex("^\\s{0,3}\\[([^\\]^][^\\]]*)]:\\s*(?:<([^>]+)>|(\\S+))(?:\\s+(?:\"([^\"]*)\"|'([^']*)'|\\(([^)]*)\\)))?\\s*$")
private val FOOTNOTE_DEF_REGEX = Regex("^\\s{0,3}\\[\\^([^\\]\\s]+)]:\\s*(.*)$")
private val IMAGE_BLOCK_REGEX = Regex("^!\\[([^\\]]*)]\\(([^)]+)\\)\\s*$")
private val DETAILS_OPEN_REGEX = Regex("^\\s*<details(?:\\s+open[^>]*)?>\\s*$", RegexOption.IGNORE_CASE)
private val DETAILS_CLOSE_REGEX = Regex("^\\s*</details>\\s*$", RegexOption.IGNORE_CASE)
private val SUMMARY_REGEX = Regex("^\\s*<summary>(.*?)</summary>\\s*$", RegexOption.IGNORE_CASE)

internal fun parseMarkdownDocument(markdown: String): ParsedMarkdownDocument {
    val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    var startLine = 0

    // 1. Extract YAML frontmatter if present at top of file
    val frontmatter = mutableListOf<Pair<String, String>>()
    if (lines.isNotEmpty() && lines[0].trim() == "---") {
        var closeIdx = -1
        for (idx in 1 until lines.size) {
            val t = lines[idx].trim()
            if (t == "---" || t == "...") {
                closeIdx = idx
                break
            }
        }
        if (closeIdx > 1) {
            var looksLikeYaml = false
            var pendingListKey: String? = null
            for (idx in 1 until closeIdx) {
                val line = lines[idx]
                if (line.isBlank() || line.trimStart().startsWith("#")) continue
                val trimmedStart = line.trimStart()
                if (trimmedStart.startsWith("- ")) {
                    val item = trimmedStart.removePrefix("- ").trim().trim('"', '\'')
                    if (pendingListKey != null && item.isNotEmpty()) {
                        val existingIdx = frontmatter.indexOfLast { it.first == pendingListKey }
                        if (existingIdx >= 0) {
                            val prevVal = frontmatter[existingIdx].second
                            frontmatter[existingIdx] = pendingListKey to "$prevVal, $item"
                        } else {
                            frontmatter.add(pendingListKey to item)
                        }
                    } else if (frontmatter.isNotEmpty() && item.isNotEmpty()) {
                        val last = frontmatter.removeAt(frontmatter.lastIndex)
                        frontmatter.add(last.first to "${last.second}, $item")
                    }
                    continue
                }
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    looksLikeYaml = true
                    val key = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim().trim('"', '\'')
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        pendingListKey = null
                        frontmatter.add(key to value)
                    } else if (key.isNotEmpty()) {
                        pendingListKey = key
                    }
                }
            }
            if (looksLikeYaml) {
                startLine = closeIdx + 1
            } else {
                frontmatter.clear()
            }
        }
    }

    // 2. Collect reference link definitions & footnote definitions (skipping fenced code blocks)
    val referenceLinks = linkedMapOf<String, ReferenceLinkDef>()
    val footnotes = linkedMapOf<String, String>()
    val consumedDefinitionLines = hashSetOf<Int>()

    run {
        var idx = startLine
        var activeFenceChar: Char? = null
        var activeFenceLen = 0
        while (idx < lines.size) {
            val line = lines[idx]
            val fenceMatch = FENCE_START_REGEX.find(line)
            if (fenceMatch != null) {
                val fence = fenceMatch.groupValues[1]
                val ch = fence[0]
                if (activeFenceChar == null) {
                    activeFenceChar = ch
                    activeFenceLen = fence.length
                    idx++
                    continue
                } else if (ch == activeFenceChar && fence.length >= activeFenceLen && fenceMatch.groupValues[2].isBlank()) {
                    activeFenceChar = null
                    activeFenceLen = 0
                    idx++
                    continue
                }
            }
            if (activeFenceChar != null) {
                idx++
                continue
            }

            val fnMatch = FOOTNOTE_DEF_REGEX.matchEntire(line)
            if (fnMatch != null) {
                val id = fnMatch.groupValues[1].trim()
                val textParts = mutableListOf(fnMatch.groupValues[2].trim())
                consumedDefinitionLines.add(idx)
                var next = idx + 1
                while (next < lines.size && (lines[next].startsWith("    ") || lines[next].startsWith("\t"))) {
                    textParts.add(lines[next].trim())
                    consumedDefinitionLines.add(next)
                    next++
                }
                footnotes[id] = textParts.filter { it.isNotEmpty() }.joinToString(" ")
                idx = next
                continue
            }

            val refMatch = REF_DEF_REGEX.matchEntire(line)
            if (refMatch != null) {
                val id = normalizeRefId(refMatch.groupValues[1])
                val url = (refMatch.groupValues[2].ifEmpty { refMatch.groupValues[3] }).trim()
                val title = refMatch.groupValues[4].ifEmpty {
                    refMatch.groupValues[5].ifEmpty { refMatch.groupValues[6] }
                }.takeIf { it.isNotEmpty() }
                if (id.isNotEmpty() && url.isNotEmpty() && !referenceLinks.containsKey(id)) {
                    referenceLinks[id] = ReferenceLinkDef(url = url, title = title)
                }
                consumedDefinitionLines.add(idx)
            }
            idx++
        }
    }

    val blocks = parseBlockRange(
        lines = lines,
        fromIndex = startLine,
        toIndexExclusive = lines.size,
        consumedLines = consumedDefinitionLines,
        referenceLinks = referenceLinks
    )

    return ParsedMarkdownDocument(
        frontmatter = frontmatter,
        blocks = blocks,
        referenceLinks = referenceLinks,
        footnotes = footnotes.entries.map { it.key to it.value }
    )
}

private fun parseBlockRange(
    lines: List<String>,
    fromIndex: Int,
    toIndexExclusive: Int,
    consumedLines: Set<Int>,
    referenceLinks: Map<String, ReferenceLinkDef>,
    lineIndexOffset: Int = 0
): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    var i = fromIndex

    while (i < toIndexExclusive) {
        if (i in consumedLines) {
            i++
            continue
        }

        val rawLine = lines[i]
        val trimmed = rawLine.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // HTML multi-line/single-line comment <!-- ... -->
        if (trimmed.startsWith("<!--")) {
            if (trimmed.contains("-->")) {
                val afterComment = trimmed.substringAfter("-->").trim()
                if (afterComment.isEmpty()) {
                    i++
                    continue
                }
            } else {
                i++
                while (i < toIndexExclusive && !lines[i].contains("-->")) {
                    i++
                }
                if (i < toIndexExclusive) i++
                continue
            }
        }

        // <details> collapsible block
        if (DETAILS_OPEN_REGEX.matches(rawLine)) {
            val closeIdx = findMatchingDetailsClose(lines, i + 1, toIndexExclusive)
            if (closeIdx != -1) {
                var innerStart = i + 1
                var summary = "详细信息"
                while (innerStart < closeIdx && lines[innerStart].isBlank()) {
                    innerStart++
                }
                if (innerStart < closeIdx) {
                    val sumMatch = SUMMARY_REGEX.matchEntire(lines[innerStart])
                    if (sumMatch != null) {
                        summary = sumMatch.groupValues[1].trim().ifEmpty { summary }
                        innerStart++
                    }
                }
                val innerBlocks = parseBlockRange(
                    lines = lines,
                    fromIndex = innerStart,
                    toIndexExclusive = closeIdx,
                    consumedLines = consumedLines,
                    referenceLinks = referenceLinks,
                    lineIndexOffset = lineIndexOffset
                )
                blocks.add(MdBlock.DetailsBlock(summary = summary, blocks = innerBlocks))
                i = closeIdx + 1
                continue
            }
        }

        // Fenced code block (``` or ~~~)
        val fenceMatch = FENCE_START_REGEX.find(rawLine)
        if (fenceMatch != null) {
            val fence = fenceMatch.groupValues[1]
            val fenceChar = fence[0]
            val fenceLen = fence.length
            val lang = fenceMatch.groupValues[2].trim().substringBefore(' ')
            val codeLines = mutableListOf<String>()
            i++
            while (i < toIndexExclusive) {
                val closeCandidate = FENCE_START_REGEX.find(lines[i])
                if (closeCandidate != null &&
                    closeCandidate.groupValues[1][0] == fenceChar &&
                    closeCandidate.groupValues[1].length >= fenceLen &&
                    closeCandidate.groupValues[2].isBlank()
                ) {
                    i++
                    break
                }
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        // Horizontal rule (also accept <hr>, <hr/>)
        if (HR_REGEX.matches(rawLine) || trimmed.equals("<hr>", ignoreCase = true) ||
            trimmed.equals("<hr/>", ignoreCase = true) || trimmed.equals("<hr />", ignoreCase = true)
        ) {
            blocks.add(MdBlock.HorizontalRule)
            i++
            continue
        }

        // ATX Heading (# ... ######)
        val headingMatch = ATX_HEADING_REGEX.matchEntire(rawLine)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2]
                .replace(Regex("\\s+#+\\s*$"), "")
                .trim()
            blocks.add(MdBlock.Heading(level, headingText))
            i++
            continue
        }

        // Blockquote (and GitHub Callouts [!NOTE] / [!TIP] / ...)
        if (BLOCKQUOTE_LINE_REGEX.matches(rawLine)) {
            val quoteRawLines = mutableListOf<String>()
            while (i < toIndexExclusive && i !in consumedLines) {
                val bqMatch = BLOCKQUOTE_LINE_REGEX.matchEntire(lines[i]) ?: break
                quoteRawLines.add(bqMatch.groupValues[1])
                i++
            }
            var calloutType: CalloutType? = null
            var calloutTitle: String? = null
            var contentStart = 0
            if (quoteRawLines.isNotEmpty()) {
                val calloutMatch = CALLOUT_HEADER_REGEX.matchEntire(quoteRawLines[0].trim())
                if (calloutMatch != null) {
                    calloutType = CalloutType.entries.firstOrNull {
                        it.name.equals(calloutMatch.groupValues[1], ignoreCase = true)
                    }
                    calloutTitle = calloutMatch.groupValues[2].trim().ifEmpty { calloutType?.title }
                    contentStart = 1
                }
            }
            val innerBlocks = parseBlockRange(
                lines = quoteRawLines,
                fromIndex = contentStart,
                toIndexExclusive = quoteRawLines.size,
                consumedLines = emptySet(),
                referenceLinks = referenceLinks,
                lineIndexOffset = -1
            )
            blocks.add(
                MdBlock.BlockQuote(
                    blocks = innerBlocks,
                    calloutType = calloutType,
                    calloutTitle = calloutTitle
                )
            )
            continue
        }

        // Task list item (checked/unchecked)
        val taskMatch = TASK_LIST_REGEX.matchEntire(rawLine)
        if (taskMatch != null) {
            val indent = computeIndentLevel(taskMatch.groupValues[1])
            val checked = taskMatch.groupValues[2].equals("x", ignoreCase = true)
            val firstLine = taskMatch.groupValues[3]
            val itemLines = mutableListOf(firstLine)
            val taskLineIndex = if (lineIndexOffset >= 0) i + lineIndexOffset else -1
            i++
            while (i < toIndexExclusive && i !in consumedLines && isListContinuationLine(lines, i, toIndexExclusive)) {
                itemLines.add(lines[i].trim())
                i++
            }
            blocks.add(
                MdBlock.TaskItem(
                    indent = indent,
                    lineIndex = taskLineIndex,
                    checked = checked,
                    text = joinParagraphLines(itemLines)
                )
            )
            continue
        }

        // Bullet list item
        val bulletMatch = BULLET_LIST_REGEX.matchEntire(rawLine)
        if (bulletMatch != null) {
            val indent = computeIndentLevel(bulletMatch.groupValues[1])
            val itemLines = mutableListOf(bulletMatch.groupValues[2])
            i++
            while (i < toIndexExclusive && i !in consumedLines && isListContinuationLine(lines, i, toIndexExclusive)) {
                itemLines.add(lines[i].trim())
                i++
            }
            blocks.add(MdBlock.BulletItem(indent = indent, text = joinParagraphLines(itemLines)))
            continue
        }

        // Ordered list item
        val orderedMatch = ORDERED_LIST_REGEX.matchEntire(rawLine)
        if (orderedMatch != null) {
            val indent = computeIndentLevel(orderedMatch.groupValues[1])
            val number = orderedMatch.groupValues[2]
            val itemLines = mutableListOf(orderedMatch.groupValues[3])
            i++
            while (i < toIndexExclusive && i !in consumedLines && isListContinuationLine(lines, i, toIndexExclusive)) {
                itemLines.add(lines[i].trim())
                i++
            }
            blocks.add(MdBlock.OrderedItem(indent = indent, number = number, text = joinParagraphLines(itemLines)))
            continue
        }

        // GFM Table
        val parsedTable = parseMarkdownTable(lines.subList(0, toIndexExclusive), i)
        if (parsedTable != null) {
            blocks.add(MdBlock.Table(parsedTable.table))
            i = parsedTable.nextIndex
            continue
        }

        // Standalone image line ![alt](url "title")
        val standaloneImage = parseStandaloneImageBlock(trimmed)
        if (standaloneImage != null) {
            blocks.add(standaloneImage)
            i++
            continue
        }

        // Indented code block (4 spaces or 1 tab)
        if (rawLine.startsWith("    ") || rawLine.startsWith("\t")) {
            val codeLines = mutableListOf<String>()
            while (i < toIndexExclusive && i !in consumedLines) {
                val candidate = lines[i]
                if (candidate.isBlank()) {
                    // Peek ahead: only continue indented code block if another indented line follows
                    val hasMoreIndented = (i + 1 until toIndexExclusive).any { k ->
                        val nextL = lines[k]
                        if (nextL.isBlank()) false else nextL.startsWith("    ") || nextL.startsWith("\t")
                    }
                    if (!hasMoreIndented) break
                    codeLines.add("")
                    i++
                } else if (candidate.startsWith("    ")) {
                    codeLines.add(candidate.substring(4))
                    i++
                } else if (candidate.startsWith("\t")) {
                    codeLines.add(candidate.substring(1))
                    i++
                } else {
                    break
                }
            }
            while (codeLines.isNotEmpty() && codeLines.last().isBlank()) {
                codeLines.removeAt(codeLines.lastIndex)
            }
            blocks.add(MdBlock.CodeBlock(language = "", code = codeLines.joinToString("\n")))
            continue
        }

        // Paragraph (including Setext Heading detection: Title\n=== or Title\n---)
        val paraLines = mutableListOf(rawLine)
        i++
        var setextLevel: Int? = null
        while (i < toIndexExclusive && i !in consumedLines) {
            val next = lines[i]
            val nextTrim = next.trim()

            // Setext heading underline (only when previous lines are plain paragraph text)
            if (paraLines.size == 1 && (SETEXT_H1_REGEX.matches(next) || SETEXT_H2_REGEX.matches(next))) {
                setextLevel = if (SETEXT_H1_REGEX.matches(next)) 1 else 2
                i++
                break
            }

            if (nextTrim.isEmpty() ||
                nextTrim.startsWith("<!--") ||
                DETAILS_OPEN_REGEX.matches(next) ||
                FENCE_START_REGEX.matches(next) ||
                ATX_HEADING_REGEX.matches(next) ||
                BLOCKQUOTE_LINE_REGEX.matches(next) ||
                HR_REGEX.matches(next) ||
                TASK_LIST_REGEX.matches(next) ||
                BULLET_LIST_REGEX.matches(next) ||
                ORDERED_LIST_REGEX.matches(next) ||
                isTableStart(lines.subList(0, toIndexExclusive), i) ||
                parseStandaloneImageBlock(nextTrim) != null
            ) {
                break
            }
            paraLines.add(next)
            i++
        }

        if (setextLevel != null) {
            blocks.add(MdBlock.Heading(setextLevel, paraLines[0].trim()))
        } else {
            blocks.add(MdBlock.Paragraph(joinParagraphLines(paraLines)))
        }
    }

    return blocks
}

private fun findMatchingDetailsClose(lines: List<String>, fromIndex: Int, toIndexExclusive: Int): Int {
    var depth = 1
    for (idx in fromIndex until toIndexExclusive) {
        if (DETAILS_OPEN_REGEX.matches(lines[idx])) depth++
        else if (DETAILS_CLOSE_REGEX.matches(lines[idx])) {
            depth--
            if (depth == 0) return idx
        }
    }
    return -1
}

private fun isListContinuationLine(lines: List<String>, index: Int, toIndexExclusive: Int): Boolean {
    val line = lines[index]
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    if (FENCE_START_REGEX.matches(line) ||
        ATX_HEADING_REGEX.matches(line) ||
        BLOCKQUOTE_LINE_REGEX.matches(line) ||
        HR_REGEX.matches(line) ||
        TASK_LIST_REGEX.matches(line) ||
        BULLET_LIST_REGEX.matches(line) ||
        ORDERED_LIST_REGEX.matches(line) ||
        isTableStart(lines.subList(0, toIndexExclusive), index)
    ) {
        return false
    }
    // Continuation must either be indented (2+ spaces or tab) or lazy-wrapped plain text
    return line.startsWith("  ") || line.startsWith("\t") || trimmed.isNotEmpty()
}

private fun joinParagraphLines(lines: List<String>): String {
    if (lines.isEmpty()) return ""
    val sb = StringBuilder()
    lines.forEachIndexed { idx, line ->
        val cleaned = line
            .replace(Regex(" {2,}$"), "")
            .let { if (it.endsWith("\\") && !it.endsWith("\\\\")) it.dropLast(1) else it }
        sb.append(cleaned)
        if (idx < lines.lastIndex) {
            sb.append('\n')
        }
    }
    return sb.toString()
}

private fun computeIndentLevel(whitespacePrefix: String): Int {
    var spaces = 0
    for (ch in whitespacePrefix) {
        spaces += if (ch == '\t') 4 else 1
    }
    return (spaces / 2).coerceIn(0, 5)
}

internal fun parseStandaloneImageBlock(trimmedLine: String): MdBlock.ImageBlock? {
    if (!trimmedLine.startsWith("![")) return null
    val match = IMAGE_BLOCK_REGEX.matchEntire(trimmedLine) ?: return null
    val alt = match.groupValues[1].trim()
    val parsedDest = parseLinkDestinationAndTitle(match.groupValues[2]) ?: return null
    return MdBlock.ImageBlock(
        alt = alt,
        url = parsedDest.first,
        title = parsedDest.second
    )
}

internal fun normalizeRefId(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").lowercase()
