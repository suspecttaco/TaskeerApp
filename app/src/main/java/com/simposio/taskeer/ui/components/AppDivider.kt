package com.simposio.taskeer.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant)
}

@Preview(showBackground = true)
@Composable
private fun AppDividerPreview() {
    TaskeerTheme { AppDivider() }
}
