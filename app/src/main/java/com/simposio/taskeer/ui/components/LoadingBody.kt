package com.simposio.taskeer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun LoadingBody(
    description: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    contentAlignment: Alignment = Alignment.Center,
) {
    Box(modifier = modifier, contentAlignment = contentAlignment) {
        CircularProgressIndicator(
            modifier = Modifier.semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
}

@Composable
internal fun InlineProgress(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun LoadingBodyPreview() {
    TaskeerTheme { LoadingBody(description = "Cargando") }
}
