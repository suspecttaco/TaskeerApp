package com.simposio.taskeer.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.task.QuickDateChoice
import com.simposio.taskeer.domain.task.QuickDateShortcut
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.theme.Spacing

@Composable
internal fun QuickDateShortcutRow(
    choice: QuickDateChoice,
    onShortcut: (QuickDateShortcut) -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.pill_shortcuts_description)
    LazyRow(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
        contentPadding = PaddingValues(horizontal = Spacing.InlineGap),
    ) {
        items(SHORTCUTS, key = { it.first }) { (shortcut, label) ->
            val selected = choice == QuickDateChoice.Shortcut(shortcut)
            FilterChip(
                selected = selected,
                onClick = { onShortcut(shortcut) },
                label = { Text(stringResource(label)) },
                leadingIcon = if (selected) {
                    { DecorativeIcon(Icons.Filled.Check) }
                } else {
                    null
                },
            )
        }
        item {
            FilterChip(
                selected = choice is QuickDateChoice.Picked,
                onClick = onPickDate,
                label = { Text(stringResource(R.string.pill_shortcut_pick)) },
                leadingIcon = { DecorativeIcon(Icons.Filled.CalendarMonth) },
            )
        }
    }
}

private val SHORTCUTS = listOf(
    QuickDateShortcut.TOMORROW_MORNING to R.string.pill_shortcut_tomorrow_morning,
    QuickDateShortcut.NEXT_WEEK to R.string.pill_shortcut_next_week,
    QuickDateShortcut.SOMEDAY to R.string.pill_shortcut_someday,
)
