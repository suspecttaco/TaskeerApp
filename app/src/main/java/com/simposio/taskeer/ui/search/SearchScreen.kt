package com.simposio.taskeer.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column as BoardColumn
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.search.BoardHit
import com.simposio.taskeer.domain.search.ColumnHit
import com.simposio.taskeer.domain.search.ListHit
import com.simposio.taskeer.domain.search.SearchHit
import com.simposio.taskeer.domain.search.SearchResults
import com.simposio.taskeer.domain.search.SearchableTask
import com.simposio.taskeer.domain.search.TaskHit
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant

@Composable
fun SearchScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onOpenBoard: (String) -> Unit,
    onOpenList: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory, onDelete)

    // El campo guarda su propio TextFieldValue: el estado del ViewModel llega con retraso y, usado como
    // valor del campo, movería el cursor mientras se escribe.
    var field by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
    val startedEmpty = remember { state.query.isEmpty() }
    SearchContent(
        state = state,
        field = field,
        startedEmpty = startedEmpty,
        onFieldChange = {
            field = it
            viewModel.onQueryChange(it.text)
        },
        onClear = {
            field = TextFieldValue("")
            viewModel.clear()
        },
        onOpen = { hit ->
            when (val target = targetOf(hit)) {
                is SearchTarget.OpenBoard -> onOpenBoard(target.boardId)
                is SearchTarget.OpenList -> onOpenList(target.listId)
                is SearchTarget.OpenTask -> editorHost.openForEdit(target.taskId)
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun SearchContent(
    state: SearchUiState,
    field: TextFieldValue,
    startedEmpty: Boolean,
    onFieldChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    onOpen: (SearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (startedEmpty) focusRequester.requestFocus()
    }
    val clearAndFocus: () -> Unit = {
        onClear()
        focusRequester.requestFocus()
    }
    Column(modifier = modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        OutlinedTextField(
            value = field,
            onValueChange = onFieldChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { DecorativeIcon(Icons.Filled.Search) },
            trailingIcon = if (field.text.isNotEmpty()) {
                {
                    ActionIcon(
                        icon = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.search_clear),
                        onClick = clearAndFocus,
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.ScreenPadding)
                .focusRequester(focusRequester),
        )
        when (state.status) {
            SearchStatus.Idle -> StatePage(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.search_idle_title),
                message = stringResource(R.string.search_idle_message),
            )
            SearchStatus.NoResults -> StatePage(
                icon = Icons.Filled.SearchOff,
                title = stringResource(R.string.search_empty_title),
                message = stringResource(R.string.search_empty_message, state.query.trim()),
                action = StatePageAction(stringResource(R.string.search_clear), clearAndFocus),
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            )
            SearchStatus.Results -> SearchResultsList(
                results = state.results,
                onOpen = onOpen,
                onUserScroll = { keyboard?.hide() },
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: SearchResults,
    onOpen: (SearchHit) -> Unit,
    onUserScroll: () -> Unit,
) {
    val hideKeyboard = remember(onUserScroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) onUserScroll()
                return Offset.Zero
            }
        }
    }
    LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(hideKeyboard)) {
        item(key = "summary") {
            Text(
                text = pluralStringResource(R.plurals.search_summary, results.total, results.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (results.boards.isNotEmpty()) {
            item(key = "boards-header") {
                GroupHeader(stringResource(R.string.search_group_boards, results.boards.size))
            }
            items(results.boards, key = { "board-${it.id}" }) { hit -> BoardRow(hit) { onOpen(hit) } }
        }
        if (results.lists.isNotEmpty()) {
            item(key = "lists-header") {
                GroupHeader(stringResource(R.string.search_group_lists, results.lists.size))
            }
            items(results.lists, key = { "list-${it.id}" }) { hit -> ListRow(hit) { onOpen(hit) } }
        }
        if (results.columns.isNotEmpty()) {
            item(key = "columns-header") {
                GroupHeader(stringResource(R.string.search_group_columns, results.columns.size))
            }
            items(results.columns, key = { "column-${it.id}" }) { hit -> ColumnRow(hit) { onOpen(hit) } }
        }
        if (results.tasks.isNotEmpty()) {
            item(key = "tasks-header") {
                GroupHeader(stringResource(R.string.search_group_tasks, results.tasks.size))
            }
            items(results.tasks, key = { "task-${it.id}" }) { hit -> TaskRow(hit) { onOpen(hit) } }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap)
            .semantics { heading() },
    )
}

@Composable
private fun BoardRow(hit: BoardHit, onClick: () -> Unit) {
    SearchRow(
        icon = Icons.Filled.Dashboard,
        title = hit.board.name,
        context = null,
        description = rowDescription(stringResource(R.string.search_kind_board), hit.board.name, null, null),
        onClick = onClick,
    )
}

@Composable
private fun ListRow(hit: ListHit, onClick: () -> Unit) {
    val context = if (hit.openTasks == 0) {
        stringResource(R.string.search_list_no_open_tasks)
    } else {
        pluralStringResource(R.plurals.search_list_open_tasks, hit.openTasks, hit.openTasks)
    }
    SearchRow(
        icon = Icons.Filled.Checklist,
        title = hit.list.name,
        context = context,
        description = rowDescription(stringResource(R.string.search_kind_list), hit.list.name, context, null),
        onClick = onClick,
    )
}

@Composable
private fun ColumnRow(hit: ColumnHit, onClick: () -> Unit) {
    SearchRow(
        icon = Icons.Filled.ViewColumn,
        title = hit.column.name,
        context = hit.boardName,
        description = rowDescription(
            stringResource(R.string.search_kind_column),
            hit.column.name,
            hit.boardName,
            null,
        ),
        onClick = onClick,
    )
}

@Composable
private fun TaskRow(hit: TaskHit, onClick: () -> Unit) {
    val task = hit.task.task
    val context = when (val value = taskContext(hit.task)) {
        is TaskContext.InBoard -> stringResource(R.string.search_task_in_board, value.board, value.column)
        is TaskContext.InList -> value.name.ifBlank { null }
    }
    SearchRow(
        icon = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
        title = task.title,
        context = context,
        completed = task.isCompleted,
        description = rowDescription(
            stringResource(R.string.search_kind_task),
            task.title,
            context,
            if (task.isCompleted) stringResource(R.string.search_completed) else null,
        ),
        onClick = onClick,
    )
}

@Composable
private fun SearchRow(
    icon: ImageVector,
    title: String,
    context: String?,
    description: String,
    onClick: () -> Unit,
    completed: Boolean = false,
) {
    ListItem(
        leadingContent = { DecorativeIcon(icon) },
        headlineContent = {
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = context?.let {
            { Text(text = it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        modifier = Modifier
            .heightIn(min = Spacing.MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    )
}

private val previewNow: Instant = Instant.EPOCH

private fun previewSync(id: String) = SyncMetadata(id, previewNow, pending = false, deletedAt = null)

private fun previewResults(): SearchResults {
    fun task(id: String, title: String, completed: Boolean) = SearchableTask(
        task = Task(
            columnId = "c1",
            title = title,
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = null,
            assigneeId = null,
            position = 0,
            createdAt = previewNow,
            completedAt = if (completed) previewNow else null,
            sync = previewSync(id),
        ),
        boardId = "b1",
        boardName = "Viaje",
        columnName = "Por hacer",
        listName = null,
    )
    return SearchResults(
        boards = listOf(BoardHit(Board("Viaje a Lisboa", previewNow, previewSync("b1")))),
        lists = listOf(ListHit(TaskList("Viaje: maleta", 0, previewNow, previewSync("l1")), openTasks = 2)),
        columns = listOf(
            ColumnHit(
                BoardColumn("b1", "Viaje pendiente", 0, null, false, previewNow, previewSync("c1")),
                "Viaje a Lisboa",
            ),
        ),
        tasks = listOf(
            TaskHit(task("t1", "Reservar viaje", completed = false), inTitle = true),
            TaskHit(task("t2", "Comprar maleta de viaje", completed = true), inTitle = true),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun SearchIdlePreview() {
    TaskeerTheme {
        SearchContent(SearchUiState(), TextFieldValue(""), true, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchNoResultsPreview() {
    TaskeerTheme {
        SearchContent(
            SearchUiState("zzzz", SearchStatus.NoResults),
            TextFieldValue("zzzz"),
            false,
            {},
            {},
            {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchResultsPreview() {
    TaskeerTheme {
        SearchContent(
            SearchUiState("viaje", SearchStatus.Results, previewResults()),
            TextFieldValue("viaje"),
            false,
            {},
            {},
            {},
        )
    }
}
