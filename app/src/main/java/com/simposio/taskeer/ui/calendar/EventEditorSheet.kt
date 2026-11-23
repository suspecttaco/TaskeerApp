package com.simposio.taskeer.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.calendar.EventSpan
import com.simposio.taskeer.domain.calendar.eventEndLocal
import com.simposio.taskeer.domain.calendar.eventStartLocal
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.navigation.editorPanelTransition
import com.simposio.taskeer.ui.permission.CalendarNotice
import com.simposio.taskeer.ui.permission.CalendarPermissionNotice
import com.simposio.taskeer.ui.permission.RefreshOnResume
import com.simposio.taskeer.ui.permission.rememberCalendarPermissionRequest
import com.simposio.taskeer.ui.permission.rememberOpenAppSettings
import com.simposio.taskeer.ui.shell.TrackOpenSheet
import com.simposio.taskeer.ui.tasks.DueDatePickerContent
import com.simposio.taskeer.ui.tasks.DueTimePickerContent
import com.simposio.taskeer.ui.tasks.PanelScaffold
import com.simposio.taskeer.ui.tasks.RadioOption
import com.simposio.taskeer.ui.tasks.requestFocusIfAttached
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.upcoming.weekdayLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class EventEditorActions(
    val onTitleChange: (String) -> Unit,
    val onAllDayChange: (Boolean) -> Unit,
    val onOpenPanel: (EventPanel) -> Unit,
    val onPickStartDate: (LocalDate) -> Unit,
    val onPickStartTime: (LocalTime) -> Unit,
    val onPickEndDate: (LocalDate) -> Unit,
    val onPickEndTime: (LocalTime) -> Unit,
    val onCalendarChosen: (Long) -> Unit,
    val onSave: () -> Unit,
    val onBack: () -> Unit,
    val onRequestClose: () -> Unit,
    val onClosed: () -> Unit,
    val onRefreshAccess: () -> Unit,
    val onPermissionResult: () -> Unit,
)

