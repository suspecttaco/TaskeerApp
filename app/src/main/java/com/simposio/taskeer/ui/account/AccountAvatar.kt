package com.simposio.taskeer.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.theme.AvatarPalette
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

// A medida: Material 3 no trae avatar y los chips no son círculos con iniciales. La descripción la pone quien lo envuelve.
@Composable
internal fun AccountAvatar(
    initials: String?,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = Spacing.AvatarSize,
) {
    val large = size == Spacing.AvatarSizeLarge
    if (initials == null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            DecorativeIcon(
                icon = Icons.Filled.AccountCircle,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = size,
            )
        }
        return
    }
    val colors = AvatarPalette.forIndex(colorIndex)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colors.containerArgb))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color(colors.contentArgb),
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountAvatarPreview() {
    TaskeerTheme {
        AccountAvatar(initials = "AL", colorIndex = 3)
    }
}
