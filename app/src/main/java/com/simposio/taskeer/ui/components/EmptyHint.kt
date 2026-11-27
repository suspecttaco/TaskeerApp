package com.simposio.taskeer.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier.padding(Spacing.InlineGap),
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun EmptyHintPreview() {
    TaskeerTheme { EmptyHint(text = "Sin elementos") }
}