// El panel nulo es el de permiso de escritura: sustituye al formulario mientras falte el permiso.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(
    state: EventEditorUiState,
    actions: EventEditorActions,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val onClosed = rememberUpdatedState(actions.onClosed)
    val requestPermission = rememberCalendarPermissionRequest(actions.onPermissionResult)
    val openSettings = rememberOpenAppSettings()
    RefreshOnResume(actions.onRefreshAccess)

    val animated = rememberAnimationsEnabled()
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val contentHeight = (windowHeight * Spacing.EditorSheetHeightFraction - Spacing.EditorDragHandleHeight)
        .coerceAtLeast(Spacing.EditorSheetMinHeight)

    LaunchedEffect(state.isClosing) {
        if (state.isClosing) {
            focusManager.clearFocus()
            keyboard?.hide()
            sheetState.hide()
            onClosed.value()
        }
    }

    val title = stringResource(R.string.event_sheet_title)
    val dragDescription = stringResource(R.string.quick_add_drag_handle)

    ModalBottomSheet(
        onDismissRequest = actions.onClosed,
        modifier = modifier,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.semantics { contentDescription = dragDescription },
            )
        },
        // Atrás lo resuelve el BackHandler de abajo, panel a panel; el de la hoja la cerraría de golpe.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        TrackOpenSheet()
        BackHandler(enabled = true) { actions.onBack() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .height(contentHeight)
                .padding(horizontal = Spacing.ScreenPadding)
                .semantics { paneTitle = title },
        ) {
            AnimatedContent(
                targetState = if (state.access.write) state.panel else null,
                transitionSpec = { editorPanelTransition(animated) },
                contentAlignment = Alignment.TopStart,
                label = "eventPanel",
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { panel ->
                Column(Modifier.fillMaxWidth().fillMaxHeight()) {
                    when (panel) {
                        null -> WritePermissionPanel(
                            notice = state.writeNotice ?: CalendarNotice.ASK,
                            onAllow = requestPermission,
                            onOpenSettings = openSettings,
                            onClose = actions.onRequestClose,
                        )
                        EventPanel.Form -> EventForm(state = state, actions = actions)
                        EventPanel.StartDate, EventPanel.StartTime, EventPanel.EndDate, EventPanel.EndTime ->
                            EventPickerPanel(panel = panel, state = state, actions = actions)
                        EventPanel.Calendars -> CalendarsPanel(state = state, actions = actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun WritePermissionPanel(
    notice: CalendarNotice,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ActionIcon(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.quick_add_close),
            onClick = onClose,
        )
        Text(
            text = stringResource(R.string.event_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
    CalendarPermissionNotice(
        notice = notice,
        onAllow = onAllow,
        onOpenSettings = onOpenSettings,
        forWriting = true,
        modifier = Modifier.padding(top = Spacing.SectionGap),
    )
}

@Composable
private fun ColumnScope.EventForm(state: EventEditorUiState, actions: EventEditorActions) {
    val titleFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val zone = ZoneId.of(state.zoneId)
    val draft = state.draft
    val allDay = draft.span is EventSpan.AllDay
    val start = eventStartLocal(draft, zone)
    val end = eventEndLocal(draft, zone)
    val calendar = state.calendars.firstOrNull { it.id == draft.calendarId }
    val validation = state.validation

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ActionIcon(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.quick_add_close),
            onClick = actions.onRequestClose,
        )
        Text(
            text = stringResource(R.string.event_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.InlineGap)
                .semantics { heading() },
        )
        Button(onClick = actions.onSave, enabled = state.canSave, modifier = Modifier.heightIn(min = Spacing.MinTouchTarget)) {
            Text(stringResource(R.string.event_save))
        }
    }
    state.error?.let { error ->
        Text(
            text = error.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(vertical = Spacing.InlineGap)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        val titleCode = validation?.code?.takeIf { it in TITLE_CODES }
        val showTitleError = titleCode != null && (state.titleTouched || titleCode != CODE_TITLE_REQUIRED)
        OutlinedTextField(
            value = draft.title,
            onValueChange = actions.onTitleChange,
            label = { Text(stringResource(R.string.event_title_label)) },
            singleLine = true,
            isError = showTitleError,
            supportingText = validation?.takeIf { titleCode != null }?.let { error ->
                {
                    Text(
                        text = error.message,
                        color = if (showTitleError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
        )
        AllDayRow(checked = allDay, onCheckedChange = actions.onAllDayChange)
        DateTimeRow(
            label = stringResource(R.string.event_start),
            date = start.toLocalDate(),
            time = if (allDay) null else start.toLocalTime(),
            onDate = { actions.onOpenPanel(EventPanel.StartDate) },
            onTime = { actions.onOpenPanel(EventPanel.StartTime) },
        )
        DateTimeRow(
            label = stringResource(R.string.event_end),
            date = end.toLocalDate(),
            time = if (allDay) null else end.toLocalTime(),
            onDate = { actions.onOpenPanel(EventPanel.EndDate) },
            onTime = { actions.onOpenPanel(EventPanel.EndTime) },
        )
        if (validation?.code == CODE_END_BEFORE_START) {
            Text(
                text = validation.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        CalendarChoice(calendar = calendar, onClick = { actions.onOpenPanel(EventPanel.Calendars) })
        val calendarError = validation?.takeIf { it.code in CALENDAR_CODES }
        if (!state.hasWritableCalendar) {
            Text(
                text = stringResource(R.string.event_no_writable),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else if (calendarError != null) {
            Text(
                text = calendarError.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    LaunchedEffect(Unit) {
        titleFocus.requestFocusIfAttached()
        keyboard?.show()
    }
}

@Composable
private fun AllDayRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.event_all_day),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime?,
    onDate: () -> Unit,
    onTime: () -> Unit,
) {
    val locale = currentLocale()
    val dateText = weekdayLabel(date, locale)
    val dateDescription = stringResource(R.string.event_date_description, label, dateText)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onDate,
            modifier = Modifier
                .heightIn(min = Spacing.MinTouchTarget)
                .semantics { contentDescription = dateDescription },
        ) {
            Text(dateText)
        }
        if (time != null) {
            val timeText = formatTime(time, locale)
            val timeDescription = stringResource(R.string.event_time_description, label, timeText)
            OutlinedButton(
                onClick = onTime,
                modifier = Modifier
                    .heightIn(min = Spacing.MinTouchTarget)
                    .semantics { contentDescription = timeDescription },
            ) {
                Text(timeText)
            }
        }
    }
}

@Composable
private fun CalendarChoice(calendar: DeviceCalendar?, onClick: () -> Unit) {
    val name = calendar?.name ?: stringResource(R.string.event_choose_calendar)
    val description = stringResource(R.string.event_calendar_description, name)
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .semantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
            if (calendar != null) {
                Box(
                    Modifier
                        .size(Spacing.ActionIconSize / 2)
                        .clip(CircleShape)
                        .background(Color(calendar.colorArgb)),
                )
            }
            Text(text = name, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun EventPickerPanel(panel: EventPanel, state: EventEditorUiState, actions: EventEditorActions) {
    val zone = ZoneId.of(state.zoneId)
    val start = eventStartLocal(state.draft, zone)
    val end = eventEndLocal(state.draft, zone)
    when (panel) {
        EventPanel.StartDate -> PanelScaffold(stringResource(R.string.event_panel_start_date), actions.onBack) {
            DueDatePickerContent(initialDate = start.toLocalDate(), onDatePicked = actions.onPickStartDate, compact = true)
        }
        EventPanel.StartTime -> PanelScaffold(stringResource(R.string.event_panel_start_time), actions.onBack) {
            DueTimePickerContent(initialTime = start.toLocalTime(), onTimePicked = actions.onPickStartTime)
        }
        EventPanel.EndDate -> PanelScaffold(stringResource(R.string.event_panel_end_date), actions.onBack) {
            DueDatePickerContent(initialDate = end.toLocalDate(), onDatePicked = actions.onPickEndDate, compact = true)
        }
        EventPanel.EndTime -> PanelScaffold(stringResource(R.string.event_panel_end_time), actions.onBack) {
            DueTimePickerContent(initialTime = end.toLocalTime(), onTimePicked = actions.onPickEndTime)
        }
        EventPanel.Form, EventPanel.Calendars -> Unit
    }
}

@Composable
private fun CalendarsPanel(state: EventEditorUiState, actions: EventEditorActions) {
    PanelScaffold(stringResource(R.string.event_panel_calendars), actions.onBack) {
        if (state.calendars.isEmpty()) {
            Text(
                text = stringResource(R.string.event_no_writable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.calendars.forEach { calendar ->
            val label = if (calendar.isWritable) {
                calendar.name
            } else {
                "${calendar.name} · ${stringResource(R.string.event_calendar_read_only)}"
            }
            RadioOption(
                label = label,
                selected = calendar.id == state.draft.calendarId,
                onSelect = { actions.onCalendarChosen(calendar.id) },
            )
        }
    }
}

private const val CODE_TITLE_REQUIRED = "EVENT_TITLE_REQUIRED"
private const val CODE_END_BEFORE_START = "EVENT_END_BEFORE_START"
private val TITLE_CODES = setOf(CODE_TITLE_REQUIRED, "EVENT_TITLE_TOO_LONG")
private val CALENDAR_CODES = setOf("EVENT_CALENDAR_REQUIRED", "EVENT_CALENDAR_NOT_WRITABLE")
