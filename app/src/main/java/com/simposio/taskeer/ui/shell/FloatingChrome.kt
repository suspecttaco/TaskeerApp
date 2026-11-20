package com.simposio.taskeer.ui.shell

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.HomeButton
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.navigation.Chrome
import com.simposio.taskeer.ui.navigation.TopBarKind
import com.simposio.taskeer.ui.navigation.panelEnter
import com.simposio.taskeer.ui.navigation.panelExit
import com.simposio.taskeer.ui.quickadd.QuickAddUiState
import com.simposio.taskeer.domain.task.QuickDateChoice
import com.simposio.taskeer.domain.task.QuickDateShortcut
import com.simposio.taskeer.ui.tasks.DueDatePickerDialog
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import com.simposio.taskeer.ui.upcoming.text
import java.time.LocalDate

class PillActions(
    val onOpen: () -> Unit,
    val onTitleChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onClearDate: () -> Unit,
    val onShortcut: (QuickDateShortcut) -> Unit,
    val onPickDate: () -> Unit,
    val onDatePicked: (LocalDate) -> Unit,
    val onDismissDatePicker: () -> Unit,
    val onToggleListPanel: () -> Unit,
    val onSelectList: (String) -> Unit,
)

// Un solo inset de teclado, por fuera: el contenedor abierto queda pegado al teclado y, por dentro, su navigationBarsPadding()
// deja que el fondo llegue al borde físico. El efecto de foco vive aquí (no en la píldora) porque el cierre puede
// coincidir con un destino sin cromo, que saldría antes de la composición.
@Composable
fun BoxScope.FloatingChrome(
    chrome: Chrome,
    pill: QuickAddUiState,
    pillActions: PillActions,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inputFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(pill.isOpen) {
        if (pill.isOpen) {
            inputFocus.requestFocus()
            wasOpen = true
        } else if (wasOpen) {
            keyboardController?.hide()
            focusManager.clearFocus()
            wasOpen = false
        }
    }
    LaunchedEffect(pill.isListPanelOpen, pill.isDatePickerOpen) {
        if (pill.isOpen && !pill.isListPanelOpen && !pill.isDatePickerOpen) inputFocus.requestFocus()
    }
    if (!chrome.showHomeButton && !chrome.showPill) return
    Box(modifier = modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
        if (chrome.showPill && pill.isOpen) {
            ExpandedPill(state = pill, actions = pillActions, inputFocus = inputFocus)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Spacing.ScreenPadding),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (chrome.showHomeButton) HomeButton(onClick = onHome)
                Spacer(Modifier.weight(1f))
                if (chrome.showPill) CollapsedPill(onOpen = pillActions.onOpen)
            }
        }
    }
}

@Composable
private fun CollapsedPill(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.pill_description)
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = Spacing.FloatingElevation,
        modifier = modifier
            .heightIn(min = Spacing.FloatingControlSize)
            .widthIn(min = Spacing.PillCollapsedMinWidth)
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
}

@Composable
private fun ExpandedPill(
    state: QuickAddUiState,
    actions: PillActions,
    inputFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val animated = rememberAnimationsEnabled()
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = Spacing.FloatingElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.ItemGap),
        ) {
            state.error?.let { error ->
                Text(
                    text = error.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.InlineGap),
                )
            }
            AnimatedVisibility(
                visible = state.isListPanelOpen,
                enter = panelEnter(animated),
                exit = panelExit(animated),
            ) {
                QuickAddListPanel(
                    options = state.listOptions,
                    onSelect = actions.onSelectList,
                    modifier = Modifier.padding(bottom = Spacing.ItemGap),
                )
            }
            QuickDateShortcutRow(
                choice = state.dateChoice,
                onShortcut = actions.onShortcut,
                onPickDate = actions.onPickDate,
            )
            if (state.dateChoice != QuickDateChoice.None) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.dueLabel?.let { stringResource(R.string.pill_date_chip, it.text()) }
                            ?: stringResource(R.string.pill_date_chip_someday),
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
                    shape = RectangleShape,
                    trailingIcon = {
                        PillListButton(
                            listName = state.selectedListName.orEmpty(),
                            panelOpen = state.isListPanelOpen,
                            onClick = actions.onToggleListPanel,
                        )
                    },
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
    if (state.isDatePickerOpen) {
        DueDatePickerDialog(
            initialDate = state.dueDate,
            onDatePicked = actions.onDatePicked,
            onDismiss = actions.onDismissDatePicker,
        )
    }
}

@Composable
private fun PillListButton(listName: String, panelOpen: Boolean, onClick: () -> Unit) {
    val stateText = stringResource(if (panelOpen) R.string.pill_list_panel_open else R.string.pill_list_panel_closed)
    ActionIcon(
        icon = if (panelOpen) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.AutoMirrored.Outlined.FormatListBulleted,
        contentDescription = stringResource(R.string.pill_list_button, listName),
        onClick = onClick,
        modifier = Modifier.semantics { stateDescription = stateText },
    )
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
                pillActions = PillActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                onHome = {},
            )
        }
    }
}
