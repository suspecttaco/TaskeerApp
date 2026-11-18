package com.simposio.taskeer.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun CalendarPlaceholderScreen(modifier: Modifier = Modifier) {
    val comingSoon = stringResource(R.string.coming_soon)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap, Alignment.CenterVertically),
    ) {
        DecorativeIcon(
            icon = Icons.Filled.CalendarMonth,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = Spacing.EmptyIconSize,
        )
        Text(text = stringResource(R.string.section_calendar), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.calendar_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(stringResource(R.string.wip_label)) },
            modifier = Modifier.semantics { contentDescription = comingSoon },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarPlaceholderPreview() {
    TaskeerTheme { CalendarPlaceholderScreen() }
}
