package com.simposio.taskeer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun InlineAddField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    addDescription: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        ActionIcon(
            icon = Icons.Filled.Add,
            contentDescription = addDescription,
            onClick = onAdd,
            enabled = enabled,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InlineAddFieldPreview() {
    TaskeerTheme {
        InlineAddField(
            value = "",
            onValueChange = {},
            label = "Nombre",
            addDescription = "Crear",
            onAdd = {},
            enabled = false,
        )
    }
}
