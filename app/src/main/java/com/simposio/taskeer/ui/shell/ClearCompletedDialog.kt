package com.simposio.taskeer.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.simposio.taskeer.R

@Composable
fun ClearCompletedDialog(
    state: ClearCompletedUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val count = state.confirmCount ?: return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.clear_confirm_title)) },
        text = {
            Column {
                Text(pluralStringResource(R.plurals.clear_confirm_text, count, count))
                state.error?.let {
                    Text(text = it.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.clear_confirm_delete)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.list_cancel)) } },
    )
}
