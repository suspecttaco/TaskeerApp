package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPresentationRulesTest {

    private val sourcesRoot: File = resolveSourcesRoot()

    @Test
    fun iconoDirecto_soloEnComponents() {
        val direct = Regex("(?<![A-Za-z])Icon\\(")
        val violations = uiFiles()
            .filterNot { it.relativeTo(sourcesRoot).path.startsWith("ui/components") }
            .filter { direct.containsMatchIn(it.readText()) }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("Icon( solo en ui/components: $violations", violations.isEmpty())
    }

    @Test
    fun sinGlifosDeTextoComoIcono() {
        val violations = uiFiles()
            .filter { file -> GLYPHS.any { file.readText().contains(it) } }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("glifos de texto como icono: $violations", violations.isEmpty())
    }

    @Test
    fun componentsNoImportaDataNiRoom() {
        val forbidden = listOf("com.simposio.taskeer.data", "com.simposio.taskeer.di", "androidx.room")
        val files = listOf("ui/components", "ui/theme").flatMap { dir ->
            File(sourcesRoot, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

        assertTrue("deben existir los componentes compartidos", files.isNotEmpty())
        val violations = files.flatMap { file ->
            forbidden.filter { file.readText().contains(it) }
                .map { "${file.name} referencia $it" }
        }
        assertTrue("components solo depende de compose, R y theme: $violations", violations.isEmpty())
    }

    @Test
    fun actionIconExigeDescripcionNoNula() {
        val source = File(sourcesRoot, "ui/components/ActionIcon.kt").readText()

        assertTrue(source.contains("contentDescription: String,"))
        assertTrue(!source.contains("contentDescription: String?"))
    }

    @Test
    fun completeCheckboxTieneDescripcionDeContenidoYObjetivoTactil() {
        val source = File(sourcesRoot, "ui/components/CompleteCheckbox.kt").readText()

        assertTrue(source.contains("contentDescription = description"))
        assertTrue(source.contains("R.string.task_complete"))
        assertTrue(source.contains("R.string.task_mark_pending"))
        assertTrue(source.contains("minimumInteractiveComponentSize()"))
    }

    @Test
    fun tableroUsaLaCasillaCompartida() {
        assertTrue(source("ui/board/TaskCard.kt").contains("CompleteCheckbox("))
    }

    @Test
    fun pantallasDeTareasUsanTaskRow() {
        LIST_SCREENS.forEach { path ->
            val text = source(path)
            assertTrue("$path debe usar TaskRow", text.contains("TaskRow("))
            assertTrue("$path no debe pintar tarjetas propias", !text.contains("Card("))
        }
        assertTrue(source("ui/components/TaskRow.kt").contains("CompleteCheckbox("))
    }

    @Test
    fun taskRowTieneObjetivosTactilesYDescripciones() {
        val row = source("ui/components/TaskRow.kt")

        assertTrue(row.contains("CompleteCheckbox("))
        assertTrue(row.contains("ActionIcon("))
        assertTrue(row.contains("R.string.row_more"))
        assertTrue(row.contains("PriorityDot("))
        assertTrue(source("ui/components/PriorityStyle.kt").contains("R.string.task_priority_description"))
        assertTrue(row.contains("R.string.task_overdue_description"))
        assertTrue(source("ui/components/ActionIcon.kt").contains("IconButton("))
    }

    @Test
    fun taskRowExponeAccionesPersonalizadas() {
        val row = source("ui/components/TaskRow.kt")

        assertTrue(row.contains("customActions ="))
        assertTrue(row.contains("CustomAccessibilityAction("))
        assertTrue(row.contains("actions.sheet"))
        assertTrue(source("ui/components/RowActionStyle.kt").contains("R.string.row_action_delete"))
    }

    @Test
    fun filasDeListaNoUsanMyDayToggle() {
        LIST_SCREENS.plus("ui/components/TaskRow.kt").forEach { path ->
            assertTrue("$path no debe usar MyDayToggle", !source(path).contains("MyDayToggle("))
        }
    }

    @Test
    fun sinBarraInferiorNiFab() {
        val forbidden = listOf(
            "QuickAddFab",
            "QuickAddSheet",
            "ListSelection",
            "TaskListSelector",
            "PlannedScreen",
            "BoardsScreen",
            "AppDestination.topLevel",
        )
        val violations = mainFiles().flatMap { file ->
            forbidden.filter { file.readText().contains(it) }.map { "${file.name} referencia $it" }
        }

        assertTrue("restos de lo retirado: $violations", violations.isEmpty())
        val navigationBarUsers = mainFiles().filter { it.readText().contains("NavigationBar(") }.map { it.name }
        assertEquals("la barra inferior solo vive en el hub", listOf("HomeScreen.kt"), navigationBarUsers)
        assertTrue(!File(sourcesRoot, "ui/components/QuickAddFab.kt").exists())
        assertTrue(!File(sourcesRoot, "ui/quickadd/QuickAddSheet.kt").exists())
        assertTrue(!File(sourcesRoot, "ui/planned").exists())
        assertTrue(!File(sourcesRoot, "ui/inbox").exists())
        assertTrue(!File(sourcesRoot, "ui/boards/BoardsScreen.kt").exists())
    }

    @Test
    fun mosaicosDelHub_navegaConLaunchSingleTop() {
        val host = source("ui/navigation/AppNavHost.kt")

        assertTrue(host.contains("navigate(AppDestination.listRoute(listId)) { launchSingleTop = true }"))
        assertTrue(host.contains("navigate(AppDestination.boardRoute(boardId)) { launchSingleTop = true }"))
    }

    @Test
    fun barraDelHubEsLaNavigationBarEstandarSinPersonalizar() {
        val home = source("ui/home/HomeScreen.kt")
        val bar = home.substringAfter("fun HomeSectionBar(").substringBefore("@StringRes")

        assertTrue(bar.contains("NavigationBar("))
        assertTrue(bar.contains("NavigationBarItem("))
        assertTrue(bar.contains("alwaysShowLabel = true"))
        assertTrue(bar.contains("selected = false"))
        listOf("shape", "Shape", "containerColor", "height", "heightIn", "minLines", "maxLines", ".dp").forEach {
            assertTrue("la barra no personaliza $it", !bar.contains(it))
        }
    }

    @Test
    fun pildoraYBotonHomeUsanLaFormaDelTemaSinCapsula() {
        listOf("ui/shell/FloatingChrome.kt", "ui/components/HomeButton.kt").forEach { path ->
            val text = source(path)
            listOf("CircleShape", "RoundedCornerShape(50", "shapes.extraLarge", "shapes.full").forEach {
                assertTrue("$path no debe usar $it", !text.contains(it))
            }
            assertTrue("$path usa la forma del tema", text.contains("MaterialTheme.shapes.medium"))
        }
    }

    @Test
    fun barraDelHubUsaNombresCompletosYIconosDe24dp() {
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()
        fun value(name: String) = Regex("""name="$name">(.*?)</string>""").find(strings)?.groupValues?.get(1)

        assertEquals("Mi día", value("section_my_day"))
        assertEquals("Próximos 7 días", value("section_upcoming"))
        assertEquals("Todas las tareas", value("section_all_tasks"))
        assertEquals("Calendario", value("section_calendar"))
        assertTrue("sin textos de barra duplicados", !strings.contains("section_bar_"))
        val home = source("ui/home/HomeScreen.kt")
        val bar = home.substringAfter("fun HomeSectionBar(").substringBefore("@StringRes")
        assertTrue(bar.contains("val fullName = stringResource(section.labelRes())"))
        assertTrue(bar.contains("label = { Text(fullName) }"))
        assertTrue(bar.contains("contentDescription = fullName"))
        assertTrue(bar.contains("size = Spacing.ActionIconSize"))
        assertTrue(source("ui/theme/Spacing.kt").contains("ActionIconSize = 24.dp"))
    }

    @Test
    fun buscadorOcupaTodoElAnchoDeLaBarra() {
        val field = source("ui/home/HomeScreen.kt").substringAfter("fun HomeSearchField(")

        assertTrue(field.contains("fillMaxWidth()"))
    }

    @Test
    fun sinFabClearanceHuerfano() {
        val offenders = mainFiles().filter { it.readText().contains("FabClearance") }.map { it.name }

        assertTrue("FabClearance ya no existe: $offenders", offenders.isEmpty())
        val spacing = source("ui/theme/Spacing.kt")
        assertTrue(spacing.contains("val ChromeClearance = FloatingControlSize + ScreenPadding * 2"))
        listOf(
            "ui/myday/MyDayScreen.kt",
            "ui/lists/ListScreen.kt",
            "ui/upcoming/UpcomingScreen.kt",
            "ui/alltasks/AllTasksScreen.kt",
            "ui/board/BoardScreen.kt",
        ).forEach { path ->
            assertTrue("$path debe terminar con ChromeClearance", source(path).contains("Spacing.ChromeClearance"))
        }
        assertTrue(source("ui/home/HomeScreen.kt").contains("Spacing.HubBarClearance"))
    }

    @Test
    fun cromoFlotanteUsaImePadding() {
        val chrome = source("ui/shell/FloatingChrome.kt")

        assertTrue(chrome.contains("navigationBarsPadding()"))
        assertTrue(chrome.contains("imePadding()"))
    }

    @Test
    fun homeFlotanteYPildoraTienenDescripcion() {
        assertTrue(source("ui/components/HomeButton.kt").contains("R.string.home_button_description"))
        val chrome = source("ui/shell/FloatingChrome.kt")
        assertTrue(chrome.contains("contentDescription = description"))
        assertTrue(chrome.contains("R.string.pill_description"))
        assertTrue(chrome.contains("R.string.pill_input_label"))
        assertTrue(chrome.contains("R.string.pill_send"))
        assertTrue(chrome.contains("R.string.pill_remove_date"))
    }

    @Test
    fun cabeceraPlegableExponeEstado() {
        val header = source("ui/components/DateGroupHeader.kt")

        assertTrue(header.contains("stateDescription = "))
        assertTrue(header.contains("Role.Button"))
        assertTrue(header.contains("R.string.group_expanded"))
        assertTrue(header.contains("R.string.group_collapsed"))
        assertTrue(header.contains("heading()"))
    }

    @Test
    fun controlesNuevosUsanActionIconOMinimumInteractive() {
        val offenders = NEW_CONTROL_FILES.filter { path ->
            val text = source(path)
            val raw = text.contains("IconButton(") || text.contains(".clickable(")
            raw && !(text.contains("minimumInteractiveComponentSize") || text.contains("ActionIcon("))
        }

        assertTrue("controles sin objetivo táctil mínimo: $offenders", offenders.isEmpty())
    }

    @Test
    fun homeBuscador_abreLaBusqueda() {
        val field = source("ui/home/HomeScreen.kt").substringAfter("fun HomeSearchField(").substringBefore("@Preview")

        assertTrue(field.contains("Surface("))
        assertTrue(field.contains("onClick = onOpenSearch"))
        assertTrue(!source("ui/home/HomeScreen.kt").contains("SEARCH_ENABLED"))
        assertTrue(source("ui/shell/SectionTopBar.kt").contains("HomeSearchField(actions.onOpenSearch)"))
        assertTrue(source("ui/shell/AppShell.kt").contains("AppDestination.SEARCH.route"))
    }

    @Test
    fun homeBuscador_tieneRolDeBoton() {
        val field = source("ui/home/HomeScreen.kt").substringAfter("fun HomeSearchField(").substringBefore("@Preview")

        assertTrue(field.contains("role = Role.Button"))
    }

    @Test
    fun searchField_usaOutlinedTextFieldYImeSearch() {
        val screen = source("ui/search/SearchScreen.kt")

        assertTrue(screen.contains("OutlinedTextField("))
        assertTrue(screen.contains("singleLine = true"))
        assertTrue(screen.contains("ImeAction.Search"))
        assertTrue(screen.contains("focusRequester.requestFocus()"))
        assertTrue(screen.contains("if (startedEmpty)"))
    }

    @Test
    fun searchClear_tieneDescripcion() {
        val screen = source("ui/search/SearchScreen.kt")

        assertTrue(screen.contains("icon = Icons.Filled.Clear"))
        assertTrue(screen.contains("contentDescription = stringResource(R.string.search_clear)"))
    }

    @Test
    fun searchEstados_usanStatePage() {
        val screen = source("ui/search/SearchScreen.kt")

        assertTrue(screen.contains("StatePage("))
        assertTrue(!screen.contains("ErrorPage("))
        assertTrue(screen.contains("Icons.Filled.SearchOff"))
    }

    @Test
    fun searchResultados_agrupaConEncabezadosHeading() {
        val screen = source("ui/search/SearchScreen.kt")

        assertTrue(screen.contains("LazyColumn("))
        assertTrue(screen.contains("heading()"))
        assertTrue(!screen.contains("stickyHeader"))
        assertTrue(screen.contains("R.string.search_group_lists"))
        val order = listOf("search_group_boards", "search_group_lists", "search_group_columns", "search_group_tasks")
            .map { screen.indexOf("R.string.$it") }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun searchListas_abrenLaPantallaDeLista() {
        assertTrue(source("ui/navigation/AppNavHost.kt").substringAfter("AppDestination.SEARCH.route").contains("AppDestination.listRoute(listId)"))
        val screen = source("ui/search/SearchScreen.kt")
        assertTrue(screen.contains("onOpenList(target.listId)"))
        assertTrue(screen.contains("Icons.Filled.Checklist"))
    }

    @Test
    fun searchFilas_usanListItemNativo() {
        val screen = source("ui/search/SearchScreen.kt")

        assertTrue(screen.contains("ListItem("))
        assertTrue(!screen.contains("Card("))
        assertTrue(screen.contains("role = Role.Button"))
        assertTrue(screen.contains("maxLines = 2"))
        assertTrue(screen.contains("Spacing.MinTouchTarget"))
    }

    @Test
    fun searchTarea_abreElEditorExistente() {
        val screen = source("ui/search/SearchScreen.kt")

        assertTrue(screen.contains("rememberTaskEditorHost("))
        assertTrue(screen.contains("editorHost.openForEdit(target.taskId)"))
    }

    @Test
    fun searchPantalla_respetaImeYBarrasDelSistema() {
        val screen = source("ui/search/SearchScreen.kt")

        assertTrue(screen.contains("imePadding()"))
        assertTrue(screen.contains("navigationBarsPadding()"))
        assertTrue(screen.contains("nestedScroll("))
        assertTrue(screen.contains("keyboard?.hide()"))
    }

    @Test
    fun searchLiveRegion_anunciaElTotal() {
        val screen = source("ui/search/SearchScreen.kt")

        assertTrue(screen.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(screen.contains("R.plurals.search_summary"))
        val noResults = screen.substringAfter("SearchStatus.NoResults -> StatePage(").substringBefore("SearchStatus.Results ->")
        assertTrue(noResults.contains("liveRegion = LiveRegionMode.Polite"))
    }

    @Test
    fun calendario_declaraPermisosDeLecturaYEscritura() {
        val manifest = File(sourcesRoot, "../../../../AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.READ_CALENDAR"))
        assertTrue(manifest.contains("android.permission.WRITE_CALENDAR"))
    }

    @Test
    fun calendarContractSoloViveEnDataCalendar() {
        val platformTokens = listOf("CalendarContract", "ContentResolver", "ContentObserver")
        val violations = mainFiles()
            .filterNot { it.relativeTo(sourcesRoot).path.replace(File.separatorChar, '/').startsWith("data/calendar/") }
            .flatMap { file ->
                platformTokens.filter { file.readText().contains(it) }.map { "${file.relativeTo(sourcesRoot)} usa $it" }
            }

        assertTrue("el proveedor de calendario vive en data/calendar/: $violations", violations.isEmpty())
        assertTrue(File(sourcesRoot, "data/calendar/DeviceCalendarRepository.kt").isFile)
    }

    @Test
    fun misCalendariosSeDesactivaSinPermisoDeLecturaEnElMenuYLaCabecera() {
        val menu = source("ui/shell/OverflowMenuButton.kt")
        val header = source("ui/calendar/CalendarHeader.kt")

        assertTrue(menu.contains("(item != MenuItem.MY_CALENDARS || calendarsEnabled)"))
        assertTrue(!menu.substringAfter("private fun MenuItem.isPlaceholder()").substringBefore("private fun MenuAction").contains("MY_CALENDARS"))
        assertTrue(header.contains("enabled = calendarsEnabled"))
        assertTrue(header.contains("R.string.calendar_my_calendars_unavailable"))
        assertTrue(source("ui/shell/AppShell.kt").contains("calendarsEnabled = calendarsReadable"))
    }

    @Test
    fun calendarioYaNoEsMarcador() {
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()

        assertTrue(!File(sourcesRoot, "ui/calendar/CalendarPlaceholderScreen.kt").exists())
        assertTrue(!strings.contains("name=\"calendar_placeholder\""))
        assertTrue(source("ui/navigation/AppNavHost.kt").contains("CalendarScreen(viewModelFactory, onDelete)"))
        assertTrue(!source("ui/home/HomeSection.kt").contains("isPlaceholder"))
    }

    @Test
    fun calendarioNoMuestraSinEventosHoyEnMiDia() {
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()

        assertTrue(!strings.contains("Sin eventos hoy"))
        assertTrue(!source("ui/myday/MyDayScreen.kt").contains("calendar"))
        assertTrue(!source("ui/navigation/ChromeRules.kt").substringAfter("fun overflowMenuFor(").substringBefore("AppDestination.UPCOMING_7_DAYS")
            .contains("MY_CALENDARS"))
    }

    @Test
    fun hojasDeCalendarioSonModalesYRegistranApertura() {
        listOf("ui/calendar/CalendarsSheet.kt", "ui/calendar/EventEditorSheet.kt").forEach { path ->
            val text = source(path)
            assertEquals("$path: una sola hoja", 1, Regex("ModalBottomSheet\\(").findAll(text).count())
            assertTrue("$path: registra la hoja abierta", text.contains("TrackOpenSheet()"))
            listOf("AlertDialog", "Dialog(", "DatePickerDialog", "DueDatePickerDialog", "DueTimePickerDialog").forEach {
                assertTrue("$path no debe usar $it", !text.contains(it))
            }
        }
        assertTrue(!source("ui/calendar/EventEditorSheet.kt").contains("CalendarsSheet("))
        assertTrue(!source("ui/calendar/CalendarScreen.kt").contains("EventEditorSheet("))
        assertTrue(source("ui/shell/AppShell.kt").contains("if (eventState.isOpen && destination == AppDestination.CALENDAR)"))
    }

    @Test
    fun calendarioUsaClavesEstables() {
        assertTrue(source("ui/calendar/CalendarBody.kt").contains("items(rows, key = { it.key })"))
        assertTrue(source("ui/calendar/CalendarsSheet.kt").contains("items(rowsOf(calendars), key = { it.key })"))
        val rows = source("ui/calendar/CalendarBodyRows.kt")
        listOf("\"d:\$date\"", "\"e:\${item.event.instanceKey}:\$date\"", "\"t:\${item.task.taskId}:\$date\"").forEach {
            assertTrue("falta la clave $it", rows.contains(it))
        }
        assertTrue(source("ui/calendar/CalendarsSheet.kt").contains("\"c:\${option.calendar.id}\""))
    }

    @Test
    fun calendarioNoGuardaNegocioEnRememberSaveable() {
        val offenders = File(sourcesRoot, "ui/calendar").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.readText().contains("rememberSaveable") }
            .map { it.name }
            .toList()

        assertTrue("estado de negocio fuera del ViewModel: $offenders", offenders.isEmpty())
    }

    @Test
    fun calendarioSoloUsaDuracionesDeNavMotion() {
        val calendarFiles = File(sourcesRoot, "ui/calendar").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        calendarFiles.forEach { file ->
            listOf("tween(", "spring(", "snap(", "durationMillis", "Settings.Global", "animateFloatAsState").forEach {
                assertTrue("${file.name} no debe usar $it", !file.readText().contains(it))
            }
        }
        val screen = source("ui/calendar/CalendarScreen.kt")
        assertTrue(screen.contains("panelEnter(animated)") && screen.contains("panelExit(animated)"))
        assertTrue(screen.contains("rememberAnimationsEnabled()"))
        assertTrue(source("ui/calendar/EventEditorSheet.kt").contains("editorPanelTransition(animated)"))
    }

    @Test
    fun cuadriculaYFilasTienenObjetivoTactilYDescripcion() {
        val grid = source("ui/calendar/MonthGrid.kt")
        assertTrue(grid.contains("heightIn(min = Spacing.MinTouchTarget)"))
        assertTrue(grid.contains("cellDescription("))
        assertTrue(grid.contains("onClick = onClick"))
        val body = source("ui/calendar/CalendarBody.kt")
        assertTrue(body.contains("clearAndSetSemantics { contentDescription = description }"))
        assertTrue(body.contains("heading()"))
        assertTrue(body.contains("minimumInteractiveComponentSize()"))
        val calendars = source("ui/calendar/CalendarsSheet.kt")
        assertTrue(calendars.contains("Role.Checkbox") && calendars.contains("heightIn(min = Spacing.MinTouchTarget)"))
        val header = source("ui/calendar/CalendarHeader.kt")
        assertTrue(header.contains("stateDescription = gridState") && header.contains("SingleChoiceSegmentedButtonRow("))
        assertTrue(header.contains("selected = state.mode == mode"))
    }

    @Test
    fun pildoraDeEventoUsaCadenasPropias() {
        val chrome = source("ui/shell/FloatingChrome.kt")
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()

        assertTrue(chrome.contains("R.string.pill_event_hint") && chrome.contains("R.string.pill_event_description"))
        assertTrue(strings.contains("name=\"pill_event_hint\"") && strings.contains("name=\"pill_event_description\""))
        assertTrue(chrome.contains("chrome.pill == PillKind.TASK && pill.isOpen"))
    }

    @Test
    fun calendarioNoAnadeImePaddingNiBackHandlerEnShell() {
        assertEquals(1, Regex("BackHandler\\(").findAll(source("ui/shell/AppShell.kt")).count())
        assertEquals(1, Regex("imePadding\\(\\)").findAll(source("ui/shell/FloatingChrome.kt")).count())
        val sheet = source("ui/calendar/EventEditorSheet.kt")
        assertEquals(1, Regex("BackHandler\\(").findAll(sheet).count())
        assertEquals(1, Regex("imePadding\\(\\)").findAll(sheet).count())
        File(sourcesRoot, "ui/calendar").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "EventEditorSheet.kt" }
            .forEach { file ->
                assertTrue("${file.name} no debe usar imePadding", !file.readText().contains("imePadding"))
                assertTrue("${file.name} no debe usar BackHandler", !file.readText().contains("BackHandler"))
            }
    }

    @Test
    fun miDia_ordenEnLaCabecera() {
        assertTrue(source("ui/myday/MyDayScreen.kt").contains("MyDaySortMenu("))
        assertTrue(!source("ui/shell/AppShell.kt").contains("MyDaySortMenu"))
        assertTrue(!source("ui/shell/SectionTopBar.kt").contains("MyDaySortMenu"))
    }

    @Test
    fun sinDependenciasNuevas() {
        val gradle = File(sourcesRoot, "../../../../../../build.gradle.kts").readText()
        val declared = gradle.substringAfter("dependencies {").substringBefore("\n}")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        assertEquals(EXPECTED_DEPENDENCIES, declared)
    }

    @Test
    fun miDiaYaNoUsaQuickAddBar() {
        assertTrue(!File(sourcesRoot, "ui/myday/QuickAddBar.kt").exists())
        val users = uiFiles().filter { it.readText().contains("QuickAddBar") }.map { it.name }

        assertTrue("QuickAddBar sin uso: $users", users.isEmpty())
    }

    @Test
    fun miDiaNoTieneChipsDeFiltro() {
        val screen = source("ui/myday/MyDayScreen.kt")

        assertEquals(0, Regex("FilterChip\\(").findAll(screen).count())
        assertTrue(!screen.contains("MyDayFilter"))
        assertTrue(!screen.contains("my_day_sort"))
    }

    @Test
    fun iconoDeSugerenciasEnBarraSuperiorConInsignia() {
        val bar = source("ui/shell/SectionTopBar.kt")
        val action = bar.substringAfter("private fun SuggestionsAction(").substringBefore("@Preview")
        val actions = bar.substringAfter("actions = {").substringBefore("OverflowMenuButton(")

        assertTrue(action.contains("BadgedBox("))
        assertTrue(action.contains("Badge {"))
        assertTrue(action.contains("Icons.Filled.Lightbulb"))
        assertTrue(action.contains("badgeLabel(count)"))
        assertTrue(actions.contains("if (showSuggestions)"))
    }

    @Test
    fun hojaDeSugerenciasUsaBotonesDeIconoALaDerecha() {
        val sheet = source("ui/myday/SuggestionsSheet.kt")

        val actions = source("ui/components/ActionIcon.kt")

        assertEquals(1, Regex("TonalActionIcon\\(").findAll(sheet).count())
        assertEquals(2, Regex("(?<!Tonal)ActionIcon\\(").findAll(sheet).count())
        assertTrue(actions.contains("FilledTonalIconButton("))
        assertTrue(actions.contains("IconButton(onClick = onClick"))
        assertTrue(sheet.contains("trailingContent"))
        assertTrue(actions.contains("sizeIn(minWidth = Spacing.MinTouchTarget, minHeight = Spacing.MinTouchTarget)"))
        assertTrue(sheet.contains("minWidth = Spacing.MinTouchTarget"))
        assertTrue(sheet.contains("Icons.Filled.Add"))
        assertTrue(sheet.contains("contentDescription = addDescription"))
        assertTrue(sheet.contains("contentDescription = dismissDescription"))
        assertTrue(!sheet.contains("stringResource(R.string.my_day_suggestion_add)"))
        assertTrue(sheet.contains("R.string.my_day_suggestion_add_description, item.title"))
        assertTrue(sheet.contains("R.string.my_day_suggestion_dismiss_description, item.title"))
        assertTrue(!sheet.contains("FilledTonalButton("))
        assertTrue(!sheet.contains("TextButton("))
        assertTrue(sheet.contains("skipPartiallyExpanded = false"))
        assertTrue(!sheet.contains("clickable("))
        assertTrue(!sheet.contains("SnackbarHost"))
    }

    @Test
    fun cadenasObsoletasDeLaHojaRetiradas() {
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()

        assertTrue(!strings.contains("name=\"my_day_suggestion_add\""))
        assertTrue(!strings.contains("name=\"my_day_suggestion_dismiss\""))
        assertTrue(strings.contains(">Añadir a Mi día: %1\$s<"))
        assertTrue(strings.contains(">Descartar: %1\$s<"))
    }

    @Test
    fun miDiaNoDesvaneceLaFilaNuevaNiHaceAsomoAlPasarDeVacio() {
        val screen = source("ui/myday/MyDayScreen.kt")
        val motion = source("ui/components/CompletionMotion.kt")

        assertTrue(screen.contains("rowItemModifier(fadeIn = false)"))
        assertTrue(screen.contains("startedEmpty"))
        assertTrue("vacío solo tras cargar", screen.contains("state.loaded && state.items.isEmpty()"))
        assertTrue(screen.contains("peek = !startedEmpty &&"))
        assertTrue(!screen.contains("Crossfade("))
        assertTrue(!screen.contains("AnimatedContent("))
        assertTrue(!screen.contains("AnimatedVisibility("))
        assertTrue(motion.contains("Modifier.animateItem(fadeInSpec = null)"))
        assertTrue(motion.contains("rememberAnimationsEnabled()"))
    }

    @Test
    fun hojaDeSugerenciasNoUsaTaskRowNiGestos() {
        val sheet = source("ui/myday/SuggestionsSheet.kt")

        listOf(
            "TaskRow(", "SwipeToDismissBox", "swipeable", "rememberTaskActionsSheet", "rememberTaskEditorHost",
            "combinedClickable", "pointerInput",
        ).forEach { assertTrue("no debe usar $it", !sheet.contains(it)) }
    }

    @Test
    fun aperturaAutomaticaSoloEnPrimerPlanoSinReevaluarAlNavegar() {
        val shell = source("ui/shell/AppShell.kt")
        val effect = shell.substringAfter("private fun SuggestionsAutoOpen(").substringBefore("@Composable")
        val claim = effect.indexOf("claimFirstForegroundOfDay()")
        val wait = effect.indexOf("awaitFirstCount()")

        assertTrue(effect.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
        assertTrue("reclama antes de esperar el conteo", claim in 0 until wait)
        assertTrue("sin efecto por destino", !effect.contains("LaunchedEffect(currentDestination") && !effect.contains("LaunchedEffect(destination"))
        assertTrue(effect.contains("shouldAutoOpenSuggestions("))
        assertTrue(!effect.contains("setSuggestionsLastShownDay"))
    }

    @Test
    fun appShellNoTieneAccesoACalendarioEnLaBarraSuperior() {
        val shell = source("ui/shell/AppShell.kt") + source("ui/shell/SectionTopBar.kt")

        assertTrue(!shell.contains("CalendarMonth"))
    }

    @Test
    fun emptyStateExigeAccion() {
        val empty = source("ui/components/EmptyState.kt")

        assertTrue(empty.contains("actionLabel: String? = null,"))
        assertTrue(empty.contains("onAction: (() -> Unit)? = null,"))
        assertTrue(empty.contains("FilledTonalButton("))
        LIST_SCREENS.forEach { assertTrue("$it debe usar EmptyState", source(it).contains("EmptyState(")) }
    }

    @Test
    fun statePageUsaComponentesNativosYDecorativeIcon() {
        val page = source("ui/components/StatePage.kt")

        listOf("DecorativeIcon(", "heading()", "Button(").forEach { assertTrue("falta $it", page.contains(it)) }
        assertTrue(!page.contains("Card("))
    }

    @Test
    fun statePageUsaSoloTokensDeTema() {
        val page = source("ui/components/StatePage.kt")

        assertTrue(!page.contains("Color(0x"))
        assertTrue(!Regex("""\d\.?\d*\.(sp|dp)\b""").containsMatchIn(page))
        assertTrue(page.contains("verticalScroll("))
    }

    @Test
    fun errorPageDelegaEnStatePage() {
        val page = source("ui/components/ErrorPage.kt")

        assertTrue(page.contains("StatePage("))
        listOf("Column(", "Text(", "Button(").forEach { assertTrue("no debe tener $it", !page.contains(it)) }
    }

    @Test
    fun errorPageUsaUnIconoDistintoPorTipo() {
        val page = source("ui/components/ErrorPage.kt")
        val icons = listOf("SearchOff", "Lock", "ErrorOutline", "CloudOff", "LockClock", "WifiOff")

        icons.forEach { assertTrue("falta $it", page.contains("Icons.Filled.$it\n")) }
    }

    @Test
    fun boardScreenUsaErrorPageSiFalta() {
        assertTrue(source("ui/board/BoardScreen.kt").contains("ErrorPage(ErrorPageKind.NOT_FOUND"))
    }

    @Test
    fun errorPageSoloSeUsaEnBoardScreen() {
        val users = uiFiles()
            .filterNot { it.relativeTo(sourcesRoot).path.startsWith("ui/components") }
            .filter { Regex("(?<![A-Za-z])ErrorPage\\(").containsMatchIn(it.readText()) }
            .map { it.relativeTo(sourcesRoot).path }

        assertEquals(listOf("ui/board/BoardScreen.kt"), users)
    }

    @Test
    fun altaRapidaNoUsaDialogos() {
        quickAddFiles().forEach { file ->
            val text = file.readText()
            listOf("AlertDialog", "Dialog(", "DatePickerDialog", "DueDatePickerDialog", "DueTimePickerDialog")
                .forEach { forbidden ->
                    assertTrue("${file.name} no debe usar $forbidden", !text.contains(forbidden))
                }
        }
    }

    @Test
    fun altaRapidaYaNoUsaBottomSheet() {
        val sheets = quickAddFiles().sumOf { Regex("ModalBottomSheet\\(").findAll(it.readText()).count() }

        assertEquals(0, sheets)
        assertTrue(!source("ui/shell/FloatingChrome.kt").contains("ModalBottomSheet("))
    }

    @Test
    fun bottomSheetsSonModalesConAsaYAccionDeCierre() {
        val sheetFiles = uiFiles().filter { it.readText().contains("ModalBottomSheet(") }
        val names = sheetFiles.map { it.name }.toSet()

        assertEquals(
            setOf(
                "TaskActionsSheet.kt", "AssignToBoardHost.kt", "MoveToListHost.kt", "TaskEditorSheet.kt", "SuggestionsSheet.kt",
                "CalendarsSheet.kt", "EventEditorSheet.kt", "RegionPickerSheet.kt", "AccountSheet.kt",
            ),
            names,
        )
        sheetFiles.forEach { file ->
            val text = file.readText()
            assertTrue("${file.name}: asa", text.contains("dragHandle ="))
            assertTrue("${file.name}: asa accesible", text.contains("R.string.quick_add_drag_handle"))
            assertTrue("${file.name}: sheetState", text.contains("sheetState ="))
            assertTrue("${file.name}: cerrar", text.contains("R.string.quick_add_close"))
            assertTrue("${file.name}: título anunciado", text.contains("paneTitle"))
            if (file.name != "SuggestionsSheet.kt") {
                assertTrue("${file.name}: registra la hoja abierta", text.contains("TrackOpenSheet()"))
            }
        }
    }

    @Test
    fun selectorDeDestinoCompartidoNoEsDialogo() {
        val picker = source("ui/tasks/DestinationPickerDialog.kt")
        val options = picker.substringAfter("fun DestinationOptions(").substringBefore("@Composable")

        assertTrue(options.contains("RadioOption("))
        assertTrue(!options.contains("AlertDialog"))
    }

    @Test
    fun asignarATableroUsaBottomSheet() {
        val host = source("ui/tasks/AssignToBoardHost.kt")

        assertTrue(host.contains("ModalBottomSheet("))
        assertTrue(host.contains("DestinationOptions("))
        assertTrue(!host.contains("DestinationPickerDialog("))
    }

    @Test
    fun textosNuevosVivenEnStringsXml() {
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()
        val literal = Regex("(Text\\(\\s*\"|contentDescription\\s*=\\s*\")")
        NEW_UI_FILES.forEach { path ->
            val text = source(path)
            assertTrue("$path tiene texto literal", !literal.containsMatchIn(text))
            Regex("R\\.string\\.([a-z0-9_]+)").findAll(text).map { it.groupValues[1] }.forEach { name ->
                assertTrue("$path usa R.string.$name inexistente", strings.contains("name=\"$name\""))
            }
        }
    }

    @Test
    fun cadenasDelCalendarioExistenYLasRetiradasNoQuedan() {
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()

        listOf("calendar_mode_agenda", "calendar_permission_allow", "pill_event_hint", "event_sheet_title", "event_added", "event_no_writable")
            .forEach { assertTrue("falta $it", strings.contains("name=\"$it\"")) }
        listOf("calendar_placeholder", "coming_soon").forEach {
            assertTrue("$it ya no debe existir", !strings.contains("name=\"$it\""))
        }
    }

    @Test
    fun sinDpLiteralesEnPantallas() {
        val literal = Regex("\\d\\.(dp|sp)\\b")
        val violations = uiFiles()
            .filter { file -> !file.relativeTo(sourcesRoot).path.startsWith("ui/theme") }
            .filter { literal.containsMatchIn(it.readText()) }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("dp literales de espaciado en: $violations", violations.isEmpty())
    }

    @Test
    fun editorUsaDesplegableParaPrioridad() {
        val source = source("ui/tasks/TaskEditorPills.kt")

        assertTrue(source.contains("TaskPriority.entries"))
        assertTrue(!Regex("RadioOption\\([^)]*priority", RegexOption.DOT_MATCHES_ALL).containsMatchIn(source))
    }

    @Test
    fun selectorDeRepeticion_tomaLosTextosDeRecursosYNoLlevaLaRegla() {
        val section = File(sourcesRoot, "ui/tasks/RepeatSection.kt").readText()
        val pills = source("ui/tasks/TaskEditorPills.kt")
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()

        listOf("Repetir", "No se repite", "Cada día", "Entre semana", "Cada semana", "Cada mes", "Cada año", "Personalizada…")
            .forEach { label ->
                assertTrue("$label debe vivir en strings.xml", strings.contains(">$label<"))
                assertTrue("$label no debe estar escrito en el composable", !section.contains("\"$label\""))
            }
        assertTrue(pills.contains("R.string.task_repeat_hint_needs_due"))
        assertTrue("el composable no construye reglas", !section.contains("TaskRepeat("))
        assertTrue("el composable no calcula fechas", !section.contains("nextDueAt"))
    }

    @Test
    fun fondoDeBorrarUsaColoresDeError() {
        val style = source("ui/components/RowActionStyle.kt")

        assertTrue(style.contains("RowAction.DELETE -> MaterialTheme.colorScheme.errorContainer"))
        assertTrue(style.contains("RowAction.DELETE -> MaterialTheme.colorScheme.onErrorContainer"))
        assertTrue(style.contains("RowAction.DELETE -> Icons.Filled.Delete"))
        assertTrue(source("ui/components/TaskRow.kt").contains("action.swipeContentColor()"))
    }

    @Test
    fun accionBorrarTieneEtiquetaEnRecursos() {
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()

        assertTrue(strings.contains("""name="row_action_delete">Borrar tarea</string>"""))
        assertTrue(strings.contains("""name="swipe_delete">Borrar</string>"""))
        assertTrue(strings.contains("""name="task_deleted_undo">Deshacer</string>"""))
    }

    @Test
    fun appShellTieneSnackbarHostAnclado() {
        val shell = source("ui/shell/AppShell.kt")

        assertTrue(shell.contains("snackbarHost"))
        assertTrue(shell.contains("Spacing.ChromeClearance"))
        assertTrue(shell.contains("Spacing.HubBarClearance"))
        assertTrue(shell.contains("NoticeKind.UNDO"))
        assertTrue(shell.contains("navigationBarsPadding()") && shell.contains("imePadding()"))
        assertTrue("sin duración literal", !Regex("""Duration\.of|\d+\.seconds|[0-9_]{4,}L""").containsMatchIn(shell))
    }

    @Test
    fun laVentanaDeDeshacerSoloSeDefineEnDominio() {
        val violations = (uiFiles() + File(sourcesRoot, "data").walkTopDown().filter { it.extension == "kt" })
            .filter { it.readText().contains("UNDO_WINDOW") && it.name != "TaskDeletionViewModel.kt" }
            .map { it.name }

        assertTrue("UNDO_WINDOW solo lo usa el ViewModel de borrado: $violations", violations.isEmpty())
        assertTrue(source("ui/tasks/TaskDeletionViewModel.kt").contains("UNDO_WINDOW"))
        assertTrue(!source("ui/tasks/TaskDeletionViewModel.kt").contains("ofSeconds"))
        assertTrue(source("domain/task/DeletionUndoWindow.kt").contains("val UNDO_WINDOW: Duration"))
    }

    @Test
    fun taskRowAnimaLaCompletada() {
        val row = source("ui/components/TaskRow.kt")
        val checkbox = source("ui/components/CompleteCheckbox.kt")

        assertTrue(row.contains("Animatable("))
        assertTrue(row.contains("animateFloatAsState("))
        assertTrue(!row.contains("TextDecoration.LineThrough"))
        assertTrue(checkbox.contains("graphicsLayer"))
        assertTrue(checkbox.contains("Animatable("))
    }

    @Test
    fun lasListasAnimanElMovimientoDeLasFilas() {
        LIST_SCREENS.forEach { path ->
            val call = if (path == "ui/myday/MyDayScreen.kt") "rowItemModifier(fadeIn = false)" else "rowItemModifier()"
            assertTrue("$path debe usar $call", source(path).contains("modifier = $call"))
        }
        val motion = source("ui/components/CompletionMotion.kt")
        assertTrue(motion.contains("Modifier.animateItem()"))
        assertTrue(motion.contains("animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)"))
    }

    @Test
    fun filaNoSeAsientaEnDescartada_paraSobrevivirABorrarYRestaurar() {
        val row = source("ui/components/TaskRow.kt")

        assertTrue(row.contains("confirmValueChange"))
        assertTrue("sin onDismiss: dejaría el estado descartado guardado", !row.contains("onDismiss = {"))
        assertTrue(!row.contains("state.reset()"))
    }

    @Test
    fun lasPantallasDeTareasReciben_onDelete() {
        EDITOR_HOST_SCREENS.forEach { path ->
            val text = source(path)
            assertTrue("$path debe recibir onDelete", text.contains("onDelete: (String) -> Unit"))
            assertTrue("$path debe pasar onDelete al editor", text.contains("rememberTaskEditorHost(viewModelFactory, onDelete)"))
        }
        assertTrue(source("ui/shell/AppShell.kt").contains("onDelete = deletion::delete"))
    }

    @Test
    fun pildoraExponeFilaDeAtajos() {
        val row = source("ui/shell/QuickDateShortcutRow.kt")

        assertTrue(row.contains("LazyRow("))
        assertTrue(row.contains("FilterChip("))
        listOf(
            "pill_shortcut_tomorrow_morning",
            "pill_shortcut_next_week",
            "pill_shortcut_someday",
            "pill_shortcut_pick",
        ).forEach { assertTrue("falta R.string.$it", row.contains("R.string.$it")) }
        assertTrue(row.contains("Icons.Filled.Check"))
        assertTrue(row.contains("Icons.Filled.CalendarMonth"))
        assertTrue(source("ui/shell/FloatingChrome.kt").contains("QuickDateShortcutRow("))
    }

    @Test
    fun pildoraCerradaNoMuestraAtajos() {
        val chrome = source("ui/shell/FloatingChrome.kt")
        val collapsed = chrome.substringAfter("private fun CollapsedPill(").substringBefore("@Composable")

        assertTrue(!collapsed.contains("QuickDateShortcutRow("))
        assertTrue(!collapsed.contains("QuickAddListPanel("))
        assertTrue(chrome.substringAfter("private fun ExpandedPill(").contains("QuickDateShortcutRow("))
    }

    @Test
    fun pildoraReutilizaDueDatePickerDialog() {
        val chrome = source("ui/shell/FloatingChrome.kt")

        assertTrue(chrome.contains("DueDatePickerDialog("))
        assertTrue(chrome.contains("if (state.isDatePickerOpen)"))
        assertTrue(source("ui/tasks/TaskDuePickers.kt").contains("internal fun DueDatePickerDialog("))
        assertEquals(1, uiFiles().sumOf { Regex("DatePickerDialog\\(").findAll(it.readText()).count() - Regex("DueDatePickerDialog\\(").findAll(it.readText()).count() })
    }

    @Test
    fun pildoraNoAnadeInsetsParalelos() {
        PILL_FILES.filter { it != "ui/shell/FloatingChrome.kt" }.forEach { path ->
            val text = source(path)
            listOf("imePadding", "navigationBarsPadding", "windowInsetsPadding", "WindowInsets").forEach {
                assertTrue("$path no debe usar $it", !text.contains(it))
            }
        }
        assertEquals(1, Regex("imePadding\\(\\)").findAll(source("ui/shell/FloatingChrome.kt")).count())
    }

    @Test
    fun pildoraAtajosYPanelTienenDescripcion() {
        assertTrue(source("ui/shell/QuickDateShortcutRow.kt").contains("R.string.pill_shortcuts_description"))
        val panel = source("ui/shell/QuickAddListPanel.kt")
        assertTrue(panel.contains("R.string.pill_list_panel_description"))
        assertTrue(panel.contains("R.string.pill_list_row_description"))
        assertTrue(panel.contains("R.string.pill_list_selected"))
    }

    @Test
    fun pildoraTieneBotonDeListaEnElCampo() {
        val chrome = source("ui/shell/FloatingChrome.kt")
        val field = chrome.substringAfter("OutlinedTextField(").substringBefore("keyboardOptions")

        assertTrue(field.contains("trailingIcon = {"))
        assertTrue(!field.contains("leadingIcon"))
        assertTrue(field.contains("PillListButton("))
        assertTrue(chrome.contains("R.string.pill_list_button"))
        assertTrue(chrome.contains("stateDescription = stateText"))
        assertTrue(chrome.contains("R.string.pill_list_panel_open"))
        assertTrue(chrome.contains("R.string.pill_list_panel_closed"))
    }

    @Test
    fun pildoraBotonDeListaNoEsLeadingIcon() {
        assertTrue(!source("ui/shell/FloatingChrome.kt").contains("leadingIcon"))
    }

    @Test
    fun pildoraAbiertaOcupaElAnchoYNoTieneRedondeo() {
        val expanded = source("ui/shell/FloatingChrome.kt").substringAfter("private fun ExpandedPill(").substringBefore("@Composable")
        val container = expanded.substringBefore("Column(")

        assertTrue(container.contains("shape = RectangleShape"))
        assertTrue(container.contains("fillMaxWidth()"))
        assertTrue("el contenedor no lleva margen exterior", !container.contains("padding("))
        assertTrue("la rama abierta no usa formas del tema", !expanded.contains("MaterialTheme.shapes."))
        assertTrue(expanded.contains("shape = RectangleShape,\n                    trailingIcon"))
    }

    @Test
    fun pildoraImePaddingEnvuelveAlContenedorYNavBarVaDentro() {
        val chrome = source("ui/shell/FloatingChrome.kt")
        val outer = chrome.substringAfter("fun BoxScope.FloatingChrome(").substringBefore("@Composable\nprivate fun CollapsedPill")
        val expanded = chrome.substringAfter("private fun ExpandedPill(").substringBefore("@Composable")

        assertEquals(1, Regex("imePadding\\(\\)").findAll(chrome).count())
        assertTrue(outer.contains("Box(modifier = modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding())"))
        assertTrue("navigationBarsPadding dentro del Surface abierto", expanded.substringAfter("Surface(").contains("navigationBarsPadding()"))
        assertTrue("sin navigationBarsPadding fuera del Surface abierto", !expanded.substringBefore("Surface(").contains("navigationBarsPadding()"))
    }

    @Test
    fun panelDeListasEsRectoEnLaPildoraExpandida() {
        val panel = source("ui/shell/QuickAddListPanel.kt")

        assertTrue(panel.contains("shape = RectangleShape"))
        assertTrue(!panel.contains("MaterialTheme.shapes."))
    }

    @Test
    fun pildoraAlCompactarOcultaElTecladoYQuitaElFoco() {
        val chrome = source("ui/shell/FloatingChrome.kt")
        val closing = chrome.substringAfter("} else if (wasOpen) {").substringBefore("wasOpen = false")

        assertTrue(closing.contains("keyboardController?.hide()"))
        assertTrue(closing.contains("focusManager.clearFocus()"))
        assertTrue(chrome.contains("LocalSoftwareKeyboardController.current"))
        assertTrue(chrome.contains("LocalFocusManager.current"))
        assertTrue("la píldora colapsada no recibe foco programático", !chrome.contains("pillFocus"))
    }

    @Test
    fun veloUsaElScrimNativoYCubreLaVentana() {
        val scrim = source("ui/shell/QuickAddScrim.kt")
        val shell = source("ui/shell/AppShell.kt")

        assertTrue(scrim.contains("BottomSheetDefaults.ScrimColor"))
        assertTrue(scrim.contains("fillMaxSize()"))
        assertTrue(scrim.contains("indication = null"))
        assertTrue(shell.contains("QuickAddScrim("))
    }

    @Test
    fun veloSoloExisteConLaPildoraAbierta() {
        assertTrue(source("ui/shell/QuickAddScrim.kt").contains("AnimatedVisibility(\n        visible = visible,"))
        assertTrue(source("ui/shell/AppShell.kt").contains("QuickAddScrim(visible = quickAddState.isOpen"))
    }

    @Test
    fun veloTieneDescripcionYNoUsaDialogos() {
        val scrim = source("ui/shell/QuickAddScrim.kt")

        assertTrue(scrim.contains("R.string.pill_scrim_description"))
        assertTrue(scrim.contains("onClickLabel = label"))
        listOf("Dialog(", "ModalBottomSheet(", "Popup(").forEach {
            assertTrue("el velo no debe usar $it", !scrim.contains(it))
        }
    }

    @Test
    fun appShellSuperponeVeloYPildoraSobreElScaffold() {
        val shell = source("ui/shell/AppShell.kt")
        val scaffold = shell.indexOf("Scaffold(")
        val scrim = shell.indexOf("QuickAddScrim(")
        val chrome = shell.indexOf("FloatingChrome(")

        assertTrue(scaffold in 0 until scrim)
        assertTrue(scrim in 0 until chrome)
        assertTrue("el cromo ya no vive dentro del contenido del Scaffold", !shell.substringAfter("{ innerPadding ->").substringBefore("QuickAddScrim(").contains("FloatingChrome("))
    }

    @Test
    fun veloLlamaAClose() {
        assertTrue(source("ui/shell/AppShell.kt").contains("onDismiss = quickAdd::close"))
    }

    @Test
    fun veloNoRegistraBackHandlePropio() {
        assertTrue(!source("ui/shell/QuickAddScrim.kt").contains("BackHandler"))
    }

    @Test
    fun appShellMantieneUnUnicoBackHandleDeLaPildora() {
        val shell = source("ui/shell/AppShell.kt")

        assertEquals(1, Regex("BackHandler\\(").findAll(shell).count())
        assertTrue(shell.contains("BackHandler(enabled = quickAddState.isOpen) { quickAdd.onBack() }"))
    }

    @Test
    fun veloYTransicionesRespetanLaEscalaDeAnimacion() {
        val scrim = source("ui/shell/QuickAddScrim.kt")
        val host = source("ui/navigation/AppNavHost.kt")
        val motion = source("ui/navigation/NavMotion.kt")

        assertTrue(scrim.contains("rememberAnimationsEnabled()"))
        assertTrue(scrim.contains("EnterTransition.None") && scrim.contains("ExitTransition.None"))
        assertTrue(host.contains("rememberAnimationsEnabled()"))
        assertTrue(motion.contains("animationsEnabled: Boolean"))
        listOf(scrim, host, motion).forEach { assertTrue(!it.contains("Settings.Global")) }
    }

    @Test
    fun navHostDefineLasCuatroTransicionesUnaVez() {
        val host = source("ui/navigation/AppNavHost.kt")
        val navHostCall = host.substringAfter("    NavHost(\n").substringBefore("    ) {")
        val routes = host.substringAfter("    NavHost(\n").substringAfter("    ) {")

        listOf("enterTransition", "exitTransition", "popEnterTransition", "popExitTransition").forEach {
            assertEquals("$it en NavHost", 1, Regex("\\b$it = ").findAll(navHostCall).count())
            assertTrue("$it no se sobrescribe por ruta", !routes.contains(it))
        }
    }

    @Test
    fun navMotionUsaZoomYFundidoSinDeslizamientos() {
        val motion = source("ui/navigation/NavMotion.kt")

        listOf("scaleIn(", "scaleOut(", "fadeIn(", "fadeOut(").forEach { assertTrue("falta $it", motion.contains(it)) }
        listOf("slideIn", "slideOut").forEach { assertTrue("no debe usar $it", !motion.contains(it)) }
    }

    @Test
    fun duracionesDeLaNavegacionSoloEnNavMotion() {
        val host = source("ui/navigation/AppNavHost.kt")

        assertTrue(!host.contains("tween("))
        assertTrue(!host.contains(".dp"))
        assertTrue(source("ui/navigation/NavMotion.kt").contains("const val ExitFadeMillis"))
    }

    @Test
    fun panelDeListasSeAnimaConEntradaYSalidaQueRespetanLaEscala() {
        val chrome = source("ui/shell/FloatingChrome.kt")
        val expanded = chrome.substringAfter("private fun ExpandedPill(").substringBefore("@Composable")
        val motion = source("ui/navigation/NavMotion.kt")

        assertTrue(expanded.contains("AnimatedVisibility(\n                visible = state.isListPanelOpen,"))
        assertTrue(expanded.contains("enter = panelEnter(animated)") && expanded.contains("exit = panelExit(animated)"))
        assertTrue(expanded.contains("rememberAnimationsEnabled()"))
        assertTrue(motion.contains("expandVertically(") && motion.contains("shrinkVertically("))
        assertTrue(!chrome.contains("tween(") && !chrome.contains("animateContentSize"))
        assertTrue(expanded.contains("QuickAddListPanel("))
    }

    @Test
    fun pildoraListasSinDialogoNiHoja() {
        PILL_FILES.forEach { path ->
            val text = source(path)
            assertTrue("$path no debe usar ModalBottomSheet(", !text.contains("ModalBottomSheet("))
            assertTrue("$path no debe usar DropdownMenu", !text.contains("DropdownMenu("))
            assertTrue("$path no debe usar AlertDialog(", !text.contains("AlertDialog("))
        }
        assertTrue(!source("ui/shell/QuickAddListPanel.kt").contains("Dialog("))
    }

    @Test
    fun pildoraListaMuestraConteoConPlural() {
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()
        val plural = strings.substringAfter("<plurals name=\"pill_list_task_count\">").substringBefore("</plurals>")

        assertTrue(plural.contains("<item quantity=\"one\">%d tarea</item>"))
        assertTrue(plural.contains("<item quantity=\"other\">%d tareas</item>"))
        assertTrue(source("ui/shell/QuickAddListPanel.kt").contains("pluralStringResource(R.plurals.pill_list_task_count"))
    }

    @Test
    fun pildoraListaSeleccionadaNoDependeSoloDelColor() {
        val panel = source("ui/shell/QuickAddListPanel.kt")

        assertTrue(panel.contains("Icons.Filled.Check"))
        assertTrue(panel.contains("selectable(selected = option.selected"))
        assertTrue(panel.contains("stateDescription = selectedText"))
    }

    @Test
    fun pildoraListaUsaClavesEstablesPorId() {
        val panel = source("ui/shell/QuickAddListPanel.kt")

        assertTrue(panel.contains("key = { it.id }"))
        assertTrue(source("ui/shell/QuickDateShortcutRow.kt").contains("key = { it.first }"))
    }

    @Test
    fun pildoraNoGuardaEstadoPropioQueSobrevivaAlViewModel() {
        PILL_FILES.forEach { path ->
            assertTrue("$path no debe usar rememberSaveable", !source(path).contains("rememberSaveable"))
        }
    }

    @Test
    fun pildoraDevuelveElFocoAlCampoAlCerrarPanelOSelector() {
        val chrome = source("ui/shell/FloatingChrome.kt")

        assertTrue(chrome.contains("LaunchedEffect(pill.isListPanelOpen, pill.isDatePickerOpen)"))
        assertTrue(chrome.contains("inputFocus.requestFocus()"))
    }

    @Test
    fun pildoraSinControlesDePrioridadNiAsignado() {
        PILL_FILES.forEach { path ->
            val text = source(path)
            assertTrue("$path no debe referenciar TaskPriority", !text.contains("TaskPriority"))
            assertTrue("$path no debe referenciar assignee", !text.contains("assignee", ignoreCase = true))
        }
        quickAddFiles().forEach { file ->
            if (file.name == "QuickAddViewModel.kt") return@forEach
            assertTrue(!file.readText().contains("assignee", ignoreCase = true))
        }
    }

    @Test
    fun dimensionesDeLaPildoraEnSpacing() {
        assertTrue(source("ui/theme/Spacing.kt").contains("PillListPanelMaxHeight = 240.dp"))
        assertTrue(source("ui/shell/QuickAddListPanel.kt").contains("Spacing.PillListPanelMaxHeight"))
    }

    @Test
    fun horaDelAtajoDeMananaViveSoloEnElDominio() {
        val literal = Regex("LocalTime\\.of\\(|atTime\\(|MORNING_TIME")
        PILL_FILES.forEach { path ->
            assertTrue("$path no debe fijar horas", !literal.containsMatchIn(source(path)))
        }
        assertTrue(!source("ui/quickadd/QuickAddViewModel.kt").contains("LocalTime"))
    }

    @Test
    fun editorEsUnaHojaYNoUnDialogo() {
        val sheet = source("ui/tasks/TaskEditorSheet.kt")

        assertTrue(sheet.contains("ModalBottomSheet("))
        assertTrue(sheet.contains("skipPartiallyExpanded = true"))
        EDITOR_FILES.forEach { assertTrue("$it usa Dialog(", !source(it).contains("Dialog(")) }
    }

    @Test
    fun editorTieneCabeceraConCerrarMenuYGuardar() {
        val sheet = source("ui/tasks/TaskEditorSheet.kt")

        listOf("R.string.quick_add_close", "R.string.task_save", "R.string.task_editor_menu", "DropdownMenu(")
            .forEach { assertTrue("falta $it", sheet.contains(it)) }
        assertTrue(sheet.contains("R.string.row_action_delete"))
        assertTrue(sheet.contains("enabled = state.isDirty"))
    }

    @Test
    fun editorLlamaNotasALaDescripcion() {
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()

        assertTrue(strings.contains("<string name=\"task_notes_label\">Notas</string>"))
        assertTrue(!strings.contains("task_description_label"))
    }

    @Test
    fun editorTieneLasSietePildorasEnOrden() {
        val pills = source("ui/tasks/TaskEditorPills.kt")
        val order = listOf(
            "R.string.task_pill_complete",
            "R.string.task_pill_my_day",
            "R.string.task_pill_due",
            "R.string.reminder_title",
            "R.string.task_repeat_label",
            "R.string.task_priority_label",
            "R.string.task_assignee_label",
        )

        val positions = order.map { pills.indexOf(it) }
        assertTrue("faltan píldoras: $positions", positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun pildorasDelEditorUsanChipsNativos() {
        val pills = source("ui/tasks/TaskEditorPills.kt")

        assertTrue(pills.contains("FilterChip("))
        assertTrue(pills.contains("AssistChip("))
        assertTrue(!pills.contains("Surface("))
        assertTrue(!pills.contains("clickable"))
    }

    @Test
    fun subtareasDelEditorNoUsanDialogos() {
        listOf("ui/tasks/SubtaskEditFields.kt", "ui/tasks/SubtasksSection.kt", "ui/tasks/SubtaskDraftsSection.kt")
            .forEach { path ->
                val text = source(path)
                assertTrue("$path abre diálogos de fecha", !text.contains("DueDatePickerDialog") && !text.contains("DueTimePickerDialog"))
                assertTrue("$path debe dar clave estable a las filas", path.endsWith("SubtaskEditFields.kt") || text.contains("key("))
            }
    }

    @Test
    fun editorNoUsaDialogosApilados() {
        val forbidden = listOf("AlertDialog", "Dialog(", "DatePickerDialog", "DueDatePickerDialog", "DueTimePickerDialog")
        EDITOR_FILES.forEach { path ->
            val text = source(path)
            forbidden.forEach { assertTrue("$path contiene $it", !text.contains(it)) }
        }
    }

    @Test
    fun editorGestionaAtrasDentroDeLaHoja() {
        assertTrue(source("ui/tasks/TaskEditorSheet.kt").contains("BackHandler("))
        assertEquals(1, Regex("BackHandler\\(").findAll(source("ui/shell/AppShell.kt")).count())
    }

    @Test
    fun editorAplicaInsetsEnElContenidoDeLaHoja() {
        val sheet = source("ui/tasks/TaskEditorSheet.kt")

        assertEquals(1, Regex("imePadding\\(\\)").findAll(sheet).count())
        val navigation = sheet.indexOf("navigationBarsPadding()")
        val ime = sheet.indexOf("imePadding()")
        val height = sheet.indexOf(".height(")
        assertTrue("el orden debe ser navigationBarsPadding, imePadding, height", navigation in 0 until ime && ime < height)
        assertTrue(!sheet.contains("WindowInsets") && !sheet.contains("windowInsetsPadding"))
        EDITOR_FILES.filter { it != "ui/tasks/TaskEditorSheet.kt" }
            .forEach { assertTrue("$it añade imePadding", !source(it).contains("imePadding")) }
    }

    @Test
    fun editorDevuelveElFocoALaPildora() {
        val pills = source("ui/tasks/TaskEditorPills.kt")

        listOf("val due = FocusRequester()", "val reminder = FocusRequester()", "val list = FocusRequester()")
            .forEach { assertTrue("falta $it", pills.contains(it)) }
        assertTrue(source("ui/tasks/TaskEditorSheet.kt").contains("pillFocus.forPanel(lastPanel)"))
    }

    @Test
    fun editorNoDefineDuracionesPropias() {
        val forbidden = listOf("tween(", "spring(", "snap(", "Millis")
        EDITOR_FILES.forEach { path ->
            val text = source(path)
            forbidden.forEach { assertTrue("$path define una duración con $it", !text.contains(it)) }
            assertTrue("$path anima un valor propio", !Regex("animate\\w*AsState").containsMatchIn(text))
        }
    }

    @Test
    fun editorCierraConHideAntesDeVaciar() {
        val sheet = source("ui/tasks/TaskEditorSheet.kt")
        val closing = sheet.substringAfter("LaunchedEffect(state.isClosing)")

        assertTrue(closing.indexOf("sheetState.hide()") in 0 until closing.indexOf("onClosed.value()"))
    }

    @Test
    fun editorAnunciaPaneTitle() {
        assertTrue(source("ui/tasks/TaskEditorSheet.kt").contains("paneTitle = title"))
        assertTrue(source("ui/tasks/TaskEditorPanels.kt").contains("paneTitle = title"))
    }

    @Test
    fun editorBorraPorOnDeleteYNoPorElRepositorio() {
        assertTrue(!source("ui/tasks/TaskEditorViewModel.kt").contains("deleteTask("))
        assertTrue(!source("ui/tasks/TaskEditorViewModel.kt").contains("fun delete("))
        val host = source("ui/tasks/TaskEditorHost.kt")
        assertTrue(host.contains("onDelete: (String) -> Unit"))
        assertTrue(host.contains("editorState.taskId?.let(onDelete)"))
    }

    @Test
    fun editorNoAnadeRutas() {
        val destinations = source("ui/navigation/AppDestination.kt")

        assertTrue(!destinations.contains("EDITOR", ignoreCase = true))
    }

    @Test
    fun taskEditorDialogYaNoExiste() {
        assertTrue(!File(sourcesRoot, "ui/tasks/TaskEditorDialog.kt").exists())
        assertTrue(!File(sourcesRoot, "ui/tasks/RemindersSection.kt").exists())
        assertTrue(!File(sourcesRoot, "ui/tasks/ReminderPanel.kt").exists())
    }

    @Test
    fun editorNoGuardaEstadoDeNegocioEnRememberSaveable() {
        listOf(
            "ui/tasks/TaskEditorSheet.kt",
            "ui/tasks/TaskEditorPills.kt",
            "ui/tasks/TaskEditorPanels.kt",
            "ui/tasks/AnimatedSubtaskRow.kt",
            "ui/tasks/SubtaskEditFields.kt",
            "ui/tasks/SubtasksSection.kt",
            "ui/tasks/SubtaskDraftsSection.kt",
        ).forEach { path -> assertTrue("$path usa rememberSaveable", !source(path).contains("rememberSaveable")) }
    }

    @Test
    fun editorFijaLaAlturaDeLaHojaAl65PorCiento() {
        val spacing = source("ui/theme/Spacing.kt")
        val sheet = source("ui/tasks/TaskEditorSheet.kt")

        assertTrue(spacing.contains("const val EditorSheetHeightFraction = 0.65f"))
        assertTrue(spacing.contains("val EditorSheetMinHeight = 400.dp"))
        assertTrue(spacing.contains("val EditorDragHandleHeight"))
        listOf("Spacing.EditorSheetHeightFraction", "Spacing.EditorDragHandleHeight", "Spacing.EditorSheetMinHeight", "LocalWindowInfo", "LocalDensity", ".height(")
            .forEach { assertTrue("falta $it", sheet.contains(it)) }
        assertTrue("sin LocalConfiguration", !sheet.contains("LocalConfiguration"))
        assertTrue("sin literal de la fracción", !sheet.contains("0.65"))
        assertTrue("el contenido no llena la ventana", !sheet.contains("fillMaxSize()"))
    }

    @Test
    fun datePickerCompactoSoloEnElEditor() {
        assertTrue(source("ui/tasks/TaskEditorPanels.kt").contains("compact = true"))
        assertTrue(!source("ui/tasks/TaskActionsSheet.kt").contains("compact"))
        val pickers = source("ui/tasks/TaskDuePickers.kt")
        assertTrue(pickers.contains("compact: Boolean = false"))
        assertTrue(pickers.contains("title = null, headline = null, showModeToggle = false"))
    }

    @Test
    fun editorRecordatorioEsUnMenu() {
        val pills = source("ui/tasks/TaskEditorPills.kt")
        val reminder = pills.substringAfter("private fun ReminderPill(").substringBefore("private fun RepeatPill(")

        assertTrue(reminder.contains("PillMenu("))
        listOf("reminder_later_today", "reminder_tomorrow", "reminder_next_week", "reminder_custom", "reminder_remove")
            .forEach { assertTrue("falta R.string.$it", reminder.contains("R.string.$it")) }
        assertTrue(pills.substringAfter("private fun PillMenu(").substringBefore("private fun ReminderPill(").contains("DropdownMenu("))
        assertTrue(!File(sourcesRoot, "ui/tasks/ReminderPanel.kt").exists())
        assertTrue(!source("ui/tasks/TaskEditorUiState.kt").contains("object Reminder"))
    }

    @Test
    fun editorRepetirEsUnMenu() {
        val pills = source("ui/tasks/TaskEditorPills.kt")
        val repeat = pills.substringAfter("private fun RepeatPill(").substringBefore("private fun PriorityPill(")

        assertTrue(repeat.contains("PillMenu("))
        assertTrue(repeat.contains("RepeatPreset.entries"))
        assertTrue(repeat.contains("R.string.task_repeat_hint_needs_due"))
        assertTrue(pills.contains("RepeatCustomFields("))
        assertTrue(!source("ui/tasks/TaskEditorPanels.kt").contains("RepeatPanel"))
        assertTrue(!source("ui/tasks/TaskEditorUiState.kt").contains("object Repeat"))
    }

    @Test
    fun editorAnimaElCambioDePanel() {
        val sheet = source("ui/tasks/TaskEditorSheet.kt")

        assertTrue(sheet.contains("AnimatedContent("))
        assertTrue(sheet.contains("transitionSpec = { editorPanelTransition(animated) }"))
        assertTrue(sheet.contains("import com.simposio.taskeer.ui.navigation.editorPanelTransition"))
    }

    @Test
    fun editorAnimaSeccionesYFilas() {
        val animated = ANIMATED_EDITOR_FILES.map { source(it) }
        assertTrue(animated.all { it.contains("AnimatedVisibility(") || it.contains("animateContentSize(") || it.contains("AnimatedContent(") })
        listOf("ui/tasks/SubtasksSection.kt", "ui/tasks/SubtaskDraftsSection.kt").forEach { path ->
            val text = source(path)
            assertTrue("$path debe animar el tamaño", text.contains("animateContentSize(editorSizeSpec(animated))"))
            assertTrue("$path debe usar la fila animada", text.contains("AnimatedSubtaskRow("))
            assertTrue("$path debe dar clave a las filas", text.contains("key("))
        }
        val row = source("ui/tasks/AnimatedSubtaskRow.kt")
        assertTrue(row.contains("MutableTransitionState"))
        assertTrue("el borrado diferido también corre al salir de composición", row.contains("onDispose"))
        assertTrue(source("ui/tasks/TaskEditorPanels.kt").contains("editorRowEnter(animated)"))
    }

    @Test
    fun editorAnimaElValorDeLasPildoras() {
        val pills = source("ui/tasks/TaskEditorPills.kt")

        assertTrue(pills.contains("AnimatedContent("))
        assertTrue(pills.contains("transitionSpec = { editorValueTransition(animated) }"))
        assertTrue(pills.contains("R.string.task_pill_description"))
        assertTrue("la descripción lleva el valor vigente", pills.contains("contentDescription = description"))
    }

    @Test
    fun editorAnimaSoloConHelpersDeNavMotion() {
        ANIMATED_EDITOR_FILES.forEach { path ->
            val text = source(path)
            assertTrue("$path no usa rememberAnimationsEnabled", text.contains("import com.simposio.taskeer.ui.components.rememberAnimationsEnabled"))
            assertTrue("$path no usa helpers de NavMotion", text.contains("import com.simposio.taskeer.ui.navigation.editor"))
        }
        val motion = source("ui/navigation/NavMotion.kt")
        listOf("object EditorMotion", "fun editorPanelTransition(", "fun editorValueTransition(", "fun editorSizeSpec(", "fun editorRowEnter(", "fun editorRowExit(")
            .forEach { assertTrue("NavMotion.kt sin $it", motion.contains(it)) }
    }

    @Test
    fun editorMenusExponenSeleccion() {
        val pills = source("ui/tasks/TaskEditorPills.kt")
        val repeat = pills.substringAfter("private fun RepeatPill(").substringBefore("private fun PriorityPill(")
        val priority = pills.substringAfter("private fun PriorityPill(").substringBefore("private fun AssigneePill(")

        assertTrue(repeat.contains("semantics { selected = current }"))
        assertTrue(priority.contains("semantics { selected = current }"))
        assertTrue(pills.contains("import androidx.compose.ui.semantics.selected"))
    }

    @Test
    fun editorUsaUnSoloBackHandlerYFocoTrasComponerElFormulario() {
        val sheet = source("ui/tasks/TaskEditorSheet.kt")

        assertEquals(1, Regex("BackHandler\\(").findAll(sheet).count())
        assertTrue(sheet.contains("snapshotFlow { composedForms.intValue }"))
        assertTrue("el foco se pide tras esperar al formulario", sheet.indexOf("awaitForm()") in 0 until sheet.indexOf("pillFocus.forPanel(lastPanel)"))
    }

    @Test
    fun seccionRegion_sinCamposDeTextoLibreComoValor() {
        val sheet = source("ui/settings/RegionPickerSheet.kt")
        val section = source("ui/settings/RegionSettingsSection.kt")

        assertEquals("un solo campo de texto: el buscador", 1, Regex("OutlinedTextField\\(").findAll(sheet).count())
        assertTrue(!section.contains("TextField("))
        assertTrue("el buscador solo filtra", sheet.contains("onValueChange = onQueryChange"))
        assertTrue(!sheet.contains("onSelectCountry(query") && !sheet.contains("onSelectTimeZone(query"))
        assertEquals(1, Regex("ModalBottomSheet\\(").findAll(sheet).count())
        assertTrue(sheet.contains("TrackOpenSheet()"))
    }

    @Test
    fun seccionRegion_sinCadenasNiDpLiterales() {
        listOf("ui/settings/RegionPickerSheet.kt", "ui/settings/RegionSettingsSection.kt").forEach { path ->
            val text = source(path)
            assertTrue("$path no usa dp literales", !Regex("\\d\\.dp").containsMatchIn(text))
            assertTrue("$path no usa Text con cadena literal", !Regex("Text\\(\\s*\"[A-Za-zÁ-ú]").containsMatchIn(text))
            assertTrue("$path no usa rememberSaveable", !text.contains("rememberSaveable"))
        }
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()
        listOf(
            "settings_region_title", "settings_region_country", "settings_region_time_zone",
            "settings_region_no_country", "settings_region_device_zone", "settings_region_device_zone_value",
            "settings_region_search_hint", "settings_region_search_clear", "settings_region_empty",
            "settings_region_selected", "settings_region_picker_country", "settings_region_picker_time_zone",
        ).forEach { assertTrue("falta $it", strings.contains("name=\"$it\"")) }
    }

    @Test
    fun seccionRegion_vaEntreTemaYRecordatorios() {
        val screen = source("ui/settings/SettingsScreen.kt")

        val theme = screen.indexOf("ThemeSettingsSection(")
        val region = screen.indexOf("RegionSettingsSection(")
        val reminders = screen.indexOf("ReminderSettingsSection(")
        assertTrue(theme in 0 until region && region < reminders)
    }

    private fun source(path: String): String = File(sourcesRoot, path).readText()

    private fun quickAddFiles(): List<File> = File(sourcesRoot, "ui/quickadd").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun mainFiles(): List<File> =
        sourcesRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun uiFiles(): List<File> = File(sourcesRoot, "ui").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun resolveSourcesRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }

    private companion object {
        val GLYPHS = listOf("★", "☆", "✓")
        val EXPECTED_DEPENDENCIES = listOf(
            "implementation(platform(libs.androidx.compose.bom))",
            "implementation(libs.androidx.activity.compose)",
            "implementation(libs.androidx.compose.material.icons.extended)",
            "implementation(libs.androidx.compose.material3)",
            "implementation(libs.androidx.compose.ui)",
            "implementation(libs.androidx.compose.ui.graphics)",
            "implementation(libs.androidx.compose.ui.tooling.preview)",
            "implementation(libs.androidx.core.ktx)",
            "implementation(libs.androidx.credentials)",
            "implementation(libs.androidx.credentials.play.services.auth)",
            "implementation(libs.androidx.datastore.preferences)",
            "implementation(libs.androidx.lifecycle.runtime.compose)",
            "implementation(libs.androidx.lifecycle.runtime.ktx)",
            "implementation(libs.androidx.lifecycle.viewmodel.compose)",
            "implementation(libs.androidx.lifecycle.viewmodel.ktx)",
            "implementation(libs.androidx.navigation.compose)",
            "implementation(libs.androidx.room.runtime)",
            "implementation(libs.androidx.work.runtime)",
            "implementation(libs.googleid)",
            "implementation(libs.okhttp)",
            "ksp(libs.androidx.room.compiler)",
            "coreLibraryDesugaring(libs.desugar.jdk.libs)",
            "testImplementation(libs.androidx.datastore.preferences.core)",
            "testImplementation(libs.json)",
            "testImplementation(libs.junit)",
            "testImplementation(libs.kotlinx.coroutines.test)",
            "androidTestImplementation(platform(libs.androidx.compose.bom))",
            "androidTestImplementation(libs.androidx.compose.ui.test.junit4)",
            "androidTestImplementation(libs.androidx.espresso.core)",
            "androidTestImplementation(libs.androidx.junit)",
            "debugImplementation(libs.androidx.compose.ui.test.manifest)",
            "debugImplementation(libs.androidx.compose.ui.tooling)",
        )
        val LIST_SCREENS = listOf(
            "ui/myday/MyDayScreen.kt",
            "ui/lists/ListScreen.kt",
            "ui/upcoming/UpcomingScreen.kt",
            "ui/alltasks/AllTasksScreen.kt",
        )
        val EDITOR_HOST_SCREENS = LIST_SCREENS + listOf(
            "ui/board/BoardScreen.kt",
            "ui/columns/ColumnsScreen.kt",
            "ui/notifications/NotificationsScreen.kt",
        )
        val EDITOR_FILES = listOf(
            "ui/tasks/TaskEditorSheet.kt",
            "ui/tasks/TaskEditorPills.kt",
            "ui/tasks/TaskEditorPanels.kt",
            "ui/tasks/TaskEditorHost.kt",
            "ui/tasks/RepeatSection.kt",
            "ui/tasks/AnimatedSubtaskRow.kt",
            "ui/tasks/SubtaskEditFields.kt",
            "ui/tasks/SubtasksSection.kt",
            "ui/tasks/SubtaskDraftsSection.kt",
        )
        val EDITOR_NEW_FILES = listOf(
            "ui/tasks/TaskEditorSheet.kt",
            "ui/tasks/TaskEditorPills.kt",
            "ui/tasks/TaskEditorPanels.kt",
            "ui/tasks/AnimatedSubtaskRow.kt",
        )
        val ANIMATED_EDITOR_FILES = listOf(
            "ui/tasks/TaskEditorSheet.kt",
            "ui/tasks/TaskEditorPills.kt",
            "ui/tasks/TaskEditorPanels.kt",
            "ui/tasks/AnimatedSubtaskRow.kt",
            "ui/tasks/SubtasksSection.kt",
            "ui/tasks/SubtaskDraftsSection.kt",
        )
        val PILL_FILES = listOf(
            "ui/shell/FloatingChrome.kt",
            "ui/shell/QuickAddScrim.kt",
            "ui/shell/QuickDateShortcutRow.kt",
            "ui/shell/QuickAddListPanel.kt",
        )
        // QuickAddScrim.kt queda fuera a propósito: es un velo de pantalla completa, no un control de 48 dp.
        val CALENDAR_FILES = listOf(
            "ui/calendar/CalendarScreen.kt",
            "ui/calendar/CalendarHeader.kt",
            "ui/calendar/MonthGrid.kt",
            "ui/calendar/CalendarBody.kt",
            "ui/calendar/CalendarsSheet.kt",
            "ui/calendar/EventEditorSheet.kt",
            "ui/permission/CalendarPermissionNotice.kt",
        )
        val NEW_CONTROL_FILES = listOf(
            "ui/shell/QuickDateShortcutRow.kt",
            "ui/shell/QuickAddListPanel.kt",
            "ui/components/DateGroupHeader.kt",
            "ui/components/HomeButton.kt",
            "ui/home/HomeScreen.kt",
            "ui/shell/FloatingChrome.kt",
            "ui/shell/OverflowMenuButton.kt",
            "ui/shell/SectionTopBar.kt",
            "ui/shell/ClearCompletedDialog.kt",
            "ui/upcoming/UpcomingScreen.kt",
            "ui/alltasks/AllTasksScreen.kt",
            "ui/boards/BoardNameDialog.kt",
        ) + CALENDAR_FILES + EDITOR_NEW_FILES
        val NEW_UI_FILES = listOf(
            "ui/shell/QuickAddScrim.kt",
            "ui/navigation/NavMotion.kt",
            "ui/shell/QuickDateShortcutRow.kt",
            "ui/shell/QuickAddListPanel.kt",
            "ui/components/TaskRow.kt",
            "ui/components/EmptyState.kt",
            "ui/components/DateGroupHeader.kt",
            "ui/components/HomeButton.kt",
            "ui/components/RowActionStyle.kt",
            "ui/shell/FloatingChrome.kt",
            "ui/shell/OverflowMenuButton.kt",
            "ui/shell/SectionTopBar.kt",
            "ui/shell/ClearCompletedDialog.kt",
            "ui/home/HomeScreen.kt",
            "ui/boards/BoardNameDialog.kt",
            "ui/tasks/TaskActionsSheet.kt",
            "ui/tasks/AssignToBoardHost.kt",
            "ui/tasks/MoveToListHost.kt",
            "ui/lists/TaskListNameDialog.kt",
            "ui/lists/ConfirmDeleteListDialog.kt",
            "ui/myday/MyDaySortMenu.kt",
            "ui/myday/MyDayScreen.kt",
            "ui/lists/ListScreen.kt",
            "ui/upcoming/UpcomingScreen.kt",
            "ui/alltasks/AllTasksScreen.kt",
        ) + CALENDAR_FILES + EDITOR_NEW_FILES
    }
}
