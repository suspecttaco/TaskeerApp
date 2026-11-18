package com.simposio.taskeer.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.TaskListSummary
import com.simposio.taskeer.ui.boards.BoardDialogs
import com.simposio.taskeer.ui.boards.BoardsUiState
import com.simposio.taskeer.ui.boards.BoardsViewModel
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.lists.ListDialogs
import com.simposio.taskeer.ui.lists.TaskListsUiState
import com.simposio.taskeer.ui.lists.TaskListsViewModel
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant

@Composable
fun HomeScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onOpenList: (String) -> Unit,
    onOpenBoard: (String) -> Unit,
    onOpenSection: (HomeSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listsViewModel: TaskListsViewModel = viewModel(factory = viewModelFactory)
    val boardsViewModel: BoardsViewModel = viewModel(factory = viewModelFactory)
    val listsState by listsViewModel.uiState.collectAsStateWithLifecycle()
    val boardsState by boardsViewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        lists = listsState,
        boards = boardsState,
        onOpenList = onOpenList,
        onNewList = listsViewModel::openCreate,
        onOpenBoard = onOpenBoard,
        onNewBoard = boardsViewModel::openCreate,
        onOpenSection = onOpenSection,
        modifier = modifier,
    )
    ListDialogs(listsState, listsViewModel)
    BoardDialogs(boardsState, boardsViewModel)
}

@Composable
fun HomeContent(
    lists: TaskListsUiState,
    boards: BoardsUiState,
    onOpenList: (String) -> Unit,
    onNewList: () -> Unit,
    onOpenBoard: (String) -> Unit,
    onNewBoard: () -> Unit,
    onOpenSection: (HomeSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Spacing.TileMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.ScreenPadding,
                end = Spacing.ScreenPadding,
                top = Spacing.ScreenPadding,
                bottom = Spacing.HubBarClearance,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
        ) {
            sectionTitle(R.string.home_own_lists)
            items(lists.lists, key = { "list-${it.list.id}" }) { summary ->
                Tile(
                    label = tileLabel(summary.list.name, summary.activeCount),
                    onClick = { onOpenList(summary.list.id) },
                ) {
                    Text(
                        text = summary.list.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = summary.activeCount.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "new-list") { NewTile(stringResource(R.string.home_new_list), onNewList) }
            sectionTitle(R.string.home_shared_space)
            items(boards.boards, key = { "board-${it.id}" }) { board ->
                Tile(label = board.name, onClick = { onOpenBoard(board.id) }) {
                    DecorativeIcon(Icons.Filled.Dashboard, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = board.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item(key = "new-board") { NewTile(stringResource(R.string.home_new_board), onNewBoard) }
        }
        HomeSectionBar(
            onOpenSection = onOpenSection,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun LazyGridScope.sectionTitle(@StringRes title: Int) {
    item(key = "title-$title", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(top = Spacing.SectionGap)
                .semantics { heading() },
        )
    }
}

@Composable
private fun Tile(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.TileHeight)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.CardPadding * 2),
            verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
        ) { content() }
    }
}

@Composable
private fun NewTile(label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.TileHeight)
            .semantics { role = Role.Button },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.CardPadding * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DecorativeIcon(Icons.Filled.Add, tint = MaterialTheme.colorScheme.primary)
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// El hub no es una pestaña: ningún ítem está seleccionado. Se sustituye la semántica de pestaña por la de botón para que
// TalkBack no anuncie «no seleccionado».
@Composable
fun HomeSectionBar(onOpenSection: (HomeSection) -> Unit, modifier: Modifier = Modifier) {
    val comingSoon = stringResource(R.string.coming_soon)
    NavigationBar(modifier = modifier) {
        HomeSection.entries.forEach { section ->
            val fullName = stringResource(section.labelRes())
            NavigationBarItem(
                selected = false,
                onClick = { onOpenSection(section) },
                icon = { DecorativeIcon(section.icon(), size = Spacing.ActionIconSize) },
                label = { Text(fullName) },
                alwaysShowLabel = true,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = fullName
                    role = Role.Button
                    if (section.isPlaceholder) stateDescription = comingSoon
                    onClick(label = fullName) {
                        onOpenSection(section)
                        true
                    }
                },
            )
        }
    }
}

@StringRes
fun HomeSection.labelRes(): Int = when (this) {
    HomeSection.MY_DAY -> R.string.section_my_day
    HomeSection.UPCOMING_7_DAYS -> R.string.section_upcoming
    HomeSection.ALL_TASKS -> R.string.section_all_tasks
    HomeSection.CALENDAR -> R.string.section_calendar
}

private fun HomeSection.icon(): ImageVector = when (this) {
    HomeSection.MY_DAY -> Icons.Filled.WbSunny
    HomeSection.UPCOMING_7_DAYS -> Icons.Filled.DateRange
    HomeSection.ALL_TASKS -> Icons.Filled.Checklist
    HomeSection.CALENDAR -> Icons.Filled.CalendarMonth
}

@Composable
fun HomeSearchField(modifier: Modifier = Modifier) {
    val wip = stringResource(R.string.home_search_wip)
    OutlinedTextField(
        value = "",
        onValueChange = {},
        enabled = HomeLayout.SEARCH_ENABLED,
        readOnly = true,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { DecorativeIcon(Icons.Filled.Search) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .semantics { stateDescription = wip },
    )
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun HomeContentPreview() {
    fun summary(id: String, name: String, count: Int) = TaskListSummary(
        TaskList(name, 0, Instant.EPOCH, SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null)),
        count,
    )
    TaskeerTheme {
        HomeContent(
            lists = TaskListsUiState(lists = listOf(summary("a", "Tareas", 3), summary("b", "Compras", 2))),
            boards = BoardsUiState(
                boards = listOf(Board("Proyecto", Instant.EPOCH, SyncMetadata("p", Instant.EPOCH, false, null))),
            ),
            onOpenList = {},
            onNewList = {},
            onOpenBoard = {},
            onNewBoard = {},
            onOpenSection = {},
        )
    }
}
