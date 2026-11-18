package com.simposio.taskeer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun QuickAddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.fab_new_task)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        DecorativeIcon(icon = Icons.Filled.Add, size = Spacing.ActionIconSize)
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickAddFabPreview() {
    TaskeerTheme { QuickAddFab(onClick = {}) }
}
