package com.simposio.taskeer.ui.components

import androidx.compose.material3.Checkbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun CompleteCheckbox(
    completed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        if (completed) R.string.task_mark_pending else R.string.task_complete,
    )
    Checkbox(
        checked = completed,
        onCheckedChange = { onToggle() },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = description },
    )
}

@Preview(showBackground = true)
@Composable
private fun CompleteCheckboxPreview() {
    TaskeerTheme { CompleteCheckbox(completed = true, onToggle = {}) }
}
