package com.simposio.taskeer.ui.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.ui.components.InlineError

@Composable
fun TaskListNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    error: DomainError?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.list_name_label)) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                )
                error?.let {
                    InlineError(
                        message = it.message,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) } },
    )
}
