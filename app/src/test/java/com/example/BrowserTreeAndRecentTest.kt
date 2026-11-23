package com.example

import com.example.data.model.RecentFileEntry
import com.example.data.model.RecentFilter
import com.example.data.model.RepoTreeNode
import com.example.ui.screens.RecentGroupType
import com.example.ui.screens.ancestorDirectoriesOf
import com.example.ui.screens.buildVisibleTreeRows
import com.example.ui.screens.groupRecentFiles
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class BrowserTreeAndRecentTest {

    @Test
    fun ancestorDirectoriesComputesFullPathChain() {
        assertEquals(
            setOf("work", "work/2026", "work/2026/q4"),
            ancestorDirectoriesOf("work/2026/q4")
        )
        assertEquals(emptySet<String>(), ancestorDirectoriesOf(""))
    }

    @Test
    fun buildVisibleTreeRowsRespectsExpansionAndCountsDescendants() {
        val nodes = listOf(
            RepoTreeNode("work", "work", "", isDirectory = true),
            RepoTreeNode("2026", "work/2026", "work", isDirectory = true),
            RepoTreeNode("plan.md", "work/2026/plan.md", "work/2026", isDirectory = false),
            RepoTreeNode(".gitkeep", "work/2026/.gitkeep", "work/2026", isDirectory = false),
            RepoTreeNode("notes.md", "work/notes.md", "work", isDirectory = false),
            RepoTreeNode("readme.md", "readme.md", "", isDirectory = false)
        )

        // Collapsed root: only top-level directory and root file visible
        val collapsed = buildVisibleTreeRows(nodes, emptySet())
        assertEquals(listOf("work", "readme.md"), collapsed.map { it.node.path })
        assertEquals(2, collapsed.first().descendantFileCount)

        // Expand work + work/2026: all levels visible with proper depths
        val expanded = buildVisibleTreeRows(nodes, setOf("work", "work/2026"))
        assertEquals(
            listOf("work" to 0, "work/2026" to 1, "work/2026/plan.md" to 2, "work/notes.md" to 1, "readme.md" to 0),
            expanded.map { it.node.path to it.depth }
        )
    }

    @Test
    fun groupRecentFilesSeparatesPinnedAndTimeBucketsAndFiltersEdited() {
        val noonToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val entries = listOf(
            RecentFileEntry("pinned.md", noonToday - 10 * 86_400_000L, wasEdited = true, isPinned = true),
            RecentFileEntry("today.md", noonToday - 60_000L, wasEdited = false),
            RecentFileEntry("yesterday.md", noonToday - 24 * 3_600_000L, wasEdited = true),
            RecentFileEntry("earlier.md", noonToday - 14 * 86_400_000L, wasEdited = false)
        )

        val allGroups = groupRecentFiles(entries, RecentFilter.ALL, nowMs = noonToday)
        assertEquals(
            listOf(
                RecentGroupType.PINNED,
                RecentGroupType.TODAY,
                RecentGroupType.YESTERDAY,
                RecentGroupType.EARLIER
            ),
            allGroups.map { it.type }
        )

        val editedGroups = groupRecentFiles(entries, RecentFilter.EDITED, nowMs = noonToday)
        assertEquals(
            listOf(RecentGroupType.PINNED, RecentGroupType.YESTERDAY),
            editedGroups.map { it.type }
        )
    }
}
