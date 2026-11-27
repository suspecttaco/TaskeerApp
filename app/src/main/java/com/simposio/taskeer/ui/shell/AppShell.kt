package com.simposio.taskeer.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.ui.auth.SessionMessage
import com.simposio.taskeer.ui.auth.SessionViewModel
import com.simposio.taskeer.domain.myday.shouldAutoOpenSuggestions
import com.simposio.taskeer.ui.board.BoardViewModel
import com.simposio.taskeer.ui.calendar.CalendarViewModel
import com.simposio.taskeer.ui.calendar.EventEditorActions
import com.simposio.taskeer.ui.calendar.EventEditorSheet
import com.simposio.taskeer.ui.calendar.EventEditorViewModel
import com.simposio.taskeer.ui.boards.BoardsViewModel
import com.simposio.taskeer.ui.components.NoticeKind
import com.simposio.taskeer.ui.components.showNotice
import com.simposio.taskeer.ui.lists.ListViewModel
import com.simposio.taskeer.ui.lists.TaskListsViewModel
import com.simposio.taskeer.ui.navigation.AppDestination
import com.simposio.taskeer.ui.navigation.AppNavHost
import com.simposio.taskeer.ui.navigation.BackGuard
import com.simposio.taskeer.ui.navigation.BackStep
import com.simposio.taskeer.ui.navigation.LocalBackGuard
import com.simposio.taskeer.ui.navigation.MenuItem
import com.simposio.taskeer.ui.navigation.OverflowMenu
import com.simposio.taskeer.ui.navigation.backStep
import com.simposio.taskeer.ui.navigation.chromeFor
import com.simposio.taskeer.ui.navigation.navigateHome
import com.simposio.taskeer.ui.navigation.overflowMenuFor
import com.simposio.taskeer.ui.navigation.quickAddContextFor
import com.simposio.taskeer.ui.navigation.withBoardPresent
import com.simposio.taskeer.ui.navigation.withListCount
import com.simposio.taskeer.ui.myday.MySuggestionsViewModel
import com.simposio.taskeer.ui.myday.SuggestionsSheet
import com.simposio.taskeer.ui.quickadd.QuickAddViewModel
import com.simposio.taskeer.ui.tasks.SwipeHintState
import com.simposio.taskeer.ui.tasks.TaskDeletionViewModel
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import com.simposio.taskeer.ui.workspaces.ServerBoardsViewModel
import com.simposio.taskeer.ui.workspaces.WorkspaceMembersViewModel
import kotlinx.coroutines.launch

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
    val workspaceId = currentEntry?.arguments?.getString(ServerBoardsViewModel.WORKSPACE_ID_ARG)

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
    val suggestions: MySuggestionsViewModel = viewModel(factory = viewModelFactory)
    val suggestionsBadge by suggestions.badge.collectAsStateWithLifecycle()
    var suggestionsOpen by rememberSaveable { mutableStateOf(false) }
    val sheetGate = remember { SheetGate() }
    val backGuard = remember { BackGuard() }
    val eventEditor: EventEditorViewModel = viewModel(factory = viewModelFactory)
    val eventState by eventEditor.uiState.collectAsStateWithLifecycle()
    val eventAddedText = stringResource(R.string.event_added)
    val session: SessionViewModel = viewModel(factory = viewModelFactory)
    val sessionState by session.state.collectAsStateWithLifecycle()
    val sessionExpired = (sessionState as? SessionState.SignedOut)?.expired == true
    val sessionExpiredText = stringResource(R.string.auth_session_expired)
    val signInLabel = stringResource(R.string.auth_login_action)
    val loggedOutText = stringResource(R.string.auth_logged_out)
    val loggedOutOfflineText = stringResource(R.string.auth_logged_out_server_unreachable)
    val scope = rememberCoroutineScope()

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
    val membersViewModel: WorkspaceMembersViewModel? =
        if (destination == AppDestination.WORKSPACE_MEMBERS && entry != null) {
            viewModel(viewModelStoreOwner = entry, factory = viewModelFactory)
        } else {
            null
        }
    val calendarViewModel: CalendarViewModel? = if (destination == AppDestination.CALENDAR && entry != null) {
        viewModel(viewModelStoreOwner = entry, factory = viewModelFactory)
    } else {
        null
    }
    val calendarsReadable = calendarViewModel?.canReadCalendars?.collectAsStateWithLifecycle()?.value ?: true
    val listName = listViewModel?.uiState?.collectAsStateWithLifecycle()?.value?.listName
    val listCount = listsViewModel?.uiState?.collectAsStateWithLifecycle()?.value?.lists?.size ?: 0
    val boardName = boardsViewModel?.uiState?.collectAsStateWithLifecycle()?.value
        ?.boards?.firstOrNull { it.id == boardId }?.name

    val workspaceName = membersViewModel?.uiState?.collectAsStateWithLifecycle()?.value?.title?.takeIf { it.isNotEmpty() }

    SuggestionsAutoOpen(
        suggestions = suggestions,
        destination = destination,
        viewerRequested = openViewerRequested,
        suggestionsOpen = suggestionsOpen,
        sheetGate = sheetGate,
        overlayOpen = quickAddState.isOpen || clearState.confirmCount != null,
        onOpen = { suggestionsOpen = true },
    )
    LaunchedEffect(destination) {
        if (destination != AppDestination.MY_DAY) suggestionsOpen = false
    }
    LaunchedEffect(openViewerRequested) {
        if (openViewerRequested) {
            navController.navigate(AppDestination.NOTIFICATIONS.route) { launchSingleTop = true }
            onViewerHandled()
        }
    }
    // Indefinite: el cierre lo decide el ViewModel al vencer la ventana de deshacer, no la duración de Material.
    LaunchedEffect(pendingUndo?.token) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = snackbarHost.showNotice(
            message = deletedMessage,
            kind = NoticeKind.UNDO,
            actionLabel = undoLabel,
        )
        if (result == SnackbarResult.ActionPerformed) deletion.undo()
    }
    LaunchedEffect(pendingUndo == null) {
        if (pendingUndo == null) snackbarHost.currentSnackbarData?.dismiss()
    }
    LaunchedEffect(deletionFailure) {
        val text = failureText ?: return@LaunchedEffect
        snackbarHost.showNotice(message = text, kind = NoticeKind.ERROR)
        deletion.consumeError()
    }
    LaunchedEffect(destination) {
        if (destination != AppDestination.CALENDAR) eventEditor.dismiss()
    }
    // El aviso se confirma antes de mostrarse para que no reaparezca al rotar ni al volver al primer plano.
    LaunchedEffect(sessionExpired) {
        if (!sessionExpired) return@LaunchedEffect
        session.acknowledgeExpiration()
        scope.launch {
            val result = snackbarHost.showNotice(
                message = sessionExpiredText,
                kind = NoticeKind.ACTION,
                actionLabel = signInLabel,
            )
            if (result == SnackbarResult.ActionPerformed) {
                navController.navigate(AppDestination.LOGIN.route) { launchSingleTop = true }
            }
        }
    }
    LaunchedEffect(session) {
        session.messages.collect { message ->
            val text = when (message) {
                SessionMessage.LOGGED_OUT -> loggedOutText
                SessionMessage.LOGGED_OUT_SERVER_UNREACHABLE -> loggedOutOfflineText
            }
            snackbarHost.showNotice(message = text, kind = NoticeKind.INFO)
        }
    }
    // El avance de la fecha y el aviso salen del shell porque la hoja y el Calendario son hermanos, no padre e hijo.
    LaunchedEffect(eventState.createdDate) {
        val date = eventState.createdDate ?: return@LaunchedEffect
        calendarViewModel?.selectDate(date)
        eventEditor.consumeCreated()
        scope.launch { snackbarHost.showNotice(message = eventAddedText, kind = NoticeKind.INFO) }
    }
    LaunchedEffect(destination, listId) {
        quickAdd.close()
        quickAdd.setContext(quickAddContextFor(destination, listId))
    }

    val menu: OverflowMenu = overflowMenuFor(destination).withListCount(listCount).withBoardPresent(boardName != null)
    val title = destination.title(listName, boardName, workspaceName)
    val back = backStep(
        destination,
        boardId,
        hasPrevious = navController.previousBackStackEntry != null,
        workspaceId = workspaceId,
    )

    val snackbarClearance = if (destination == AppDestination.HOME) Spacing.HubBarClearance else Spacing.ChromeClearance
    CompositionLocalProvider(LocalSheetGate provides sheetGate, LocalBackGuard provides backGuard) {
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
                        showSuggestions = chrome.showSuggestions,
                        suggestionCount = suggestionsBadge.count,
                        menu = menu,
                        clearEnabled = clearState.clearEnabled,
                        calendarsEnabled = calendarsReadable,
                        actions = TopBarActions(
                            onOpenSearch = {
                                navController.navigate(AppDestination.SEARCH.route) { launchSingleTop = true }
                            },
                            onNotifications = {
                                navController.navigate(AppDestination.NOTIFICATIONS.route) { launchSingleTop = true }
                            },
                            onSuggestions = { suggestionsOpen = true },
                            onBack = {
                                if (!backGuard.intercept()) when (back) {
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
                                    MenuItem.MY_CALENDARS -> calendarViewModel?.openPicker()
                                    MenuItem.FILTER, MenuItem.SORT -> Unit
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
                        suggestionCount = suggestionsBadge.count,
                        onOpenSuggestions = { suggestionsOpen = true },
                        session = session,
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
                onAddEvent = { calendarViewModel?.let { eventEditor.open(it.currentAnchor()) } },
            )
            if (eventState.isOpen && destination == AppDestination.CALENDAR) {
                EventEditorSheet(
                    state = eventState,
                    actions = EventEditorActions(
                        onTitleChange = eventEditor::onTitleChange,
                        onAllDayChange = eventEditor::onAllDayChange,
                        onOpenPanel = eventEditor::openPanel,
                        onPickStartDate = eventEditor::onPickStartDate,
                        onPickStartTime = eventEditor::onPickStartTime,
                        onPickEndDate = eventEditor::onPickEndDate,
                        onPickEndTime = eventEditor::onPickEndTime,
                        onCalendarChosen = eventEditor::onCalendarChosen,
                        onSave = eventEditor::save,
                        onBack = eventEditor::onBack,
                        onRequestClose = eventEditor::close,
                        onClosed = eventEditor::dismiss,
                        onRefreshAccess = eventEditor::refreshAccess,
                        onPermissionResult = eventEditor::onPermissionResult,
                    ),
                )
            }
            if (suggestionsOpen && destination == AppDestination.MY_DAY) {
                SuggestionsHost(suggestions = suggestions, onClose = { suggestionsOpen = false })
            }
        }
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
private fun SuggestionsHost(suggestions: MySuggestionsViewModel, onClose: () -> Unit) {
    val items by suggestions.items.collectAsStateWithLifecycle()
    val error by suggestions.error.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { suggestions.clearError() }
    SuggestionsSheet(
        items = items,
        errorMessage = error?.message,
        onAdd = suggestions::addToMyDay,
        onDismissItem = suggestions::dismiss,
        onClose = onClose,
    )
}

// Estricta: solo el primer RESUMED del día evalúa. El reclamo persiste el día antes de decidir, de modo que
// un aviso, otra pantalla, otra hoja o cero sugerencias lo consumen sin segunda oportunidad ese día.
@Composable
private fun SuggestionsAutoOpen(
    suggestions: MySuggestionsViewModel,
    destination: AppDestination,
    viewerRequested: Boolean,
    suggestionsOpen: Boolean,
    sheetGate: SheetGate,
    overlayOpen: Boolean,
    onOpen: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentDestination by rememberUpdatedState(destination)
    val viewerPending by rememberUpdatedState(viewerRequested)
    val sheetOpen by rememberUpdatedState(suggestionsOpen)
    val overlay by rememberUpdatedState(overlayOpen)
    val open by rememberUpdatedState(onOpen)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Se lee antes de suspender: el efecto del visor de avisos baja la petición enseguida.
            val launchedFromViewer = viewerPending
            if (!suggestions.claimFirstForegroundOfDay()) return@repeatOnLifecycle
            fun blocked() = launchedFromViewer || viewerPending || sheetOpen || sheetGate.anyOpen || overlay
            if (currentDestination != AppDestination.MY_DAY || blocked()) return@repeatOnLifecycle
            val count = suggestions.awaitFirstCount()
            if (shouldAutoOpenSuggestions(currentDestination == AppDestination.MY_DAY, count, blocked())) open()
        }
    }
}

