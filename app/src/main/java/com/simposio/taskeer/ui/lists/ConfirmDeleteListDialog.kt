package com.simposio.taskeer.ui.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.DomainError

@Composable
fun ConfirmDeleteListDialog(
    dialog: ListDialog.ConfirmDelete,
    error: DomainError?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_delete_title, dialog.name)) },
        text = {
            Column {
                if (dialog.taskCount > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.list_delete_message,
                            dialog.taskCount,
                            dialog.taskCount,
                            dialog.targetName,
                        ),
                    )
                } else {
                    Text(stringResource(R.string.list_delete_message_empty))
                }
                error?.let {
                    Text(text = it.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.list_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) } },
    )
}
