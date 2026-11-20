package com.simposio.taskeer.ui.shell

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.quickadd.QuickAddListOption
import com.simposio.taskeer.ui.theme.Spacing

@Composable
internal fun QuickAddListPanel(
    options: List<QuickAddListOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.pill_list_panel_description)
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = Spacing.PillListPanelMaxHeight)) {
            // La clave es el id de la lista: el orden y los contadores cambian en vivo.
            items(options, key = { it.id }) { option ->
                QuickAddListRow(option = option, onSelect = { onSelect(option.id) })
            }
        }
    }
}

@Composable
private fun QuickAddListRow(option: QuickAddListOption, onSelect: () -> Unit) {
    val count = pluralStringResource(R.plurals.pill_list_task_count, option.taskCount, option.taskCount)
    val selectedText = stringResource(R.string.pill_list_selected)
    val rowDescription = stringResource(R.string.pill_list_row_description, option.name, count)
    ListItem(
        headlineContent = {
            Text(
                text = option.name,
                color = if (option.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = { Text(count) },
        trailingContent = if (option.selected) {
            { DecorativeIcon(Icons.Filled.Check, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .selectable(selected = option.selected, role = Role.RadioButton, onClick = onSelect)
            .semantics {
                contentDescription = rowDescription
                if (option.selected) stateDescription = selectedText
            },
    )
}
