package com.simposio.taskeer.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberMoveToListHost(viewModelFactory: ViewModelProvider.Factory): (String, String) -> Unit {
    val viewModel: MoveToListViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isOpen) {
        val title = stringResource(R.string.move_to_list_title)
        val dragDescription = stringResource(R.string.quick_add_drag_handle)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismiss,
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
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.ScreenPadding)
                    .semantics { paneTitle = title },
                verticalArrangement = Arrangement.spacedBy(Spacing.SheetContentGap),
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
                        onClick = viewModel::dismiss,
                    )
                }
                DestinationOptions(
                    destinations = emptyList(),
                    selectedColumnId = null,
                    onSelectColumn = {},
                    lists = state.lists,
                    onSelectList = viewModel::choose,
                    showBoards = false,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    error = state.error,
                )
            }
        }
    }

    return remember(viewModel) { { taskId, currentListId -> viewModel.open(taskId, currentListId) } }
}
