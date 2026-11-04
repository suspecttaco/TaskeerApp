package com.simposio.taskeer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        if (icon != null) {
            DecorativeIcon(icon = icon, tint = MaterialTheme.colorScheme.primary)
        }
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    TaskeerTheme { SectionHeader(text = "Tema", icon = Icons.Filled.Palette) }
}
