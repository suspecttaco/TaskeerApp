package com.simposio.taskeer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.EmptyHint
import com.simposio.taskeer.ui.shell.TrackOpenSheet
import com.simposio.taskeer.ui.theme.Spacing

internal class RegionPickerActions(
    val onQueryChange: (String) -> Unit,
    val onSelectCountry: (String?) -> Unit,
    val onSelectTimeZone: (String?) -> Unit,
    val onDismiss: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RegionPickerSheet(
    kind: RegionPickerKind,
    state: SettingsUiState,
    actions: RegionPickerActions,
) {
    val title = stringResource(
        if (kind == RegionPickerKind.COUNTRY) {
            R.string.settings_region_picker_country
        } else {
            R.string.settings_region_picker_time_zone
        },
    )
    val dragDescription = stringResource(R.string.quick_add_drag_handle)
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.semantics { contentDescription = dragDescription },
            )
        },
    ) {
        TrackOpenSheet()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.ScreenPadding)
                .semantics { paneTitle = title },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ActionIcon(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.quick_add_close),
                    onClick = actions.onDismiss,
                )
            }
            SearchField(query = state.query, onQueryChange = actions.onQueryChange)
            if (kind == RegionPickerKind.COUNTRY) {
                CountryList(state, actions.onSelectCountry)
            } else {
                TimeZoneList(state, actions.onSelectTimeZone)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = { Text(stringResource(R.string.settings_region_search_hint)) },
        leadingIcon = { DecorativeIcon(Icons.Filled.Search) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                ActionIcon(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.settings_region_search_clear),
                    onClick = { onQueryChange("") },
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.InlineGap),
    )
}

@Composable
private fun CountryList(state: SettingsUiState, onSelect: (String?) -> Unit) {
    LazyColumn {
        if (state.query.isEmpty()) {
            item(key = NONE_KEY) {
                PickerRow(
                    text = stringResource(R.string.settings_region_no_country),
                    selected = state.region.countryIso == null,
                    onClick = { onSelect(null) },
                )
            }
        }
        items(state.countries, key = { it.isoCode }) { country ->
            PickerRow(
                text = "${country.flagEmoji} ${country.name}",
                selected = state.region.countryIso == country.isoCode,
                onClick = { onSelect(country.isoCode) },
            )
        }
        if (state.countries.isEmpty()) {
            item(key = EMPTY_KEY) { EmptyResults() }
        }
    }
}

@Composable
private fun TimeZoneList(state: SettingsUiState, onSelect: (String?) -> Unit) {
    LazyColumn {
        if (state.query.isEmpty()) {
            item(key = NONE_KEY) {
                PickerRow(
                    text = stringResource(R.string.settings_region_device_zone),
                    supporting = state.systemZoneId,
                    selected = state.region.timeZoneIana == null,
                    onClick = { onSelect(null) },
                )
            }
        }
        items(state.timeZones, key = { it.option.ianaName }) { row ->
            PickerRow(
                text = row.option.displayName,
                supporting = row.offsetLabel,
                selected = state.region.timeZoneIana == row.option.ianaName,
                onClick = { onSelect(row.option.ianaName) },
            )
        }
        if (state.timeZones.isEmpty()) {
            item(key = EMPTY_KEY) { EmptyResults() }
        }
    }
}

@Composable
private fun PickerRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
) {
    val selectedText = stringResource(R.string.settings_region_selected)
    ListItem(
        headlineContent = {
            Text(
                text = text,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = if (selected) {
            { DecorativeIcon(Icons.Filled.Check, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedText },
    )
}

@Composable
private fun EmptyResults() {
    EmptyHint(
        text = stringResource(R.string.settings_region_empty),
        modifier = Modifier.padding(vertical = Spacing.RowVerticalPadding),
    )
}

private const val NONE_KEY = "none"
private const val EMPTY_KEY = "empty"
