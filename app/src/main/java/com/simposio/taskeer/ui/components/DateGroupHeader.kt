package com.simposio.taskeer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

// `expanded` nulo: cabecera fija (sin plegar), como los grupos de «Todas las tareas».
@Composable
internal fun DateGroupHeader(
    title: String,
    addDescription: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    expanded: Boolean? = null,
    onToggle: () -> Unit = {},
) {
    val stateText = expanded?.let {
        stringResource(if (it) R.string.group_expanded else R.string.group_collapsed)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleModifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
        Row(
            modifier = if (expanded == null) {
                titleModifier.semantics { heading() }
            } else {
                titleModifier
                    .clickable(onClick = onToggle)
                    .semantics {
                        heading()
                        role = Role.Button
                        stateDescription = stateText.orEmpty()
                    }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.InlineGap),
                )
            }
            if (expanded != null) {
                DecorativeIcon(
                    icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    modifier = Modifier
                        .padding(start = Spacing.InlineGap)
                        .size(Spacing.ActionIconSize),
                )
            }
        }
        ActionIcon(icon = Icons.Filled.Add, contentDescription = addDescription, onClick = onAdd)
    }
}

@Preview(showBackground = true)
@Composable
private fun DateGroupHeaderPreview() {
    TaskeerTheme {
        DateGroupHeader(title = "Hoy", addDescription = "Añadir tarea a Hoy", onAdd = {}, expanded = true)
    }
}
