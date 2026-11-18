package com.simposio.taskeer.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.navigation.MenuAction
import com.simposio.taskeer.ui.navigation.MenuItem
import com.simposio.taskeer.ui.navigation.OverflowMenu
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun OverflowMenuButton(
    menu: OverflowMenu,
    clearEnabled: Boolean,
    onItem: (MenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        ActionIcon(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.menu_more),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (menu.actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.InlineGap),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
                ) {
                    menu.actions.forEach { action ->
                        ActionIcon(
                            icon = action.icon(),
                            contentDescription = stringResource(action.descriptionRes()),
                            onClick = {},
                            enabled = false,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.menu_not_synced_wip),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.ItemGap),
                )
            }
            menu.items.forEach { item ->
                val label = stringResource(item.labelRes())
                val placeholder = item.isPlaceholder()
                DropdownMenuItem(
                    text = { Text(if (placeholder) stringResource(R.string.wip_suffix, label) else label) },
                    enabled = !placeholder && (item != MenuItem.CLEAR_COMPLETED || clearEnabled),
                    onClick = {
                        expanded = false
                        onItem(item)
                    },
                )
            }
        }
    }
}

private fun MenuItem.isPlaceholder(): Boolean =
    this == MenuItem.MY_CALENDARS || this == MenuItem.FILTER || this == MenuItem.SORT

private fun MenuAction.icon(): ImageVector = when (this) {
    MenuAction.SYNC -> Icons.Filled.Sync
    MenuAction.PRINT -> Icons.Filled.Print
    MenuAction.SHARE -> Icons.Filled.Share
}

@StringRes
private fun MenuAction.descriptionRes(): Int = when (this) {
    MenuAction.SYNC -> R.string.menu_sync
    MenuAction.PRINT -> R.string.menu_print
    MenuAction.SHARE -> R.string.menu_share
}

@StringRes
private fun MenuItem.labelRes(): Int = when (this) {
    MenuItem.CLEAR_COMPLETED -> R.string.menu_clear_completed
    MenuItem.MY_CALENDARS -> R.string.menu_my_calendars
    MenuItem.FILTER -> R.string.menu_filter
    MenuItem.SORT -> R.string.menu_sort
    MenuItem.SETTINGS -> R.string.menu_settings
    MenuItem.RENAME_LIST -> R.string.menu_rename_list
    MenuItem.DELETE_LIST -> R.string.menu_delete_list
    MenuItem.RENAME_BOARD -> R.string.menu_rename_board
    MenuItem.DELETE_BOARD -> R.string.menu_delete_board
}

@Preview(showBackground = true)
@Composable
private fun OverflowMenuButtonPreview() {
    TaskeerTheme {
        OverflowMenuButton(
            menu = OverflowMenu(
                actions = MenuAction.entries,
                items = listOf(MenuItem.CLEAR_COMPLETED, MenuItem.FILTER, MenuItem.SETTINGS),
            ),
            clearEnabled = true,
            onItem = {},
        )
    }
}
