package com.simposio.taskeer.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.HomeButton
import com.simposio.taskeer.ui.navigation.Chrome
import com.simposio.taskeer.ui.navigation.TopBarKind
import com.simposio.taskeer.ui.quickadd.QuickAddUiState
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import com.simposio.taskeer.ui.upcoming.text

class PillActions(
    val onOpen: () -> Unit,
    val onTitleChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onClearDate: () -> Unit,
)

// Insets propios: el cromo flota sobre el NavHost, que ya aplica los suyos, y debe quedar sobre la barra del sistema y el IME.
@Composable
fun BoxScope.FloatingChrome(
    chrome: Chrome,
    pill: QuickAddUiState,
    pillActions: PillActions,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!chrome.showHomeButton && !chrome.showPill) return
    Row(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(Spacing.ScreenPadding),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (chrome.showHomeButton && !pill.isOpen) HomeButton(onClick = onHome)
        if (!pill.isOpen) Spacer(Modifier.weight(1f))
        if (chrome.showPill) {
            QuickAddPill(
                state = pill,
                actions = pillActions,
                modifier = if (pill.isOpen) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

@Composable
private fun QuickAddPill(state: QuickAddUiState, actions: PillActions, modifier: Modifier = Modifier) {
    val inputFocus = remember { FocusRequester() }
    val pillFocus = remember { FocusRequester() }
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.isOpen) {
        if (state.isOpen) {
            inputFocus.requestFocus()
            wasOpen = true
        } else if (wasOpen) {
            pillFocus.requestFocus()
            wasOpen = false
        }
    }
    if (!state.isOpen) {
        val description = stringResource(R.string.pill_description)
        Surface(
            onClick = actions.onOpen,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = Spacing.FloatingElevation,
            modifier = modifier
                .heightIn(min = Spacing.FloatingControlSize)
                .widthIn(min = Spacing.PillCollapsedMinWidth)
                .focusRequester(pillFocus)
                .semantics { contentDescription = description },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap, Alignment.CenterHorizontally),
            ) {
                DecorativeIcon(Icons.Filled.Add)
                Text(text = stringResource(R.string.pill_hint), style = MaterialTheme.typography.titleSmall)
            }
        }
        return
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = Spacing.FloatingElevation,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.InlineGap, vertical = Spacing.ItemGap)) {
            state.error?.let { error ->
                Text(
                    text = error.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.InlineGap),
                )
            }
            state.dueLabel?.let { label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.pill_date_chip, label.text()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Spacing.InlineGap),
                    )
                    ActionIcon(
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.pill_remove_date),
                        onClick = actions.onClearDate,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = actions.onTitleChange,
                    label = { Text(stringResource(R.string.pill_input_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { actions.onSend() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(inputFocus),
                )
                ActionIcon(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.pill_send),
                    onClick = actions.onSend,
                    enabled = state.canSubmit,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickAddPillCollapsedPreview() {
    TaskeerTheme {
        Box {
            FloatingChrome(
                chrome = Chrome(
                    topBar = TopBarKind.SECTION,
                    showHomeButton = true,
                    showPill = true,
                ),
                pill = QuickAddUiState(),
                pillActions = PillActions({}, {}, {}, {}),
                onHome = {},
            )
        }
    }
}
