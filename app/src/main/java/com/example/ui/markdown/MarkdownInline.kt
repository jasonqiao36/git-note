package com.example.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily

internal data class MarkdownInlinePalette(
    val codeBackground: Color,
    val linkColor: Color,
    val highlightBackground: Color
)

internal enum class ParsedLinkTargetType {
    EXTERNAL_URL,
    INTERNAL_NOTE,
    ANCHOR_ONLY
}

internal data class ParsedLinkAction(
    val type: ParsedLinkTargetType,
    val target: String
)

private val ESCAPABLE_PUNCTUATION = setOf(
    '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
    ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '`', '{', '|', '}', '~'
)

private val BARE_URL_REGEX = Regex(
    """^(?:https?://[^\s<>\u0000-\u001F\u2000-\u206F\u3000-\u303F\uFF00-\uFFEF]+|www\.[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+(?:[/?#][^\s<>\u0000-\u001F\u2000-\u206F\u3000-\u303F\uFF00-\uFFEF]*)?)""",
    RegexOption.IGNORE_CASE
)
private val BARE_EMAIL_REGEX = Regex(
    """^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""
)
private val HTML_ANCHOR_REGEX = Regex(
    """^<a\b[^>]*\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))[^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val HTML_BR_REGEX = Regex("""^<br\s*/?>""", RegexOption.IGNORE_CASE)
private val HTML_IMG_REGEX = Regex(
    """^<img\b[^>]*\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)')[^>]*>""",
    RegexOption.IGNORE_CASE
)

internal fun parseInlineMarkdown(
    text: String,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef> = emptyMap(),
    onNavigateLink: (String) -> Unit = {}
): AnnotatedString {
    return buildAnnotatedString {
        appendInlineSegment(
            text = text,
            palette = palette,
            referenceLinks = referenceLinks,
            onNavigateLink = onNavigateLink
        )
    }
}

private fun AnnotatedString.Builder.appendInlineSegment(
    text: String,
    palette: MarkdownInlinePalette,
    referenceLinks: Map<String, ReferenceLinkDef>,
    onNavigateLink: (String) -> Unit
) {
    var idx = 0
    val n = text.length

    while (idx < n) {
        val ch = text[idx]

        // 1. Backslash escape
        if (ch == '\\' && idx + 1 < n && text[idx + 1] in ESCAPABLE_PUNCTUATION) {
            append(text[idx + 1])
            idx += 2
            continue
        }

        // 2. HTML comment <!-- ... -->
        if (ch == '<' && text.startsWith("<!--", idx)) {
            val endComment = text.indexOf("-->", idx + 4)
            if (endComment != -1) {
                idx = endComment + 3
                continue
            }
        }

        // 3. Inline code span (`...` or ``...``)
        if (ch == '`') {
            val tickCount = countRun(text, idx, '`')
            val closingIdx = findClosingBackticks(text, idx + tickCount, tickCount)
            if (closingIdx != -1) {
                var codeText = text.substring(idx + tickCount, closingIdx)
                    .replace('\n', ' ')
                if (codeText.length >= 2 && codeText.startsWith(' ') && codeText.endsWith(' ') && codeText.any { it != ' ' }) {
                    codeText = codeText.substring(1, codeText.length - 1)
                }
                pushStyle(
                    SpanStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.5.sp,
                        background = palette.codeBackground
                    )
                )
                append(codeText)
                pop()
                idx = closingIdx + tickCount
                continue
            }
        }

        // 4. Inline image ![alt](url) or ![alt][ref]
        if (ch == '!' && idx + 1 < n && text[idx + 1] == '[') {
            val imgResult = tryParseBracketLink(text, idx + 1, referenceLinks)
            if (imgResult != null) {
                val alt = imgResult.label.ifBlank { "图片" }
                pushLinkSpan(
                    target = imgResult.destination,
                    palette = palette,
                    onNavigateLink = onNavigateLink
                )
                append("[$alt]")
                pop()
                idx = imgResult.endIndex
                continue
            }
        }

        // 5. WikiLink [[target]] or [[target|display]]
        if (ch == '[' && idx + 1 < n && text[idx + 1] == '[') {
            val closeWiki = text.indexOf("]]", idx + 2)
            if (closeWiki != -1 && !text.substring(idx + 2, closeWiki).contains('\n')) {
                val inner = text.substring(idx + 2, closeWiki).trim()
                if (inner.isNotEmpty()) {
                    val pipeIdx = inner.indexOf('|')
                    val target = if (pipeIdx != -1) inner.substring(0, pipeIdx).trim() else inner
                    val display = if (pipeIdx != -1) inner.substring(pipeIdx + 1).trim() else target.substringAfterLast('/')
                    pushLinkSpan(
                        target = "wikilink:$target",
                        palette = palette,
                        onNavigateLink = onNavigateLink
                    )
                    append(display.ifEmpty { target })
                    pop()
                    idx = closeWiki + 2
                    continue
                }
            }
        }

        // 6. Standard / Reference Link [label](url "title") or [label][ref] or [label][] or [ref]
        if (ch == '[') {
            // Footnote reference [^id]
            if (idx + 1 < n && text[idx + 1] == '^') {
                val closeBracket = text.indexOf(']', idx + 2)
                if (closeBracket != -1) {
                    val fnId = text.substring(idx + 2, closeBracket).trim()
                    if (fnId.isNotEmpty() && !fnId.contains(' ') && !fnId.contains('\n')) {
                        pushStyle(
                            SpanStyle(
                                color = palette.linkColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                baselineShift = BaselineShift.Superscript
                            )
                        )
                        append("[$fnId]")
                        pop()
                        idx = closeBracket + 1
                        continue
                    }
                }
            }

            val linkMatch = tryParseBracketLink(text, idx, referenceLinks)
            if (linkMatch != null) {
                pushLinkSpan(
                    target = linkMatch.destination,
                    palette = palette,
                    onNavigateLink = onNavigateLink
                )
                // If label is an embedded badge image ![alt](img), display alt nicely instead of nested badge link
                val badgeImg = Regex("^!\\[([^\\]]*)]\\(([^)]+)\\)$").matchEntire(linkMatch.label.trim())
                if (badgeImg != null) {
                    append(badgeImg.groupValues[1].ifBlank { linkMatch.destination })
                } else {
                    appendInlineSegment(
                        text = linkMatch.label,
                        palette = palette,
                        referenceLinks = referenceLinks,
                        onNavigateLink = onNavigateLink
                    )
                }
                pop()
                idx = linkMatch.endIndex
                continue
            }
        }

        // 7. Angle-bracket autolinks & inline HTML tags (<https://...>, <a href=...>, <br>, <b>, etc.)
        if (ch == '<') {
            // <br> / <br/>
            val brMatch = HTML_BR_REGEX.find(text.substring(idx))
            if (brMatch != null) {
                append('\n')
                idx += brMatch.value.length
                continue
            }

            // <a href="...">label</a>
            val sub = text.substring(idx)
            val anchorMatch = HTML_ANCHOR_REGEX.find(sub)
            if (anchorMatch != null) {
                val href = (anchorMatch.groupValues[1]
                    .ifEmpty { anchorMatch.groupValues[2] }
                    .ifEmpty { anchorMatch.groupValues[3] }).trim()
                val label = anchorMatch.groupValues[4]
                if (href.isNotEmpty()) {
                    pushLinkSpan(
                        target = href,
                        palette = palette,
                        onNavigateLink = onNavigateLink
                    )
                    appendInlineSegment(label, palette, referenceLinks, onNavigateLink)
                    pop()
                } else {
                    appendInlineSegment(label, palette, referenceLinks, onNavigateLink)
                }
                idx += anchorMatch.value.length
                continue
            }

            // <img src="..." alt="...">
            val htmlImgMatch = HTML_IMG_REGEX.find(sub)
            if (htmlImgMatch != null) {
                val src = (htmlImgMatch.groupValues[1].ifEmpty { htmlImgMatch.groupValues[2] }).trim()
                val alt = Regex("""\balt\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
                    .find(htmlImgMatch.value)
                    ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                    ?.takeIf { it.isNotBlank() } ?: "图片"
                if (src.isNotEmpty()) {
                    pushLinkSpan(src, palette, onNavigateLink)
                    append("[$alt]")
                    pop()
                }
                idx += htmlImgMatch.value.length
                continue
            }

            // Styled HTML inline tags: <b>, <strong>, <i>, <em>, <s>, <del>, <u>, <code>, <mark>, <sup>, <sub>
            val htmlStyled = tryParseStyledHtmlTag(sub)
            if (htmlStyled != null) {
                pushStyle(htmlStyled.style(palette))
                if (htmlStyled.isCode) {
                    append(htmlStyled.innerText)
                } else {
                    appendInlineSegment(htmlStyled.innerText, palette, referenceLinks, onNavigateLink)
                }
                pop()
                idx += htmlStyled.consumedLength
                continue
            }

            // Angle autolink <https://...> or <user@example.com>
            val closeAngle = text.indexOf('>', idx + 1)
            if (closeAngle != -1) {
                val inside = text.substring(idx + 1, closeAngle)
                if (!inside.contains(' ') && !inside.contains('\n') && !inside.contains('<')) {
                    when {
                        inside.startsWith("http://", ignoreCase = true) ||
                            inside.startsWith("https://", ignoreCase = true) ||
                            inside.startsWith("mailto:", ignoreCase = true) ||
                            inside.startsWith("tel:", ignoreCase = true) -> {
                            pushLinkSpan(inside, palette, onNavigateLink)
                            append(inside.removePrefix("mailto:"))
                            pop()
                            idx = closeAngle + 1
                            continue
                        }
                        BARE_EMAIL_REGEX.matches(inside) -> {
                            pushLinkSpan("mailto:$inside", palette, onNavigateLink)
                            append(inside)
                            pop()
                            idx = closeAngle + 1
                            continue
                        }
                    }
                }
            }
        }

        // 8. Bare URL autolink (https://..., http://..., www....) and bare email
        if (isAutolinkBoundary(text, idx)) {
            val sub = text.substring(idx)
            if (sub.startsWith("http://", ignoreCase = true) ||
                sub.startsWith("https://", ignoreCase = true) ||
                sub.startsWith("www.", ignoreCase = true)
            ) {
                val urlMatch = BARE_URL_REGEX.find(sub)
                if (urlMatch != null) {
                    val cleanUrl = trimTrailingUrlPunctuation(urlMatch.value)
                    if (cleanUrl.length > 7) {
                        val targetUrl = if (cleanUrl.startsWith("www.", ignoreCase = true)) {
                            "https://$cleanUrl"
                        } else {
                            cleanUrl
                        }
                        pushLinkSpan(targetUrl, palette, onNavigateLink)
                        append(cleanUrl)
                        pop()
                        idx += cleanUrl.length
                        continue
                    }
                }
            } else if (ch.isLetterOrDigit()) {
                val emailMatch = BARE_EMAIL_REGEX.find(sub)
                if (emailMatch != null) {
                    val email = emailMatch.value
                    pushLinkSpan("mailto:$email", palette, onNavigateLink)
                    append(email)
                    pop()
                    idx += email.length
                    continue
                }
            }
        }

        // 9. Bold + Italic (***...*** or ___...___)
        if (idx + 2 < n && ((ch == '*' && text[idx + 1] == '*' && text[idx + 2] == '*') ||
                (ch == '_' && text[idx + 1] == '_' && text[idx + 2] == '_' && isLeftFlankingUnderscore(text, idx)))
        ) {
            val delimiter = text.substring(idx, idx + 3)
            val end = findClosingDelimiter(text, idx + 3, delimiter, requireUnderscoreWordEnd = ch == '_')
            if (end != -1 && end > idx + 3) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                appendInlineSegment(text.substring(idx + 3, end), palette, referenceLinks, onNavigateLink)
                pop()
                idx = end + 3
                continue
            }
        }

        // 10. Bold (**...** or __...__)
        if (idx + 1 < n && ((ch == '*' && text[idx + 1] == '*') ||
                (ch == '_' && text[idx + 1] == '_' && isLeftFlankingUnderscore(text, idx)))
        ) {
            val delimiter = text.substring(idx, idx + 2)
            val end = findClosingDelimiter(text, idx + 2, delimiter, requireUnderscoreWordEnd = ch == '_')
            if (end != -1 && end > idx + 2) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlineSegment(text.substring(idx + 2, end), palette, referenceLinks, onNavigateLink)
                pop()
                idx = end + 2
                continue
            }
        }

        // 11. Strikethrough (~~...~~)
        if (idx + 1 < n && ch == '~' && text[idx + 1] == '~') {
            val end = findClosingDelimiter(text, idx + 2, "~~", requireUnderscoreWordEnd = false)
            if (end != -1 && end > idx + 2) {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                appendInlineSegment(text.substring(idx + 2, end), palette, referenceLinks, onNavigateLink)
                pop()
                idx = end + 2
                continue
            }
        }

        // 12. Highlight (==...==)
        if (idx + 1 < n && ch == '=' && text[idx + 1] == '=') {
            val end = findClosingDelimiter(text, idx + 2, "==", requireUnderscoreWordEnd = false)
            if (end != -1 && end > idx + 2) {
                pushStyle(SpanStyle(background = palette.highlightBackground))
                appendInlineSegment(text.substring(idx + 2, end), palette, referenceLinks, onNavigateLink)
                pop()
                idx = end + 2
                continue
            }
        }

        // 13. Italic (*...* or _..._)
        if (ch == '*' || (ch == '_' && isLeftFlankingUnderscore(text, idx))) {
            val delimiter = ch.toString()
            val end = findClosingDelimiter(text, idx + 1, delimiter, requireUnderscoreWordEnd = ch == '_')
            if (end != -1 && end > idx + 1) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInlineSegment(text.substring(idx + 1, end), palette, referenceLinks, onNavigateLink)
                pop()
                idx = end + 1
                continue
            }
        }

        append(ch)
        idx++
    }
}

private fun AnnotatedString.Builder.pushLinkSpan(
    target: String,
    palette: MarkdownInlinePalette,
    onNavigateLink: (String) -> Unit
) {
    val styles = TextLinkStyles(
        style = SpanStyle(
            color = palette.linkColor,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium
        ),
        pressedStyle = SpanStyle(
            color = palette.linkColor,
            background = palette.linkColor.copy(alpha = 0.14f),
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium
        )
    )
    pushLink(
        LinkAnnotation.Clickable(
            tag = target,
            styles = styles,
            linkInteractionListener = { onNavigateLink(target) }
        )
    )
}

private data class BracketLinkMatch(
    val label: String,
    val destination: String,
    val endIndex: Int
)

private fun tryParseBracketLink(
    text: String,
    openBracketIndex: Int,
    referenceLinks: Map<String, ReferenceLinkDef>
): BracketLinkMatch? {
    val closeBracket = findMatchingCloseBracket(text, openBracketIndex) ?: return null
    val label = text.substring(openBracketIndex + 1, closeBracket)
    val n = text.length

    // Case A: Inline link [label](url "title")
    if (closeBracket + 1 < n && text[closeBracket + 1] == '(') {
        val closeParen = findMatchingCloseParenForLink(text, closeBracket + 1)
        if (closeParen != -1) {
            val rawInner = text.substring(closeBracket + 2, closeParen)
            val destAndTitle = parseLinkDestinationAndTitle(rawInner)
            if (destAndTitle != null && destAndTitle.first.isNotEmpty()) {
                return BracketLinkMatch(
                    label = label,
                    destination = destAndTitle.first,
                    endIndex = closeParen + 1
                )
            }
        }
    }

    // Case B: Full or collapsed reference link [label][ref] or [label][]
    if (closeBracket + 1 < n && text[closeBracket + 1] == '[') {
        val closeRefBracket = findMatchingCloseBracket(text, closeBracket + 1)
        if (closeRefBracket != null) {
            val rawRef = text.substring(closeBracket + 2, closeRefBracket)
            val key = normalizeRefId(rawRef.ifBlank { label })
            val def = referenceLinks[key]
            if (def != null) {
                return BracketLinkMatch(
                    label = label,
                    destination = def.url,
                    endIndex = closeRefBracket + 1
                )
            }
        }
    }

    // Case C: Shortcut reference link [label]
    if (referenceLinks.isNotEmpty() && label.isNotBlank()) {
        val def = referenceLinks[normalizeRefId(label)]
        if (def != null) {
            return BracketLinkMatch(
                label = label,
                destination = def.url,
                endIndex = closeBracket + 1
            )
        }
    }

    return null
}

private fun findMatchingCloseBracket(text: String, openIdx: Int): Int? {
    var depth = 0
    var i = openIdx
    val n = text.length
    while (i < n) {
        val ch = text[i]
        if (ch == '\\' && i + 1 < n) {
            i += 2
            continue
        }
        if (ch == '`') {
            val ticks = countRun(text, i, '`')
            val end = findClosingBackticks(text, i + ticks, ticks)
            if (end != -1) {
                i = end + ticks
                continue
            }
        }
        if (ch == '[') {
            depth++
        } else if (ch == ']') {
            depth--
            if (depth == 0) return i
        }
        i++
    }
    return null
}

private fun findMatchingCloseParenForLink(text: String, openParenIdx: Int): Int {
    var depth = 0
    var i = openParenIdx
    var inQuote: Char? = null
    val n = text.length
    while (i < n) {
        val ch = text[i]
        if (ch == '\\' && i + 1 < n) {
            i += 2
            continue
        }
        if (inQuote != null) {
            if (ch == inQuote) inQuote = null
            i++
            continue
        }
        if (ch == '"' || ch == '\'') {
            inQuote = ch
            i++
            continue
        }
        if (ch == '(') {
            depth++
        } else if (ch == ')') {
            depth--
            if (depth == 0) return i
        } else if (ch == '\n' && i > openParenIdx && text[i - 1] == '\n') {
            return -1
        }
        i++
    }
    return -1
}

internal fun parseLinkDestinationAndTitle(rawInner: String): Pair<String, String?>? {
    val trimmed = rawInner.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith('<')) {
        val closeAngle = trimmed.indexOf('>', 1)
        if (closeAngle == -1) return null
        val url = trimmed.substring(1, closeAngle).trim()
        val rest = trimmed.substring(closeAngle + 1).trim()
        val title = parseOptionalTitle(rest)
        return url to title
    }

    var parenDepth = 0
    var i = 0
    while (i < trimmed.length) {
        val ch = trimmed[i]
        if (ch == '\\' && i + 1 < trimmed.length) {
            i += 2
            continue
        }
        if (ch == '(') {
            parenDepth++
        } else if (ch == ')') {
            if (parenDepth > 0) parenDepth-- else break
        } else if (ch.isWhitespace()) {
            break
        }
        i++
    }

    val url = trimmed.substring(0, i).trim()
    val rest = trimmed.substring(i).trim()
    val title = if (rest.isEmpty()) null else parseOptionalTitle(rest)
    if (rest.isNotEmpty() && title == null) {
        // Still allow unquoted spaces in malformed paths? Only reject if clearly invalid title syntax, else keep url
        return url to null
    }
    return url to title
}

private fun parseOptionalTitle(rest: String): String? {
    if (rest.length < 2) return null
    val first = rest.first()
    val last = rest.last()
    val validPair = (first == '"' && last == '"') ||
        (first == '\'' && last == '\'') ||
        (first == '(' && last == ')')
    return if (validPair) rest.substring(1, rest.length - 1) else null
}

private data class HtmlStyledMatch(
    val innerText: String,
    val consumedLength: Int,
    val isCode: Boolean = false,
    val style: (MarkdownInlinePalette) -> SpanStyle
)

private val STYLED_HTML_TAG_REGEX = Regex(
    """^<(strong|b|em|i|del|s|u|code|mark|sup|sub)\b[^>]*>(.*?)</\1>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private fun tryParseStyledHtmlTag(sub: String): HtmlStyledMatch? {
    val match = STYLED_HTML_TAG_REGEX.find(sub) ?: return null
    val tag = match.groupValues[1].lowercase()
    val inner = match.groupValues[2]
    val len = match.value.length
    return when (tag) {
        "strong", "b" -> HtmlStyledMatch(inner, len) { SpanStyle(fontWeight = FontWeight.Bold) }
        "em", "i" -> HtmlStyledMatch(inner, len) { SpanStyle(fontStyle = FontStyle.Italic) }
        "del", "s" -> HtmlStyledMatch(inner, len) { SpanStyle(textDecoration = TextDecoration.LineThrough) }
        "u" -> HtmlStyledMatch(inner, len) { SpanStyle(textDecoration = TextDecoration.Underline) }
        "code" -> HtmlStyledMatch(inner, len, isCode = true) { palette ->
            SpanStyle(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 13.5.sp,
                background = palette.codeBackground
            )
        }
        "mark" -> HtmlStyledMatch(inner, len) { palette ->
            SpanStyle(background = palette.highlightBackground)
        }
        "sup" -> HtmlStyledMatch(inner, len) {
            SpanStyle(fontSize = 11.sp, baselineShift = BaselineShift.Superscript)
        }
        "sub" -> HtmlStyledMatch(inner, len) {
            SpanStyle(fontSize = 11.sp, baselineShift = BaselineShift.Subscript)
        }
        else -> null
    }
}

private fun countRun(text: String, startIndex: Int, targetChar: Char): Int {
    var count = 0
    while (startIndex + count < text.length && text[startIndex + count] == targetChar) {
        count++
    }
    return count
}

private fun findClosingBackticks(text: String, searchFrom: Int, tickCount: Int): Int {
    var i = searchFrom
    val n = text.length
    while (i < n) {
        if (text[i] == '`') {
            val run = countRun(text, i, '`')
            if (run == tickCount) return i
            i += run
        } else {
            i++
        }
    }
    return -1
}

private fun findClosingDelimiter(
    text: String,
    searchFrom: Int,
    delimiter: String,
    requireUnderscoreWordEnd: Boolean
): Int {
    var i = searchFrom
    val n = text.length
    val dLen = delimiter.length
    while (i <= n - dLen) {
        val ch = text[i]
        if (ch == '\\' && i + 1 < n) {
            i += 2
            continue
        }
        if (ch == '`') {
            val ticks = countRun(text, i, '`')
            val endCode = findClosingBackticks(text, i + ticks, ticks)
            if (endCode != -1) {
                i = endCode + ticks
                continue
            }
        }
        if (text.startsWith(delimiter, i)) {
            val prevChar = text[i - 1]
            if (!prevChar.isWhitespace()) {
                if (!requireUnderscoreWordEnd || isRightFlankingUnderscore(text, i + dLen - 1)) {
                    return i
                }
            }
        }
        i++
    }
    return -1
}

private fun isLeftFlankingUnderscore(text: String, idx: Int): Boolean {
    if (idx == 0) return true
    val prev = text[idx - 1]
    return !prev.isLetterOrDigit()
}

private fun isRightFlankingUnderscore(text: String, endIdx: Int): Boolean {
    if (endIdx + 1 >= text.length) return true
    val next = text[endIdx + 1]
    return !next.isLetterOrDigit()
}

private fun isAutolinkBoundary(text: String, idx: Int): Boolean {
    if (idx == 0) return true
    val prev = text[idx - 1]
    return prev.isWhitespace() || prev in setOf('(', '[', '{', '"', '\'', '：', '（', '【', '《', '「', '、', ',', ';')
}

internal fun trimTrailingUrlPunctuation(rawUrl: String): String {
    var end = rawUrl.length
    while (end > 0) {
        val last = rawUrl[end - 1]
        when (last) {
            '.', ',', ';', ':', '!', '?', '\'', '"', '。', '，', '；', '：', '！', '？', '、', '）', '】', '》', '」' -> {
                end--
            }
            ')' -> {
                val slice = rawUrl.substring(0, end)
                val openCount = slice.count { it == '(' }
                val closeCount = slice.count { it == ')' }
                if (closeCount > openCount) {
                    end--
                } else {
                    break
                }
            }
            ']' -> {
                val slice = rawUrl.substring(0, end)
                val openCount = slice.count { it == '[' }
                val closeCount = slice.count { it == ']' }
                if (closeCount > openCount) {
                    end--
                } else {
                    break
                }
            }
            else -> break
        }
    }
    return rawUrl.substring(0, end)
}

internal fun classifyLinkAction(
    rawTarget: String,
    currentNotePath: String,
    availableFilePaths: List<String>
): ParsedLinkAction {
    val trimmed = rawTarget.trim()
    if (trimmed.startsWith("wikilink:")) {
        val wikiTarget = trimmed.removePrefix("wikilink:")
        val resolved = resolveWikiLink(wikiTarget, currentNotePath, availableFilePaths)
        return ParsedLinkAction(ParsedLinkTargetType.INTERNAL_NOTE, resolved ?: wikiTarget)
    }
    if (trimmed.startsWith("#")) {
        return ParsedLinkAction(ParsedLinkTargetType.ANCHOR_ONLY, trimmed)
    }
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("mailto:", ignoreCase = true) ||
        trimmed.startsWith("tel:", ignoreCase = true)
    ) {
        return ParsedLinkAction(ParsedLinkTargetType.EXTERNAL_URL, trimmed)
    }
    if (trimmed.startsWith("www.", ignoreCase = true)) {
        return ParsedLinkAction(ParsedLinkTargetType.EXTERNAL_URL, "https://$trimmed")
    }
    // Relative path inside the GitHub notes repository
    val pathWithoutAnchor = trimmed.substringBefore('#').substringBefore('?')
    val resolvedRepoPath = resolveRepoRelativePath(currentNotePath, pathWithoutAnchor)
    return ParsedLinkAction(ParsedLinkTargetType.INTERNAL_NOTE, resolvedRepoPath)
}

internal fun resolveRepoRelativePath(currentNotePath: String, relativeTarget: String): String {
    val cleanTarget = relativeTarget.trim().replace('\\', '/')
    if (cleanTarget.isEmpty()) return currentNotePath
    val baseSegments = if (cleanTarget.startsWith('/')) {
        mutableListOf()
    } else {
        val parentDir = if ('/' in currentNotePath) currentNotePath.substringBeforeLast('/') else ""
        if (parentDir.isEmpty()) mutableListOf() else parentDir.split('/').toMutableList()
    }
    for (seg in cleanTarget.trimStart('/').split('/')) {
        when (seg) {
            "", "." -> Unit
            ".." -> if (baseSegments.isNotEmpty()) baseSegments.removeAt(baseSegments.lastIndex)
            else -> baseSegments.add(seg)
        }
    }
    return baseSegments.joinToString("/")
}

internal fun resolveWikiLink(
    wikiTarget: String,
    currentNotePath: String,
    availableFilePaths: List<String>
): String? {
    val cleaned = wikiTarget.substringBefore('#').trim()
    if (cleaned.isEmpty()) return null
    val candidates = if (cleaned.endsWith(".md", ignoreCase = true)) {
        listOf(cleaned)
    } else {
        listOf("$cleaned.md", "$cleaned.markdown", cleaned)
    }

    // 1. Exact path match or relative to current folder
    for (candidate in candidates) {
        val rel = resolveRepoRelativePath(currentNotePath, candidate)
        availableFilePaths.firstOrNull { it.equals(rel, ignoreCase = true) }?.let { return it }
        availableFilePaths.firstOrNull { it.equals(candidate.trimStart('/'), ignoreCase = true) }?.let { return it }
    }

    // 2. Match by file name anywhere in repository
    for (candidate in candidates) {
        val fileName = candidate.substringAfterLast('/')
        availableFilePaths.firstOrNull { path ->
            path.substringAfterLast('/').equals(fileName, ignoreCase = true)
        }?.let { return it }
    }

    return null
}
