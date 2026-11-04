package com.simposio.taskeer.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.theme.Spacing

@Composable
internal fun BoardColumnView(
    column: BoardColumnUi,
    draggedId: String?,
    onColumnPositioned: (Bounds) -> Unit,
    onColumnDisposed: () -> Unit,
    onCardPositioned: (String, Bounds) -> Unit,
    onCardDisposed: (String) -> Unit,
    onDragStart: (TaskCardUi, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onCreateTask: () -> Unit,
    onEditTask: (String) -> Unit,
    onToggleMyDay: (String) -> Unit,
) {
    DisposableEffect(column.id) { onDispose(onColumnDisposed) }

    Card(
        modifier = Modifier
            .width(ColumnWidth)
            .fillMaxHeight()
            .onGloballyPositioned { onColumnPositioned(it.boundsInRoot().toBounds()) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.CardPadding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.InlineGap),
            ) {
                Text(
                    text = column.column.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (column.column.isDoneColumn) {
                    val doneDescription = stringResource(R.string.column_done_description)
                    DecorativeIcon(
                        icon = Icons.Filled.CheckCircle,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = Spacing.InlineGap)
                            .semantics { contentDescription = doneDescription },
                    )
                }
                Spacer(Modifier.weight(1f))
                wipCounterLabel(column.cards.size, column.column.wipLimit)?.let { label ->
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
            ) {
                if (column.cards.isEmpty()) {
                    Text(
                        text = stringResource(R.string.board_column_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                column.cards.forEach { card ->
                    DisposableEffect(card.id) { onDispose { onCardDisposed(card.id) } }
                    TaskCard(
                        card = card,
                        onToggleMyDay = onToggleMyDay,
                        modifier = Modifier
                            .alpha(if (card.id == draggedId) 0.3f else 1f)
                            .onGloballyPositioned { onCardPositioned(card.id, it.boundsInRoot().toBounds()) }
                            .pointerInput(card.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart(card, it) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        onDrag(amount)
                                    },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                )
                            }
                            .clickable { onEditTask(card.id) },
                    )
                }
            }
            TextButton(onClick = onCreateTask) {
                DecorativeIcon(Icons.Filled.Add)
                Spacer(Modifier.width(Spacing.InlineGap))
                Text(stringResource(R.string.task_add))
            }
        }
    }
}
