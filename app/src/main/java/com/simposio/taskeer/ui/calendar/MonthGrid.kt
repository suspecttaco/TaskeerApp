package com.simposio.taskeer.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.simposio.taskeer.domain.calendar.DayMarks
import com.simposio.taskeer.domain.calendar.weekdayHeader
import com.simposio.taskeer.ui.theme.Emphasis
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.boldIf
import java.time.LocalDate

private const val MAX_MARKS = 3

// A medida: el DatePicker de Material 3 no admite marcas por día ni selección sin diálogo, y esta rejilla
// necesita ambas cosas. Cada celda sigue siendo una Surface nativa con objetivo táctil de 48 dp.
@Composable
fun MonthGrid(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenPadding)) {
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            weekdayHeader(state.firstDay).forEach { day ->
                Text(
                    text = weekdayInitial(day),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        state.grid.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCell(cell = cell, onClick = { onSelectDate(cell.date) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: GridCell, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = cellDescription(cell.date, cell.marks, cell.isToday)
    val textColor = if (cell.inMonth) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = Emphasis.OutOfMonth)
    }
    Surface(
        selected = cell.isAnchor,
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (cell.isAnchor) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (cell.isToday) BorderStroke(Spacing.ItemGap / 2, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .heightIn(min = Spacing.MinTouchTarget)
            .padding(Spacing.ItemGap / 2)
            .semantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouchTarget),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.boldIf(cell.isToday),
                color = textColor,
            )
            Marks(cell.marks)
        }
    }
}

// Evento = punto lleno; tarea = anillo. La forma distingue el tipo sin depender del color.
@Composable
private fun Marks(marks: DayMarks) {
    val events = minOf(marks.events, MAX_MARKS)
    val tasks = minOf(marks.tasks, MAX_MARKS - events)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.ItemGap / 2),
        modifier = Modifier.heightIn(min = Spacing.DotSize),
    ) {
        repeat(events) {
            Box(
                Modifier
                    .size(Spacing.DotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        repeat(tasks) {
            Box(
                Modifier
                    .size(Spacing.DotSize)
                    .border(Spacing.ItemGap / 2, MaterialTheme.colorScheme.tertiary, CircleShape),
            )
        }
    }
}
