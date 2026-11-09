package com.simposio.taskeer.ui.myday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.InlineAddField
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun QuickAddBar(
    state: QuickAddUiState,
    onTitleChange: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
    ) {
        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        InlineAddField(
            value = state.title,
            onValueChange = onTitleChange,
            label = stringResource(R.string.quick_add_label),
            addDescription = stringResource(R.string.quick_add_add),
            onAdd = onAdd,
            enabled = state.title.isNotBlank() && !state.isSaving,
            onDone = onAdd,
        )
        val isInbox = state.destinationLabel == null
        AssistChip(
            onClick = onOpenPicker,
            label = { Text(state.destinationLabel ?: stringResource(R.string.destination_inbox)) },
            leadingIcon = {
                DecorativeIcon(if (isInbox) Icons.Filled.Inbox else Icons.Filled.Dashboard)
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickAddBarPreview() {
    TaskeerTheme {
        QuickAddBar(state = QuickAddUiState(title = "Comprar pan"), onTitleChange = {}, onAdd = {}, onOpenPicker = {})
    }
}
