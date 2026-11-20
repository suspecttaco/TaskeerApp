package com.simposio.taskeer.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.board.BoardViewModel
import com.simposio.taskeer.ui.boards.BoardsViewModel
import com.simposio.taskeer.ui.lists.ListViewModel
import com.simposio.taskeer.ui.lists.TaskListsViewModel
import com.simposio.taskeer.ui.navigation.AppDestination
import com.simposio.taskeer.ui.navigation.AppNavHost
import com.simposio.taskeer.ui.navigation.BackStep
import com.simposio.taskeer.ui.navigation.MenuItem
import com.simposio.taskeer.ui.navigation.OverflowMenu
import com.simposio.taskeer.ui.navigation.backStep
import com.simposio.taskeer.ui.navigation.chromeFor
import com.simposio.taskeer.ui.navigation.navigateHome
import com.simposio.taskeer.ui.navigation.overflowMenuFor
import com.simposio.taskeer.ui.navigation.quickAddContextFor
import com.simposio.taskeer.ui.navigation.withListCount
import com.simposio.taskeer.ui.quickadd.QuickAddViewModel
import com.simposio.taskeer.ui.tasks.SwipeHintState
import com.simposio.taskeer.ui.tasks.TaskDeletionViewModel
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun AppShell(
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
    unreadCount: Int = 0,
    openViewerRequested: Boolean = false,
    onViewerHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val swipeHint = rememberSaveable(saver = SwipeHintState.Saver) { SwipeHintState() }
    val currentEntry by navController.currentBackStackEntryAsState()
    val destination = AppDestination.fromRoute(currentEntry?.destination?.route) ?: AppDestination.START
    val chrome = chromeFor(destination)
    val listId = currentEntry?.arguments?.getString(ListViewModel.LIST_ID_ARG)
    val boardId = currentEntry?.arguments?.getString(BoardViewModel.BOARD_ID_ARG)

    val quickAdd: QuickAddViewModel = viewModel(factory = viewModelFactory)
    val quickAddState by quickAdd.uiState.collectAsStateWithLifecycle()
    val clearCompleted: ClearCompletedViewModel = viewModel(factory = viewModelFactory)
    val deletion: TaskDeletionViewModel = viewModel(factory = viewModelFactory)
    val pendingUndo by deletion.pending.collectAsStateWithLifecycle()
    val deletionFailure by deletion.error.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val deletedCount = pendingUndo?.taskIds?.size ?: 0
    val deletedMessage = if (deletedCount <= 1) {
        stringResource(R.string.task_deleted)
    } else {
        pluralStringResource(R.plurals.tasks_deleted, deletedCount, deletedCount)
    }
    val undoLabel = stringResource(R.string.task_deleted_undo)
    val failureText = deletionFailure?.let {
        if (it.whileUndoing) stringResource(R.string.task_undo_failed, it.error.message) else it.error.message
    }
    val clearState by clearCompleted.uiState.collectAsStateWithLifecycle()

    // Los ViewModels de lista y tablero comparten dueño con su pantalla (la entrada de navegación).
    val entry = currentEntry
    val listViewModel: ListViewModel? = if (destination == AppDestination.LIST && entry != null) {
        viewModel(viewModelStoreOwner = entry, factory = viewModelFactory)
    } else {
        null
    }
    val listsViewModel: TaskListsViewModel? = if (destination == AppDestination.LIST && entry != null) {
        viewModel(viewModelStoreOwner = entry, factory = viewModelFactory)
    } else {
        null
    }
    val boardsViewModel: BoardsViewModel? = if (destination == AppDestination.BOARD && entry != null) {
        viewModel(viewModelStoreOwner = entry, factory = viewModelFactory)
    } else {
        null
    }
    val listName = listViewModel?.uiState?.collectAsStateWithLifecycle()?.value?.listName
    val listCount = listsViewModel?.uiState?.collectAsStateWithLifecycle()?.value?.lists?.size ?: 0
    val boardName = boardsViewModel?.uiState?.collectAsStateWithLifecycle()?.value
        ?.boards?.firstOrNull { it.id == boardId }?.name

    LaunchedEffect(openViewerRequested) {
        if (openViewerRequested) {
            navController.navigate(AppDestination.NOTIFICATIONS.route) { launchSingleTop = true }
            onViewerHandled()
        }
    }
    // Indefinite: el cierre lo decide el ViewModel al vencer la ventana de deshacer, no la duración de Material.
    LaunchedEffect(pendingUndo?.token) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) deletion.undo()
    }
    LaunchedEffect(pendingUndo == null) {
        if (pendingUndo == null) snackbarHost.currentSnackbarData?.dismiss()
    }
    LaunchedEffect(deletionFailure) {
        val text = failureText ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message = text, duration = SnackbarDuration.Short)
        deletion.consumeError()
    }
    LaunchedEffect(destination, listId) {
        quickAdd.close()
        quickAdd.setContext(quickAddContextFor(destination, listId))
    }

    val menu: OverflowMenu = overflowMenuFor(destination).withListCount(listCount)
    val title = destination.title(listName, boardName)
    val back = backStep(destination, boardId, hasPrevious = navController.previousBackStackEntry != null)

    val snackbarClearance = if (destination == AppDestination.HOME) Spacing.HubBarClearance else Spacing.ChromeClearance
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHost,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = snackbarClearance),
                )
            },
            topBar = {
                ShellTopBar(
                    kind = chrome.topBar,
                    title = title,
                    unreadCount = unreadCount,
                    menu = menu,
                    clearEnabled = clearState.clearEnabled,
                    actions = TopBarActions(
                        onNotifications = {
                            navController.navigate(AppDestination.NOTIFICATIONS.route) { launchSingleTop = true }
                        },
                        onBack = {
                            when (back) {
                                BackStep.Pop -> navController.popBackStack()
                                is BackStep.Navigate -> navController.navigate(back.route) { launchSingleTop = true }
                                BackStep.None -> Unit
                            }
                        },
                        onMenuItem = { item ->
                            when (item) {
                                MenuItem.CLEAR_COMPLETED -> clearCompleted.requestClear()
                                MenuItem.SETTINGS ->
                                    navController.navigate(AppDestination.SETTINGS.route) { launchSingleTop = true }
                                MenuItem.RENAME_LIST -> listId?.let { listsViewModel?.openRename(it) }
                                MenuItem.DELETE_LIST -> listId?.let { listsViewModel?.openDelete(it) }
                                MenuItem.RENAME_BOARD -> boardId?.let { boardsViewModel?.openRename(it) }
                                MenuItem.DELETE_BOARD -> boardId?.let { boardsViewModel?.openDelete(it) }
                                MenuItem.MY_CALENDARS, MenuItem.FILTER, MenuItem.SORT -> Unit
                            }
                        },
                    ),
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                AppNavHost(
                    navController = navController,
                    viewModelFactory = viewModelFactory,
                    swipeHint = swipeHint,
                    onAddForDate = quickAdd::openWithDate,
                    onDelete = deletion::delete,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding(),
                )
            }
        }
        QuickAddScrim(visible = quickAddState.isOpen, onDismiss = quickAdd::close)
        FloatingChrome(
            chrome = chrome,
            pill = quickAddState,
            pillActions = PillActions(
                onOpen = quickAdd::open,
                onTitleChange = quickAdd::onTitleChange,
                onSend = quickAdd::add,
                onClearDate = quickAdd::clearDate,
                onShortcut = quickAdd::selectShortcut,
                onPickDate = quickAdd::openDatePicker,
                onDatePicked = quickAdd::pickDate,
                onDismissDatePicker = quickAdd::dismissDatePicker,
                onToggleListPanel = quickAdd::toggleListPanel,
                onSelectList = quickAdd::selectList,
            ),
            onHome = navController::navigateHome,
        )
    }
    ClearCompletedDialog(
        state = clearState,
        onConfirm = { clearCompleted.confirm(deletion::deleteCompleted) },
        onCancel = clearCompleted::cancel,
    )
    // Después del NavHost: gana el último registrado, así Atrás cierra la píldora antes de salir de la pantalla.
    BackHandler(enabled = quickAddState.isOpen) { quickAdd.onBack() }
}

@Composable
private fun AppDestination.title(listName: String?, boardName: String?): String = when (this) {
    AppDestination.MY_DAY -> stringResource(R.string.section_my_day)
    AppDestination.HOME -> stringResource(R.string.home_title)
    AppDestination.UPCOMING_7_DAYS -> stringResource(R.string.section_upcoming)
    AppDestination.ALL_TASKS -> stringResource(R.string.section_all_tasks)
    AppDestination.CALENDAR -> stringResource(R.string.section_calendar)
    AppDestination.LIST -> listName.orEmpty()
    AppDestination.BOARD -> boardName ?: stringResource(R.string.board_title)
    AppDestination.BOARD_COLUMNS -> stringResource(R.string.columns_title)
    AppDestination.NOTIFICATIONS -> stringResource(R.string.notifications_title)
    AppDestination.SETTINGS -> stringResource(R.string.menu_settings)
}

@Preview(showBackground = true)
@Composable
private fun AppShellPreview() {
    TaskeerTheme {
        AppShell(
            // La preview abre el destino inicial (Mi día): la factoría nunca se usa.
            viewModelFactory = object : ViewModelProvider.Factory {},
        )
    }
}
