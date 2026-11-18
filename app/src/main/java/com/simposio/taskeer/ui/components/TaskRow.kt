package com.simposio.taskeer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
internal fun TaskRow(
    row: TaskRowUi,
    actions: RowActions,
    onAction: (RowAction) -> Unit,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    primaryAction: RowAction? = null,
    peek: Boolean = false,
    onPeekDone: () -> Unit = {},
) {
    val currentActions by rememberUpdatedState(actions)
    val currentOnAction by rememberUpdatedState(onAction)
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val peekOffset = remember { Animatable(0f) }
    val peekPx = with(LocalDensity.current) { Spacing.SwipePeek.toPx() }
    LaunchedEffect(peek) {
        if (peek) {
            peekOffset.animateTo(peekPx, tween(PEEK_HALF_MILLIS))
            peekOffset.animateTo(0f, tween(PEEK_HALF_MILLIS))
            onPeekDone()
        }
    }

    val labels = actions.sheet.map { stringResource(it.labelRes()) }
    Column(modifier = modifier) {
        SwipeToDismissBox(
            state = state,
            enableDismissFromStartToEnd = actions.swipeEnd != null,
            enableDismissFromEndToStart = actions.swipeStart != null,
            onDismiss = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> currentActions.swipeEnd?.let(currentOnAction)
                    SwipeToDismissBoxValue.EndToStart -> currentActions.swipeStart?.let(currentOnAction)
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                // La lista se actualiza por el flujo del repositorio: la fila vuelve a su sitio.
                scope.launch { state.reset() }
            },
            backgroundContent = {
                val direction = state.dismissDirection
                val peeking = direction == SwipeToDismissBoxValue.Settled && peekOffset.value > 0f
                val action = when {
                    direction == SwipeToDismissBoxValue.StartToEnd || peeking -> actions.swipeEnd
                    direction == SwipeToDismissBoxValue.EndToStart -> actions.swipeStart
                    else -> null
                }
                if (action != null) {
                    SwipeBackground(action = action, atStart = direction != SwipeToDismissBoxValue.EndToStart)
                }
            },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.offset { IntOffset(peekOffset.value.roundToInt(), 0) },
            ) {
                RowContent(
                    row = row,
                    primaryAction = primaryAction,
                    onAction = onAction,
                    onOpen = onOpen,
                    onMore = onMore,
                    modifier = Modifier.semantics {
                        customActions = actions.sheet.mapIndexed { index, action ->
                            CustomAccessibilityAction(labels[index]) {
                                onAction(action)
                                true
                            }
                        }
                    },
                )
            }
        }
        AppDivider()
    }
}

@Composable
private fun SwipeBackground(action: RowAction, atStart: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(action.swipeContainerColor())
            .padding(horizontal = Spacing.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(
            Spacing.InlineGap,
            if (atStart) Alignment.Start else Alignment.End,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DecorativeIcon(icon = action.icon())
        Text(text = stringResource(action.swipeLabelRes()), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RowContent(
    row: TaskRowUi,
    primaryAction: RowAction?,
    onAction: (RowAction) -> Unit,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val moreLabel = stringResource(R.string.row_more)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onMore, onLongClickLabel = moreLabel)
            .padding(horizontal = Spacing.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompleteCheckbox(
            completed = row.isCompleted,
            onToggle = { onAction(if (row.isCompleted) RowAction.REOPEN else RowAction.COMPLETE) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Spacing.RowVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (row.isCompleted) TextDecoration.LineThrough else null,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            SecondLine(row)
        }
        if (primaryAction != null) {
            TextButton(onClick = { onAction(primaryAction) }) {
                DecorativeIcon(icon = primaryAction.icon())
                Text(
                    text = stringResource(primaryAction.labelRes()),
                    modifier = Modifier.padding(start = Spacing.ItemGap),
                )
            }
        }
        ActionIcon(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.row_more),
            onClick = onMore,
        )
    }
}

@Composable
private fun SecondLine(row: TaskRowUi) {
    val due = row.due
    val board = if (row.showBoard) row.boardName ?: stringResource(R.string.inbox_title) else null
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelMedium
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (due != null) {
            val overdueDescription = stringResource(R.string.task_overdue_description, due)
            Text(
                text = due,
                style = style,
                color = if (row.isOverdue) MaterialTheme.colorScheme.error else muted,
                modifier = if (row.isOverdue) {
                    Modifier.semantics { contentDescription = overdueDescription }
                } else {
                    Modifier
                },
            )
        }
        if (due != null && board != null) Text(text = SEPARATOR, style = style, color = muted)
        if (board != null) {
            Text(
                text = board,
                style = style,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (due != null || board != null) Text(text = SEPARATOR, style = style, color = muted)
        PriorityDot(row.priority)
        if (row.showMyDayStar) {
            DecorativeIcon(icon = Icons.Filled.Star, tint = MaterialTheme.colorScheme.tertiary)
        }
    }
}

private const val SEPARATOR = "·"
private const val TITLE_MAX_LINES = 2
private const val PEEK_HALF_MILLIS = 300

@Preview(showBackground = true)
@Composable
private fun TaskRowPreview() {
    TaskeerTheme {
        val row = TaskRowUi(
            id = "t1",
            title = "Entregar la memoria",
            isCompleted = false,
            priority = TaskPriority.URGENT,
            due = "1 oct 2026, 9:00",
            boardName = "Proyecto",
            showBoard = true,
            isOverdue = true,
            isMarked = true,
            isAssigned = false,
            isInbox = false,
            isRepeating = false,
            showMyDayStar = true,
        )
        TaskRow(
            row = row,
            actions = row.actionsIn(RowContext.MY_DAY),
            onAction = {},
            onOpen = {},
            onMore = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompletedTaskRowPreview() {
    TaskeerTheme {
        val row = TaskRowUi(
            id = "t2",
            title = "Comprar pan",
            isCompleted = true,
            priority = TaskPriority.LOW,
            due = null,
            boardName = null,
            showBoard = true,
            isOverdue = false,
            isMarked = true,
            isAssigned = false,
            isInbox = true,
            isRepeating = false,
            showMyDayStar = false,
        )
        TaskRow(
            row = row,
            actions = row.actionsIn(RowContext.MY_DAY),
            onAction = {},
            onOpen = {},
            onMore = {},
        )
    }
}
