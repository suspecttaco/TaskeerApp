package com.simposio.taskeer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import kotlin.math.roundToInt

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
    // confirmValueChange devuelve false: la caja nunca se asienta en «descartada». Con un valor descartado
    // guardado (rememberSaveable), una fila borrada y luego restaurada con la misma clave reaparecería
    // deslizada y volvería a ejecutar la acción.
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentActions.swipeEnd?.let(currentOnAction)
                SwipeToDismissBoxValue.EndToStart -> currentActions.swipeStart?.let(currentOnAction)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    val peekOffset = remember { Animatable(0f) }
    val peekPx = with(LocalDensity.current) { Spacing.SwipePeek.toPx() }
    LaunchedEffect(peek) {
        if (peek && actions.swipeStart != null) {
            peekOffset.animateTo(-peekPx, tween(PEEK_HALF_MILLIS))
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
            backgroundContent = {
                val direction = state.dismissDirection
                val peeking = direction == SwipeToDismissBoxValue.Settled && peekOffset.value < 0f
                val action = when {
                    direction == SwipeToDismissBoxValue.StartToEnd -> actions.swipeEnd
                    direction == SwipeToDismissBoxValue.EndToStart || peeking -> actions.swipeStart
                    else -> null
                }
                if (action != null) {
                    SwipeBackground(
                        action = action,
                        atStart = direction == SwipeToDismissBoxValue.StartToEnd,
                    )
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
    val content = action.swipeContentColor()
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
        DecorativeIcon(icon = action.icon(), tint = content)
        Text(
            text = stringResource(action.swipeLabelRes()),
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
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
            CompletableTitle(title = row.title, isCompleted = row.isCompleted)
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
private fun CompletableTitle(title: String, isCompleted: Boolean, animate: Boolean = rememberAnimationsEnabled()) {
    val progress = remember { Animatable(if (isCompleted) 1f else 0f) }
    LaunchedEffect(isCompleted) {
        when {
            !isCompleted -> progress.snapTo(0f)
            animate -> progress.animateTo(1f, tween(CompletionMotion.StrikeMillis))
            else -> progress.snapTo(1f)
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (isCompleted) CompletionMotion.DimAlpha else 1f,
        animationSpec = if (animate) tween(CompletionMotion.StrikeMillis) else snap(),
        label = "titleAlpha",
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        maxLines = TITLE_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            drawContent()
            val result = layout ?: return@drawWithContent
            if (progress.value <= 0f) return@drawWithContent
            for (line in 0 until result.lineCount) {
                val left = result.getLineLeft(line)
                val right = result.getLineRight(line)
                val y = (result.getLineTop(line) + result.getLineBottom(line)) / 2f
                drawLine(
                    color = color,
                    start = Offset(left, y),
                    end = Offset(left + (right - left) * progress.value, y),
                    strokeWidth = STRIKE_STROKE_DP.dp.toPx(),
                )
            }
        },
    )
}

@Composable
private fun SecondLine(row: TaskRowUi) {
    val due = row.due
    val board = if (row.showBoard) row.boardName ?: row.listName ?: stringResource(R.string.inbox_title) else null
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
private const val STRIKE_STROKE_DP = 1.5f

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
