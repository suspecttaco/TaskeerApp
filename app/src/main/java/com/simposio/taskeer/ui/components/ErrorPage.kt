package com.simposio.taskeer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun ErrorPage(
    kind: ErrorPageKind,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = errorPageSpecFor(kind)
    StatePage(
        icon = iconFor(kind),
        title = stringResource(spec.titleRes),
        message = stringResource(spec.messageRes),
        modifier = modifier,
        action = StatePageAction(stringResource(spec.actionRes), onAction),
    )
}

private fun iconFor(kind: ErrorPageKind): ImageVector = when (kind) {
    ErrorPageKind.NOT_FOUND -> Icons.Filled.SearchOff
    ErrorPageKind.NO_PERMISSION -> Icons.Filled.Lock
    ErrorPageKind.UNEXPECTED -> Icons.Filled.ErrorOutline
    ErrorPageKind.SERVICE_UNAVAILABLE -> Icons.Filled.CloudOff
    ErrorPageKind.SESSION_EXPIRED -> Icons.Filled.LockClock
    ErrorPageKind.OFFLINE -> Icons.Filled.WifiOff
}

private class ErrorPageKindProvider : PreviewParameterProvider<ErrorPageKind> {
    override val values: Sequence<ErrorPageKind> = ErrorPageKind.entries.asSequence()
}

@Preview(showBackground = true)
@Composable
private fun ErrorPagePreview(@PreviewParameter(ErrorPageKindProvider::class) kind: ErrorPageKind) {
    TaskeerTheme { ErrorPage(kind = kind, onAction = {}) }
}
