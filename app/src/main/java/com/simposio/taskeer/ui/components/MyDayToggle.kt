package com.simposio.taskeer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun MyDayToggle(
    inMyDay: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionIcon(
        icon = if (inMyDay) Icons.Filled.Star else Icons.Filled.StarBorder,
        contentDescription = stringResource(
            if (inMyDay) R.string.task_in_my_day_remove else R.string.task_in_my_day_add,
        ),
        onClick = onToggle,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.tertiary,
    )
}

@Preview(showBackground = true)
@Composable
private fun MyDayTogglePreview() {
    TaskeerTheme { MyDayToggle(inMyDay = true, onToggle = {}) }
}
