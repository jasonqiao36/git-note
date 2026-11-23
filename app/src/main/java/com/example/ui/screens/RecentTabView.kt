package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.RecentFileEntry
import com.example.data.model.RecentFilter
import com.example.ui.theme.LocalPaperColors
import java.util.Calendar

internal enum class RecentGroupType {
    PINNED,
    TODAY,
    YESTERDAY,
    LAST_7_DAYS,
    EARLIER
}

internal data class RecentGroupSection(
    val type: RecentGroupType,
    val entries: List<RecentFileEntry>
)

internal fun groupRecentFiles(
    entries: List<RecentFileEntry>,
    filter: RecentFilter,
    nowMs: Long = System.currentTimeMillis()
): List<RecentGroupSection> {
    val filtered = when (filter) {
        RecentFilter.ALL -> entries
        RecentFilter.EDITED -> entries.filter { it.wasEdited }
    }.sortedByDescending { it.lastOpenedAt }

    if (filtered.isEmpty()) return emptyList()

    val pinned = filtered.filter { it.isPinned }
    val unpinned = filtered.filterNot { it.isPinned }

    val startOfToday = startOfDayMillis(nowMs, daysOffset = 0)
    val startOfYesterday = startOfDayMillis(nowMs, daysOffset = -1)
    val startOfWeek = startOfDayMillis(nowMs, daysOffset = -6)

    val todayList = mutableListOf<RecentFileEntry>()
    val yesterdayList = mutableListOf<RecentFileEntry>()
    val weekList = mutableListOf<RecentFileEntry>()
    val earlierList = mutableListOf<RecentFileEntry>()

    for (entry in unpinned) {
        when {
            entry.lastOpenedAt >= startOfToday -> todayList.add(entry)
            entry.lastOpenedAt >= startOfYesterday -> yesterdayList.add(entry)
            entry.lastOpenedAt >= startOfWeek -> weekList.add(entry)
            else -> earlierList.add(entry)
        }
    }

    return buildList {
        if (pinned.isNotEmpty()) add(RecentGroupSection(RecentGroupType.PINNED, pinned))
        if (todayList.isNotEmpty()) add(RecentGroupSection(RecentGroupType.TODAY, todayList))
        if (yesterdayList.isNotEmpty()) add(RecentGroupSection(RecentGroupType.YESTERDAY, yesterdayList))
        if (weekList.isNotEmpty()) add(RecentGroupSection(RecentGroupType.LAST_7_DAYS, weekList))
        if (earlierList.isNotEmpty()) add(RecentGroupSection(RecentGroupType.EARLIER, earlierList))
    }
}

private fun startOfDayMillis(nowMs: Long, daysOffset: Int): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (daysOffset != 0) {
        cal.add(Calendar.DAY_OF_YEAR, daysOffset)
    }
    return cal.timeInMillis
}

@Composable
internal fun RecentTabView(
    recentFiles: List<RecentFileEntry>,
    filter: RecentFilter,
    activeNotePath: String?,
    onFilterChange: (RecentFilter) -> Unit,
    onOpenFile: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val editedCount = remember(recentFiles) { recentFiles.count { it.wasEdited } }
    val sections = remember(recentFiles, filter) {
        groupRecentFiles(recentFiles, filter)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Filter row (All / Edited only)
        if (recentFiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filter == RecentFilter.ALL,
                    onClick = { onFilterChange(RecentFilter.ALL) },
                    label = {
                        Text(
                            text = "${stringResource(R.string.browser_recent_filter_all)} (${recentFiles.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primaryContainer,
                        selectedLabelColor = colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("recent_filter_all")
                )
                FilterChip(
                    selected = filter == RecentFilter.EDITED,
                    onClick = { onFilterChange(RecentFilter.EDITED) },
                    label = {
                        Text(
                            text = "${stringResource(R.string.browser_recent_filter_edited)} ($editedCount)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primaryContainer,
                        selectedLabelColor = colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("recent_filter_edited")
                )
            }
        }

        if (sections.isEmpty()) {
            RecentEmptyState(
                isFilteredEmpty = recentFiles.isNotEmpty() && filter == RecentFilter.EDITED,
                modifier = Modifier.fillMaxSize()
            )
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("browser_recent_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.forEach { section ->
                item(key = "recent_header_${section.type.name}") {
                    RecentGroupHeader(
                        type = section.type,
                        count = section.entries.size
                    )
                }
                items(
                    items = section.entries,
                    key = { "recent_card_${section.type.name}_${it.path}" }
                ) { entry ->
                    RecentNoteCard(
                        entry = entry,
                        isSelected = entry.path == activeNotePath,
                        onClick = { onOpenFile(entry.path) },
                        onTogglePin = { onTogglePin(entry.path) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentGroupHeader(
    type: RecentGroupType,
    count: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    val title = when (type) {
        RecentGroupType.PINNED -> stringResource(R.string.browser_recent_group_pinned)
        RecentGroupType.TODAY -> stringResource(R.string.browser_recent_group_today)
        RecentGroupType.YESTERDAY -> stringResource(R.string.browser_recent_group_yesterday)
        RecentGroupType.LAST_7_DAYS -> stringResource(R.string.browser_recent_group_week)
        RecentGroupType.EARLIER -> stringResource(R.string.browser_recent_group_earlier)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (type == RecentGroupType.PINNED) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (type == RecentGroupType.PINNED) colorScheme.primary else colorScheme.onSurfaceVariant
        )
        Text(
            text = "· $count",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentNoteCard(
    entry: RecentFileEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val paperColors = LocalPaperColors.current

    Surface(
        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("recent_tab_item_${entry.path}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) colorScheme.primary.copy(alpha = 0.14f)
                        else colorScheme.surfaceContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = if (isSelected || entry.isPinned) colorScheme.primary else colorScheme.secondary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = entry.fileName.removeSuffix(".md"),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Folder path pill
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colorScheme.surfaceContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = entry.parentDir.ifEmpty { stringResource(R.string.browser_root) },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = formatRecentRelativeTime(entry.lastOpenedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    if (entry.wasEdited) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(paperColors.badgeEditedBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.recent_badge_edited),
                                style = MaterialTheme.typography.labelSmall,
                                color = paperColors.badgeEditedText
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.testTag("recent_pin_button_${entry.path}")
            ) {
                Icon(
                    imageVector = if (entry.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(
                        if (entry.isPinned) R.string.browser_recent_unpin else R.string.browser_recent_pin
                    ),
                    tint = if (entry.isPinned) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentEmptyState(
    isFilteredEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = stringResource(
                    if (isFilteredEmpty) R.string.browser_recent_empty_edited
                    else R.string.browser_recent_empty_title
                ),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface
            )
            if (!isFilteredEmpty) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.browser_recent_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatRecentRelativeTime(timestampMs: Long): String {
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
        else -> "较早"
    }
}
