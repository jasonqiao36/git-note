package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.markdown.CalloutType
import com.example.ui.markdown.MarkdownInlinePalette
import com.example.ui.markdown.MdBlock
import com.example.ui.markdown.ParsedLinkTargetType
import com.example.ui.markdown.classifyLinkAction
import com.example.ui.markdown.parseInlineMarkdown
import com.example.ui.markdown.parseMarkdownDocument
import com.example.ui.markdown.resolveRepoRelativePath
import com.example.ui.markdown.resolveWikiLink
import com.example.ui.markdown.trimTrailingUrlPunctuation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    private val palette = MarkdownInlinePalette(
        codeBackground = Color(0xFFEEEEEE),
        linkColor = Color(0xFF1565C0),
        highlightBackground = Color(0xFFFFF59D)
    )

    private fun extractLinkTags(annotated: androidx.compose.ui.text.AnnotatedString): List<Pair<String, String>> {
        return annotated.getLinkAnnotations(0, annotated.length).map { range ->
            val label = annotated.text.substring(range.start, range.end)
            val tag = (range.item as LinkAnnotation.Clickable).tag
            label to tag
        }
    }

    @Test
    fun parsesStandardLinksWithParensAndTitles() {
        val input = "查看 [Kotlin 维基](https://en.wikipedia.org/wiki/Kotlin_(programming_language) \"标题\") 和 [空格链接](<https://example.com/a b>)"
        val annotated = parseInlineMarkdown(input, palette)
        val links = extractLinkTags(annotated)

        assertEquals(2, links.size)
        assertEquals("Kotlin 维基" to "https://en.wikipedia.org/wiki/Kotlin_(programming_language)", links[0])
        assertEquals("空格链接" to "https://example.com/a b", links[1])
    }

    @Test
    fun parsesBareUrlsAndAngleBracketAutolinksAndStripsTrailingPunctuation() {
        val input = "官网：https://github.com/open/repo，镜像（https://example.com/path_(1)）。邮箱 <dev@example.com> 和 www.kotlinlang.org！"
        val annotated = parseInlineMarkdown(input, palette)
        val links = extractLinkTags(annotated)

        assertEquals(4, links.size)
        assertEquals("https://github.com/open/repo" to "https://github.com/open/repo", links[0])
        assertEquals("https://example.com/path_(1)" to "https://example.com/path_(1)", links[1])
        assertEquals("dev@example.com" to "mailto:dev@example.com", links[2])
        assertEquals("www.kotlinlang.org" to "https://www.kotlinlang.org", links[3])
    }

    @Test
    fun parsesNestedLinksInsideBoldAndFormattedLinkLabels() {
        val input = "**加粗中的[链接](https://a.com)** 与 [**粗体** `代码`](https://b.com)"
        val annotated = parseInlineMarkdown(input, palette)
        val links = extractLinkTags(annotated)

        assertEquals(2, links.size)
        assertEquals("链接" to "https://a.com", links[0])
        assertEquals("粗体 代码" to "https://b.com", links[1])
        assertTrue(annotated.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun parsesReferenceLinksAndHtmlAnchors() {
        val doc = parseMarkdownDocument(
            """
            请看 [参考文档][docs]、[简写][]、[GitHub]，以及 <a href="https://example.org/html">HTML 链接</a>。

            [docs]: https://kotlinlang.org/docs/home.html "Docs"
            [简写]: <https://developer.android.com>
            [github]: https://github.com
            """.trimIndent()
        )
        val para = doc.blocks.single() as MdBlock.Paragraph
        val annotated = parseInlineMarkdown(para.text, palette, doc.referenceLinks)
        val links = extractLinkTags(annotated)

        assertEquals(4, links.size)
        assertEquals("参考文档" to "https://kotlinlang.org/docs/home.html", links[0])
        assertEquals("简写" to "https://developer.android.com", links[1])
        assertEquals("GitHub" to "https://github.com", links[2])
        assertEquals("HTML 链接" to "https://example.org/html", links[3])
    }

    @Test
    fun parsesBadgeLinksAndWikiLinksAndInlineEscapes() {
        val input = "[![Build](https://img.shields.io/badge/ci-pass.svg)](https://github.com/org/repo/actions) · [[architecture/design|架构设计]] · \\*不斜体\\*"
        val annotated = parseInlineMarkdown(input, palette)
        val links = extractLinkTags(annotated)

        assertEquals(2, links.size)
        assertEquals("Build" to "https://github.com/org/repo/actions", links[0])
        assertEquals("架构设计" to "wikilink:architecture/design", links[1])
        assertTrue(annotated.text.contains("*不斜体*"))
        assertFalse(annotated.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun protectsSnakeCaseIdentifiersFromFalseItalics() {
        val annotated = parseInlineMarkdown("变量 foo_bar_baz 不应斜体，而 _强调文本_ 应斜体", palette)
        assertEquals("变量 foo_bar_baz 不应斜体，而 强调文本 应斜体", annotated.text)
        val italicSpans = annotated.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, italicSpans.size)
        assertEquals("强调文本", annotated.text.substring(italicSpans[0].start, italicSpans[0].end))
    }

    @Test
    fun parsesFrontmatterCalloutsDetailsSetextAndKeepsAccurateTaskLineIndices() {
        val md = """
            ---
            title: 架构笔记
            tags:
              - Android
              - Markdown
            ---
            主标题
            ===

            > [!TIP] 最佳实践
            > - 支持嵌套列表
            > - 支持 **引用内格式**

            - [ ] 第一个待办

            <details>
            <summary>点击展开代码</summary>

            ~~~kotlin
            println("hello")
            ~~~

            </details>

            正文脚注[^note1]

            [^note1]: 这是脚注说明
        """.trimIndent()

        val doc = parseMarkdownDocument(md)
        assertEquals(listOf("title" to "架构笔记", "tags" to "Android, Markdown"), doc.frontmatter)
        assertEquals(1, doc.footnotes.size)
        assertEquals("note1" to "这是脚注说明", doc.footnotes[0])

        val heading = doc.blocks[0] as MdBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("主标题", heading.text)

        val callout = doc.blocks[1] as MdBlock.BlockQuote
        assertEquals(CalloutType.TIP, callout.calloutType)
        assertEquals("最佳实践", callout.calloutTitle)
        assertEquals(2, callout.blocks.size)

        val task = doc.blocks[2] as MdBlock.TaskItem
        val rawLines = md.split("\n")
        assertEquals("- [ ] 第一个待办", rawLines[task.lineIndex])

        val details = doc.blocks[3] as MdBlock.DetailsBlock
        assertEquals("点击展开代码", details.summary)
        val nestedCode = details.blocks.single() as MdBlock.CodeBlock
        assertEquals("kotlin", nestedCode.language)
        assertEquals("println(\"hello\")", nestedCode.code)
    }

    @Test
    fun resolvesRelativeRepoLinksAndWikiLinks() {
        assertEquals(
            "docs/guides/install.md",
            resolveRepoRelativePath("docs/readme.md", "./guides/install.md")
        )
        assertEquals(
            "README.md",
            resolveRepoRelativePath("docs/sub/note.md", "../../README.md")
        )
        val repoFiles = listOf("README.md", "docs/design.md", "journal/2026-11.md")
        assertEquals(
            "docs/design.md",
            resolveWikiLink("design", "journal/2026-11.md", repoFiles)
        )
        val action = classifyLinkAction("../README.md#intro", "docs/design.md", repoFiles)
        assertEquals(ParsedLinkTargetType.INTERNAL_NOTE, action.type)
        assertEquals("README.md", action.target)

        assertEquals(
            "https://example.com/a_(b)",
            trimTrailingUrlPunctuation("https://example.com/a_(b))。")
        )
    }
}