@Composable
private fun AppDestination.title(listName: String?, boardName: String?, workspaceName: String?): String = when (this) {
    AppDestination.MY_DAY -> stringResource(R.string.section_my_day)
    AppDestination.HOME -> stringResource(R.string.home_title)
    AppDestination.UPCOMING_7_DAYS -> stringResource(R.string.section_upcoming)
    AppDestination.ALL_TASKS -> stringResource(R.string.section_all_tasks)
    AppDestination.CALENDAR -> stringResource(R.string.section_calendar)
    AppDestination.LIST -> listName.orEmpty()
    AppDestination.BOARD -> boardName ?: stringResource(R.string.board_title)
    AppDestination.BOARD_COLUMNS -> stringResource(R.string.columns_title)
    AppDestination.SEARCH -> stringResource(R.string.search_title)
    AppDestination.NOTIFICATIONS -> stringResource(R.string.notifications_title)
    AppDestination.WORKSPACES -> stringResource(R.string.workspaces_title)
    AppDestination.WORKSPACE_INVITATIONS -> stringResource(R.string.workspace_invitations_inbox_title)
    AppDestination.WORKSPACE_MEMBERS -> workspaceName ?: stringResource(R.string.workspace_members_title)
    AppDestination.WORKSPACE_BOARDS -> stringResource(R.string.board_roles_boards_title)
    AppDestination.BOARD_ACCESS -> stringResource(R.string.board_roles_access_title)
    AppDestination.SETTINGS -> stringResource(R.string.menu_settings)
    AppDestination.LOGIN -> stringResource(R.string.auth_login_title)
    AppDestination.REGISTER -> stringResource(R.string.register_title)
    AppDestination.VERIFY_EMAIL -> stringResource(R.string.verify_title)
    AppDestination.RESET_PASSWORD -> stringResource(R.string.reset_title)
    AppDestination.CHANGE_PASSWORD -> stringResource(R.string.chpw_title)
    AppDestination.REGENERATE_CODES -> stringResource(R.string.regen_title)
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
