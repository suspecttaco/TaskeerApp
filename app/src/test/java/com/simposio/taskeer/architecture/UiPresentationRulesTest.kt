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
        val files = File(sourcesRoot, "ui/components").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

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
    fun homeBuscador_esDeshabilitado() {
        val home = source("ui/home/HomeScreen.kt")

        assertTrue(home.contains("enabled = HomeLayout.SEARCH_ENABLED"))
        assertTrue(home.contains("readOnly = true"))
        assertTrue(source("ui/home/HomeSection.kt").contains("const val SEARCH_ENABLED: Boolean = false"))
        assertTrue(source("ui/shell/SectionTopBar.kt").contains("HomeSearchField()"))
    }

    @Test
    fun calendario_noPideNingunPermiso() {
        val manifest = File(sourcesRoot, "../../../../AndroidManifest.xml").readText()

        assertTrue(!manifest.contains("READ_CALENDAR"))
        assertTrue(!manifest.contains("WRITE_CALENDAR"))
        assertTrue(!source("ui/calendar/CalendarPlaceholderScreen.kt").contains("viewModel("))
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
    fun miDiaTieneUnaSolaFilaDeChips() {
        val screen = source("ui/myday/MyDayScreen.kt")

        assertEquals(1, Regex("FilterChip\\(").findAll(screen).count())
        assertTrue(!screen.contains("my_day_sort"))
    }

    @Test
    fun appShellNoTieneAccesoACalendarioEnLaBarraSuperior() {
        val shell = source("ui/shell/AppShell.kt") + source("ui/shell/SectionTopBar.kt")

        assertTrue(!shell.contains("CalendarMonth"))
    }

    @Test
    fun emptyStateExigeAccion() {
        val empty = source("ui/components/EmptyState.kt")

        assertTrue(empty.contains("actionLabel: String,"))
        assertTrue(empty.contains("onAction: () -> Unit,"))
        assertTrue(empty.contains("FilledTonalButton("))
        LIST_SCREENS.forEach { assertTrue("$it debe usar EmptyState", source(it).contains("EmptyState(")) }
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

        assertEquals(setOf("TaskActionsSheet.kt", "AssignToBoardHost.kt", "MoveToListHost.kt"), names)
        sheetFiles.forEach { file ->
            val text = file.readText()
            assertTrue("${file.name}: asa", text.contains("dragHandle ="))
            assertTrue("${file.name}: asa accesible", text.contains("R.string.quick_add_drag_handle"))
            assertTrue("${file.name}: sheetState", text.contains("sheetState ="))
            assertTrue("${file.name}: cerrar", text.contains("R.string.quick_add_close"))
            assertTrue("${file.name}: título anunciado", text.contains("paneTitle"))
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
    fun sinDpLiteralesEnPantallas() {
        val literal = Regex("(padding|spacedBy|size)\\([^)]*\\d\\.dp")
        val violations = uiFiles()
            .filter { file ->
                val relative = file.relativeTo(sourcesRoot).path
                !relative.startsWith("ui/components") && !relative.startsWith("ui/theme")
            }
            .filter { literal.containsMatchIn(it.readText()) }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("dp literales de espaciado en: $violations", violations.isEmpty())
    }

    @Test
    fun editorUsaDesplegableParaPrioridad() {
        val source = File(sourcesRoot, "ui/tasks/TaskEditorDialog.kt").readText()

        assertTrue(source.contains("options = TaskPriority.entries"))
        assertTrue(!Regex("RadioOption\\([^)]*priority", RegexOption.DOT_MATCHES_ALL).containsMatchIn(source))
    }

    @Test
    fun selectorDeRepeticion_tomaLosTextosDeRecursosYNoLlevaLaRegla() {
        val section = File(sourcesRoot, "ui/tasks/RepeatSection.kt").readText()
        val strings = File(sourcesRoot, "../../../../res/values/strings.xml").readText()

        listOf("Repetir", "No se repite", "Cada día", "Entre semana", "Cada semana", "Cada mes", "Cada año", "Personalizada…")
            .forEach { label ->
                assertTrue("$label debe vivir en strings.xml", strings.contains(">$label<"))
                assertTrue("$label no debe estar escrito en el composable", !section.contains("\"$label\""))
            }
        assertTrue(section.contains("R.string.task_repeat_hint_needs_due"))
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
        assertTrue(shell.contains("SnackbarDuration.Indefinite"))
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
            assertTrue("$path debe usar rowItemModifier()", source(path).contains("modifier = rowItemModifier()"))
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
        LIST_SCREENS.forEach { path ->
            assertTrue("$path debe recibir onDelete", source(path).contains("onDelete: (String) -> Unit"))
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
            "implementation(libs.androidx.datastore.preferences)",
            "implementation(libs.androidx.lifecycle.runtime.compose)",
            "implementation(libs.androidx.lifecycle.runtime.ktx)",
            "implementation(libs.androidx.lifecycle.viewmodel.compose)",
            "implementation(libs.androidx.lifecycle.viewmodel.ktx)",
            "implementation(libs.androidx.navigation.compose)",
            "implementation(libs.androidx.room.runtime)",
            "ksp(libs.androidx.room.compiler)",
            "coreLibraryDesugaring(libs.desugar.jdk.libs)",
            "testImplementation(libs.androidx.datastore.preferences.core)",
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
        val PILL_FILES = listOf(
            "ui/shell/FloatingChrome.kt",
            "ui/shell/QuickAddScrim.kt",
            "ui/shell/QuickDateShortcutRow.kt",
            "ui/shell/QuickAddListPanel.kt",
        )
        // QuickAddScrim.kt queda fuera a propósito: es un velo de pantalla completa, no un control de 48 dp.
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
            "ui/calendar/CalendarPlaceholderScreen.kt",
            "ui/boards/BoardNameDialog.kt",
        )
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
            "ui/calendar/CalendarPlaceholderScreen.kt",
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
        )
    }
}
