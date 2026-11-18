package com.simposio.taskeer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun HomeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(Spacing.FloatingControlSize),
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = stringResource(R.string.home_button_description),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeButtonPreview() {
    TaskeerTheme { HomeButton(onClick = {}) }
}
