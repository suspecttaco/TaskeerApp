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
    }

    @Test
    fun filasDeListaNoUsanMyDayToggle() {
        LIST_SCREENS.plus("ui/components/TaskRow.kt").forEach { path ->
            assertTrue("$path no debe usar MyDayToggle", !source(path).contains("MyDayToggle("))
        }
    }

    @Test
    fun pantallasDeListaTienenBotonFlotante() {
        listOf("ui/myday/MyDayScreen.kt", "ui/inbox/InboxScreen.kt", "ui/board/BoardScreen.kt").forEach { path ->
            assertTrue("$path debe tener QuickAddFab", source(path).contains("QuickAddFab("))
        }
        assertTrue(source("ui/components/QuickAddFab.kt").contains("R.string.fab_new_task"))
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
        assertTrue(!screen.contains("MyDaySort"))
        assertTrue(!screen.contains("my_day_sort"))
    }

    @Test
    fun appShellNoTieneAccesoACalendarioEnLaBarraSuperior() {
        val shell = source("ui/shell/AppShell.kt")
        val actions = shell.substringAfter("actions = {").substringBefore("bottomBar")

        assertTrue(!actions.contains("CalendarMonth"))
        assertTrue(actions.contains("MyDaySortMenu("))
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
    fun altaRapidaNoApilaSelectores() {
        val sheets = quickAddFiles().sumOf { Regex("ModalBottomSheet\\(").findAll(it.readText()).count() }

        assertEquals("un único bottom sheet en la alta rápida", 1, sheets)
        assertTrue(source("ui/quickadd/QuickAddSheet.kt").contains("QuickAddPanel.DATE"))
    }

    @Test
    fun bottomSheetsSonModalesConAsaYAccionDeCierre() {
        val sheetFiles = uiFiles().filter { it.readText().contains("ModalBottomSheet(") }
        val names = sheetFiles.map { it.name }.toSet()

        assertEquals(setOf("QuickAddSheet.kt", "TaskActionsSheet.kt", "AssignToBoardHost.kt", "MoveToListHost.kt"), names)
        sheetFiles.forEach { file ->
            val text = file.readText()
            assertTrue("${file.name}: asa", text.contains("dragHandle ="))
            assertTrue("${file.name}: asa accesible", text.contains("R.string.quick_add_drag_handle"))
            assertTrue("${file.name}: sheetState", text.contains("sheetState ="))
            assertTrue("${file.name}: cerrar", text.contains("R.string.quick_add_close"))
            assertTrue("${file.name}: título anunciado", text.contains("paneTitle"))
        }
        val quickAdd = source("ui/quickadd/QuickAddSheet.kt")
        assertTrue(quickAdd.contains("imePadding()"))
        val sheetCall = quickAdd.indexOf("ModalBottomSheet(")
        val backHandler = quickAdd.indexOf("BackHandler(enabled = state.panel != null)")
        assertTrue("BackHandler dentro del contenido del sheet (diálogo con su propio despachador)", sheetCall in 0 until backHandler)
        assertEquals("un único BackHandler", 1, Regex("BackHandler\\(").findAll(quickAdd).count())
        assertTrue(quickAdd.contains("ImeAction.Done"))
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

    private fun source(path: String): String = File(sourcesRoot, path).readText()

    private fun quickAddFiles(): List<File> = File(sourcesRoot, "ui/quickadd").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

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
        val LIST_SCREENS = listOf("ui/myday/MyDayScreen.kt", "ui/inbox/InboxScreen.kt", "ui/planned/PlannedScreen.kt")
        val NEW_UI_FILES = listOf(
            "ui/components/TaskRow.kt",
            "ui/components/EmptyState.kt",
            "ui/components/QuickAddFab.kt",
            "ui/components/RowActionStyle.kt",
            "ui/quickadd/QuickAddSheet.kt",
            "ui/tasks/TaskActionsSheet.kt",
            "ui/tasks/AssignToBoardHost.kt",
            "ui/tasks/MoveToListHost.kt",
            "ui/lists/TaskListSelector.kt",
            "ui/lists/TaskListNameDialog.kt",
            "ui/lists/ConfirmDeleteListDialog.kt",
            "ui/myday/MyDaySortMenu.kt",
            "ui/myday/MyDayScreen.kt",
            "ui/inbox/InboxScreen.kt",
            "ui/planned/PlannedScreen.kt",
        )
    }
}
