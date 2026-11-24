package com.simposio.taskeer.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.calendar.periodFor
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.navigation.panelEnter
import com.simposio.taskeer.ui.navigation.panelExit
import com.simposio.taskeer.ui.permission.CalendarPermissionNotice
import com.simposio.taskeer.ui.permission.RefreshOnResume
import com.simposio.taskeer.ui.permission.rememberCalendarPermissionRequest
import com.simposio.taskeer.ui.permission.rememberOpenAppSettings
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.theme.Spacing
import java.time.ZoneId

@Composable
fun CalendarScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CalendarViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory, onDelete)
    val requestPermission = rememberCalendarPermissionRequest(viewModel::onPermissionResult)
    val openSettings = rememberOpenAppSettings()
    RefreshOnResume(viewModel::refreshAccess)

    val animated = rememberAnimationsEnabled()
    val listState = rememberLazyListState()
    val rows = remember(state.days, state.mode, state.today) { bodyRows(state.days, state.mode, state.today) }
    val periodStart = periodFor(state.mode, state.anchor, state.firstDay).start

    // Al cambiar de modo o de periodo la lista vuelve arriba; al volver a la pantalla se conserva la posición.
    var shownPeriod by remember { mutableStateOf(state.mode to periodStart) }
    LaunchedEffect(state.mode, periodStart) {
        if (shownPeriod != state.mode to periodStart) {
            shownPeriod = state.mode to periodStart
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(state.scrollTarget, rows) {
        val target = state.scrollTarget ?: return@LaunchedEffect
        rows.indexOfDay(target)?.let { index ->
            if (animated) listState.animateScrollToItem(index) else listState.scrollToItem(index)
        }
        viewModel.consumeScroll()
    }

    Column(modifier = modifier.fillMaxSize()) {
        CalendarHeader(
            state = state,
            actions = CalendarHeaderActions(
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onToday = viewModel::goToToday,
                onToggleMonth = viewModel::toggleMonth,
                onOpenCalendars = viewModel::openPicker,
                onSelectMode = viewModel::selectMode,
            ),
        )
        CalendarNoticeSlot(
            state = state,
            onAllow = requestPermission,
            onOpenSettings = openSettings,
            onRetry = viewModel::retry,
        )
        if (state.load == CalendarLoad.Loading) {
            val loading = stringResource(R.string.calendar_loading)
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = loading },
            )
        }
        AnimatedVisibility(
            visible = state.monthExpanded,
            enter = panelEnter(animated),
            exit = panelExit(animated),
        ) {
            MonthGrid(state = state, onSelectDate = viewModel::selectDate)
        }
        CalendarBody(
            rows = rows,
            zone = ZoneId.of(state.zoneId),
            listState = listState,
            onOpenTask = editorHost.openForEdit,
            modifier = Modifier.weight(1f),
        )
    }
    if (state.isPickerOpen) {
        CalendarsSheet(
            calendars = state.calendars,
            onToggle = viewModel::setCalendarVisible,
            onClose = viewModel::closePicker,
        )
    }
}

// Una sola ranura encima del cuerpo, con prioridad: permiso, error de lectura, sin calendarios.
@Composable
private fun CalendarNoticeSlot(
    state: CalendarUiState,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val slot = Modifier.padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap)
    val notice = state.readNotice
    val load = state.load
    when {
        notice != null -> CalendarPermissionNotice(notice, onAllow, onOpenSettings, modifier = slot)
        load is CalendarLoad.Failed -> Card(modifier = slot.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.calendar_error),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.calendar_retry)) }
            }
        }
        load == CalendarLoad.NoCalendars -> Text(
            text = stringResource(R.string.calendar_no_calendars),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = slot.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
