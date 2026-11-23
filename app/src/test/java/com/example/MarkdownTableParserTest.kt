package com.example

import com.example.ui.markdown.TableAlignment
import com.example.ui.markdown.isTableStart
import com.example.ui.markdown.parseMarkdownTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class MarkdownTableParserTest {

    @Test
    fun parsesStandardGfmTableWithAlignments() {
        val lines = listOf(
            "| 功能 | 状态 | 进度 |",
            "| :--- | :---: | ---: |",
            "| 表格渲染 | **已完成** | 100% |",
            "| 快捷键 | `WIP` | 50% |"
        )

        val result = parseMarkdownTable(lines, 0)
        assertNotNull(result)
        val table = result!!.table
        assertEquals(4, result.nextIndex)
        assertEquals(listOf("功能", "状态", "进度"), table.headers)
        assertEquals(
            listOf(TableAlignment.Start, TableAlignment.Center, TableAlignment.End),
            table.alignments
        )
        assertEquals(2, table.rows.size)
        assertEquals(listOf("表格渲染", "**已完成**", "100%"), table.rows[0])
        assertEquals(listOf("快捷键", "`WIP`", "50%"), table.rows[1])
    }

    @Test
    fun parsesTableWithoutOuterPipesAndEscapedPipes() {
        val lines = listOf(
            "Command | Example | Notes",
            "--- | --- | ---",
            "Pipe | `a | b` | Use \\| to escape",
            "Short | Only two"
        )

        val result = parseMarkdownTable(lines, 0)
        assertNotNull(result)
        val table = result!!.table
        assertEquals(listOf("Command", "Example", "Notes"), table.headers)
        assertEquals(listOf("Pipe", "`a | b`", "Use | to escape"), table.rows[0])
        assertEquals(listOf("Short", "Only two", ""), table.rows[1])
    }

    @Test
    fun doesNotMistakeParagraphAndHorizontalRuleForTable() {
        val lines = listOf(
            "普通段落文字",
            "---"
        )
        assertFalse(isTableStart(lines, 0))
    }
}
