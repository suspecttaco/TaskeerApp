package com.simposio.taskeer.ui.myday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.EmptyHint
import com.simposio.taskeer.ui.components.InlineError
import com.simposio.taskeer.ui.components.TonalActionIcon
import com.simposio.taskeer.ui.components.rowItemModifier
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsSheet(
    items: List<MySuggestionUi>,
    errorMessage: String?,
    onAdd: (String) -> Unit,
    onDismissItem: (String) -> Unit,
    onClose: () -> Unit,
) {
    val title = stringResource(R.string.my_day_suggestions_sheet_title, items.size)
    val dragDescription = stringResource(R.string.quick_add_drag_handle)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.semantics { contentDescription = dragDescription },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { paneTitle = title },
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                ActionIcon(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.quick_add_close),
                    onClick = onClose,
                )
            }
            errorMessage?.let {
                InlineError(
                    message = it,
                    modifier = Modifier.padding(horizontal = Spacing.ScreenPadding),
                )
            }
            if (items.isEmpty()) {
                EmptyHint(
                    text = stringResource(R.string.my_day_suggestions_sheet_empty),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(Spacing.ScreenPadding * 2),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .navigationBarsPadding(),
                ) {
                    items(items, key = { it.id }) { item ->
                        SuggestionItem(
                            item = item,
                            onAdd = { onAdd(item.id) },
                            onDismiss = { onDismissItem(item.id) },
                            modifier = rowItemModifier(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    item: MySuggestionUi,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val location = item.boardName ?: item.listName ?: stringResource(R.string.inbox_title)
    val supporting = listOfNotNull(location, item.due).joinToString(" · ")
    val addDescription = stringResource(R.string.my_day_suggestion_add_description, item.title)
    val dismissDescription = stringResource(R.string.my_day_suggestion_dismiss_description, item.title)
    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text(item.title) },
            supportingContent = {
                Text(
                    text = supporting,
                    color = if (item.isOverdue) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
                    TonalActionIcon(
                        icon = Icons.Filled.Add,
                        contentDescription = addDescription,
                        onClick = onAdd,
                    )
                    ActionIcon(
                        icon = Icons.Filled.Close,
                        contentDescription = dismissDescription,
                        onClick = onDismiss,
                        modifier = Modifier.sizeIn(
                            minWidth = Spacing.MinTouchTarget,
                            minHeight = Spacing.MinTouchTarget,
                        ),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        HorizontalDivider()
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun SuggestionsSheetPreview() {
    TaskeerTheme {
        SuggestionsSheet(
            items = (1..40).map {
                MySuggestionUi(
                    id = "s$it",
                    title = "Tarea sugerida $it",
                    boardName = if (it % 3 == 0) null else "Proyecto",
                    listName = if (it % 3 == 0) "Compras" else null,
                    due = if (it % 2 == 0) "1 oct 2026, 9:00" else null,
                    isOverdue = it % 2 == 0,
                )
            },
            errorMessage = null,
            onAdd = {},
            onDismissItem = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 480)
@Composable
private fun SuggestionsSheetEmptyPreview() {
    TaskeerTheme {
        SuggestionsSheet(items = emptyList(), errorMessage = null, onAdd = {}, onDismissItem = {}, onClose = {})
    }
}
