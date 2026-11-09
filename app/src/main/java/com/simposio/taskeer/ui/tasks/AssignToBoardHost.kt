package com.simposio.taskeer.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R

@Composable
fun rememberAssignToBoardHost(viewModelFactory: ViewModelProvider.Factory): (String) -> Unit {
    val viewModel: AssignToBoardViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isOpen) {
        DestinationPickerDialog(
            title = stringResource(R.string.assign_title),
            destinations = state.destinations,
            allowInbox = false,
            selectedColumnId = null,
            onSelectInbox = {},
            onSelectColumn = viewModel::choose,
            onDismiss = viewModel::dismiss,
            error = state.error,
        )
    }

    return remember(viewModel) { viewModel::open }
}
