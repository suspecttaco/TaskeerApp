package com.simposio.taskeer.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simposio.taskeer.ui.theme.Spacing
import androidx.compose.ui.window.Dialog
import com.simposio.taskeer.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// Los milisegundos del selector están en UTC: leerlos en la zona del usuario movería el día al cruzar medianoche.
@OptIn(ExperimentalMaterial3Api::class)
private fun DatePickerState.selectedUtcDate(): LocalDate? = selectedDateMillis?.let { millis ->
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}

private fun LocalDate?.toPickerMillis(): Long? = this?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueDatePickerDialog(
    initialDate: LocalDate?,
    onDatePicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.toPickerMillis())

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedUtcDate()?.let(onDatePicked) },
            ) {
                Text(stringResource(R.string.task_due_at_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.task_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueTimePickerDialog(
    initialTime: LocalTime,
    onTimePicked: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
            ) {
                Text(
                    text = stringResource(R.string.task_due_at_time_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                TimePicker(state = state)
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.task_cancel))
                    }
                    TextButton(
                        onClick = { onTimePicked(LocalTime.of(state.hour, state.minute)) },
                    ) {
                        Text(stringResource(R.string.task_due_at_accept))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueDatePickerContent(
    initialDate: LocalDate?,
    onDatePicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.toPickerMillis())

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
        DatePicker(state = state, showModeToggle = false)
        Button(
            onClick = { state.selectedUtcDate()?.let(onDatePicked) },
            enabled = state.selectedDateMillis != null,
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
        ) {
            Text(stringResource(R.string.task_due_at_accept))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueTimePickerContent(
    initialTime: LocalTime,
    onTimePicked: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        TimePicker(state = state)
        Button(
            onClick = { onTimePicked(LocalTime.of(state.hour, state.minute)) },
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
        ) {
            Text(stringResource(R.string.quick_add_time_accept))
        }
    }
}
