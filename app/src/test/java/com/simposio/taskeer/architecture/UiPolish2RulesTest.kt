package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPolish2RulesTest {

    private val sourcesRoot: File = resolveSourcesRoot()

    @Test
    fun sinLiteralesDeDpAlfaNiTipografiaFueraDeTheme() {
        val dpOrSp = Regex("\\d\\.(dp|sp)\\b")
        val typography = Regex("fontWeight|fontSize|letterSpacing|FontWeight\\.")
        val violations = outsideTheme().flatMap { file ->
            val text = file.readText()
            listOfNotNull(
                "${file.rel()} usa dp/sp literal".takeIf { dpOrSp.containsMatchIn(text) },
                "${file.rel()} usa tipografía ad hoc".takeIf { typography.containsMatchIn(text) },
            )
        }

        assertTrue("literales fuera de ui/theme: $violations", violations.isEmpty())
    }

    @Test
    fun alfaDeshabilitadoSoloEnEmphasis() {
        val violations = outsideTheme()
            .filter { it.readText().contains("0.38f") }
            .map { it.rel() }

        assertTrue("0.38f solo en ui/theme/Emphasis.kt: $violations", violations.isEmpty())
        assertTrue(File(sourcesRoot, "ui/theme/Emphasis.kt").readText().contains("Disabled = 0.38f"))
    }

    @Test
    fun alfasYEscalasFraccionariasSoloEnTheme() {
        val fraction = Regex("\\d\\.\\d+f\\b")
        val violations = outsideTheme()
            .filter { fraction.containsMatchIn(it.readText()) }
            .map { it.rel() }

        assertTrue("alfas/escalas literales fuera de ui/theme: $violations", violations.isEmpty())
    }

    @Test
    fun sinTweenLiteralFueraDeTheme() {
        val literal = Regex("tween\\(\\s*\\d|delayMillis\\s*=\\s*\\d|durationMillis\\s*=\\s*\\d")
        val violations = outsideTheme()
            .filter { literal.containsMatchIn(it.readText()) }
            .map { it.rel() }

        assertTrue("duraciones literales fuera de ui/theme: $violations", violations.isEmpty())
    }

    @Test
    fun animacionesDependenDeAnimated() {
        val animating = Regex("(?<![A-Za-z])AnimatedVisibility\\(|(?<![A-Za-z])AnimatedContent\\(|(?<![A-Za-z])Crossfade\\(|animate[A-Za-z]*AsState\\(")
        val violations = uiFiles()
            .filter { animating.containsMatchIn(it.readText()) }
            .filterNot { file ->
                val text = file.readText()
                text.contains("animated") || text.contains("animationsEnabled") || text.contains("AnimationsEnabled")
            }
            .map { it.rel() }

        assertTrue("animan sin consultar la reducción de animaciones: $violations", violations.isEmpty())
    }

    @Test
    fun listasConClaveUsanRowItemModifier() {
        val violations = ANIMATED_LISTS.filterNot { path ->
            val text = File(sourcesRoot, path).readText()
            text.contains("key = ") && text.contains("rowItemModifier(")
        }

        assertTrue("listas con clave sin rowItemModifier: $violations", violations.isEmpty())
    }

    @Test
    fun buscadorYTableroKanbanQuedanSinMovimientoDeFilas() {
        assertTrue(!File(sourcesRoot, "ui/search/SearchScreen.kt").readText().contains("rowItemModifier("))
        assertTrue(!File(sourcesRoot, "ui/board/BoardColumnView.kt").readText().contains("rowItemModifier("))
    }

    @Test
    fun loadingBodyUnicoYConDescripcion() {
        val duplicates = uiFiles()
            .filterNot { it.rel().startsWith("ui/components") }
            .filter { Regex("fun LoadingBody\\(").containsMatchIn(it.readText()) }
            .map { it.rel() }
        val component = File(sourcesRoot, "ui/components/LoadingBody.kt").readText()

        assertTrue("LoadingBody privado fuera de ui/components: $duplicates", duplicates.isEmpty())
        assertTrue(component.contains("contentDescription = description"))
        assertTrue(component.contains("LiveRegionMode.Polite"))
    }

    @Test
    fun faseEnvueltaEnPhaseCrossfade() {
        val violations = PHASE_SCREENS.filterNot { path ->
            val text = File(sourcesRoot, path).readText()
            text.contains("PhaseCrossfade(") && !text.contains("when (state.phase)")
        }

        assertTrue("pantallas con fase sin PhaseCrossfade: $violations", violations.isEmpty())
    }

    @Test
    fun vaciosUsanEmptyStateOEmptyHint() {
        val emptyString = Regex("R\\.string\\.(\\w*_empty\\w*)")
        val violations = uiFiles()
            .filterNot { it.rel() == "ui/tasks/EmptyStateRules.kt" }
            .flatMap { file ->
                val text = file.readText()
                emptyString.findAll(text)
                    .filterNot { it.groupValues[1] in EMPTY_STRING_EXCEPTIONS }
                    .filterNot { match ->
                        val before = text.substring(maxOf(0, match.range.first - 300), match.range.first)
                        before.contains("EmptyState(") || before.contains("EmptyHint(")
                    }
                    .map { "${file.rel()}: ${it.groupValues[1]}" }
            }
            .toList()

        assertTrue("vacíos fuera de EmptyState/EmptyHint: $violations", violations.isEmpty())
    }

    @Test
    fun emptyStateAdmiteVaciosSinAccion() {
        val text = File(sourcesRoot, "ui/components/EmptyState.kt").readText()

        assertTrue(text.contains("actionLabel: String? = null"))
        assertTrue(text.contains("onAction: (() -> Unit)? = null"))
    }

    @Test
    fun spinnerSoloEnComponents() {
        val spinner = Regex("CircularProgressIndicator\\(|strokeWidth")
        val violations = uiFiles()
            .filterNot { it.rel().startsWith("ui/components") }
            .filter { spinner.containsMatchIn(it.readText()) }
            .map { it.rel() }

        assertTrue("spinner o strokeWidth fuera de ui/components: $violations", violations.isEmpty())
    }

    @Test
    fun buttonProgressExponeEstadoAccesible() {
        val text = File(sourcesRoot, "ui/components/ButtonProgress.kt").readText()

        assertTrue(text.contains("stateDescription = loadingDescription"))
        assertTrue(text.contains("Spacing.SpinnerStroke"))
        assertTrue(text.contains("Spacing.IconSize"))
        assertTrue(text.contains("R.string.common_in_progress"))
    }

    @Test
    fun erroresEnLineaUsanInlineError() {
        val errorColor = "color = MaterialTheme.colorScheme.error"
        val messageInError = Regex("Text\\(text = [^\\n]*message[^\\n]*$errorColor")
        val violations = uiFiles()
            .filterNot { it.rel().startsWith("ui/components") }
            .filter { it.readText().contains(errorColor) }
            .map { it.rel() }
            .filterNot { it in ERROR_COLOR_EXCEPTIONS }
        val loose = uiFiles().filter { messageInError.containsMatchIn(it.readText()) }.map { it.rel() }

        assertTrue("Text con color de error fuera de la lista cerrada: $violations", violations.isEmpty())
        assertTrue("mensajes de error sin InlineError: $loose", loose.isEmpty())
    }

    @Test
    fun inlineErrorEsRegionViva() {
        val text = File(sourcesRoot, "ui/components/InlineError.kt").readText()

        assertTrue(text.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(text.contains("MaterialTheme.typography.bodyMedium"))
        assertTrue(text.contains("MaterialTheme.colorScheme.error"))
    }

    @Test
    fun showSnackbarSoloEnNotice() {
        val violations = uiFiles()
            .filterNot { it.rel() == "ui/components/Notice.kt" }
            .filter { it.readText().contains("showSnackbar(") }
            .map { it.rel() }

        assertTrue("showSnackbar fuera de Notice.kt: $violations", violations.isEmpty())
    }

    @Test
    fun toastSoloEnAnnounceDeMiembros() {
        val violations = uiFiles()
            .filter { it.readText().contains("Toast.makeText(") }
            .map { it.rel() }

        assertTrue("Toast solo cuando la pantalla se cierra: $violations", violations == listOf("ui/workspaces/WorkspaceMembersScreen.kt"))
    }

    @Test
    fun estadosPuntualesSeAnimanConMotion() {
        val card = File(sourcesRoot, "ui/board/TaskCard.kt").readText()
        val column = File(sourcesRoot, "ui/board/BoardColumnView.kt").readText()
        val notification = File(sourcesRoot, "ui/notifications/NotificationRow.kt").readText()
        val calendar = File(sourcesRoot, "ui/calendar/CalendarScreen.kt").readText()

        assertTrue(card.contains("animateDpAsState(") && card.contains("stateDpSpec(rememberAnimationsEnabled())"))
        assertTrue(column.contains("animateFloatAsState(") && column.contains("stateFloatSpec(animated)"))
        assertTrue(column.contains("Motion.DraggedSourceAlpha"))
        assertTrue(notification.contains("AnimatedVisibility(visible = row.unread"))
        assertTrue(notification.contains("stateFadeEnter(animated)") && notification.contains("stateFadeExit(animated)"))
        assertTrue(calendar.contains("visible = state.load == CalendarLoad.Loading"))
        assertTrue(calendar.contains("stateFadeEnter(animated)") && calendar.contains("stateFadeExit(animated)"))
    }

    @Test
    fun sinIndicationNullSalvoScrim() {
        val violations = uiFiles()
            .filter { it.readText().contains("indication = null") }
            .map { it.rel() }

        assertTrue("indication = null solo en QuickAddScrim: $violations", violations == listOf("ui/shell/QuickAddScrim.kt"))
    }

    @Test
    fun sinIconosNoRellenos() {
        val outlined = Regex("material\\.icons\\.(outlined|rounded|sharp|twotone)|Icons\\.(Outlined|Rounded|Sharp|TwoTone)")
        val violations = uiFiles()
            .filter { outlined.containsMatchIn(it.readText()) }
            .map { it.rel() }

        assertTrue("iconos no rellenos: $violations", violations.isEmpty())
    }

    @Test
    fun elevacionesDesdeTokens() {
        val literal = Regex("(?i)elevation\\s*=\\s*\\d")
        val violations = outsideTheme()
            .filter { literal.containsMatchIn(it.readText()) }
            .map { it.rel() }

        assertTrue("elevaciones literales fuera de ui/theme: $violations", violations.isEmpty())
    }

    @Test
    fun sinCambiosFueraDeUi() {
        val motion = Regex("ui\\.theme\\.(Motion|phaseFadeSpec|state(Float|Dp|Fade))")
        val violations = listOf("domain", "data", "di").flatMap { dir ->
            File(sourcesRoot, dir).walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { motion.containsMatchIn(it.readText()) }
                .map { it.rel() }
                .toList()
        }

        assertTrue("domain/data/di no dependen del movimiento de UI: $violations", violations.isEmpty())
    }

    @Test
    fun componentesNuevosExponenAccesibilidad() {
        listOf("ButtonProgress", "LoadingBody", "InlineError").forEach { name ->
            val text = File(sourcesRoot, "ui/components/$name.kt").readText()
            assertTrue("$name sin semántica accesible", text.contains("semantics") && Regex("stateDescription|liveRegion|contentDescription").containsMatchIn(text))
        }
    }

    private fun File.rel(): String = relativeTo(sourcesRoot).path

    private fun uiFiles(): List<File> = File(sourcesRoot, "ui").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun outsideTheme(): List<File> = uiFiles().filterNot { it.rel().startsWith("ui/theme") }

    private fun resolveSourcesRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }

    private companion object {
        // Estado (vencido, seguridad, destructivo), no error en línea de una operación.
        val ERROR_COLOR_EXCEPTIONS = setOf(
            "ui/myday/SuggestionsSheet.kt",
            "ui/myday/MyDayScreen.kt",
            "ui/tasks/TaskEditorSheet.kt",
            "ui/notifications/NotificationRow.kt",
            "ui/notifications/AccountNotificationsContent.kt",
            "ui/board/TaskCard.kt",
            "ui/calendar/EventEditorSheet.kt",
            "ui/workspaces/WorkspaceInvitationDialogs.kt",
            "ui/workspaces/WorkspaceDialogs.kt",
            "ui/workspaces/WorkspaceMemberDialogs.kt",
            "ui/workspaces/BoardAccessDialogs.kt",
        )
        val EMPTY_STRING_EXCEPTIONS = setOf("list_delete_message_empty", "search_empty_title", "search_empty_message")
        val PHASE_SCREENS = listOf(
            "ui/workspaces/WorkspacesScreen.kt",
            "ui/workspaces/WorkspaceMembersScreen.kt",
            "ui/workspaces/MyInvitationsScreen.kt",
            "ui/workspaces/ServerBoardListScreen.kt",
            "ui/workspaces/BoardAccessScreen.kt",
            "ui/notifications/AccountNotificationsContent.kt",
        )
        val ANIMATED_LISTS = listOf(
            "ui/notifications/AccountNotificationsContent.kt",
            "ui/notifications/DeviceNotificationsContent.kt",
            "ui/workspaces/WorkspacesScreen.kt",
            "ui/workspaces/WorkspaceMembersScreen.kt",
            "ui/workspaces/MyInvitationsScreen.kt",
            "ui/workspaces/WorkspaceInvitationsSection.kt",
            "ui/workspaces/ServerBoardListScreen.kt",
            "ui/workspaces/BoardAccessScreen.kt",
            "ui/columns/ColumnsScreen.kt",
            "ui/home/HomeScreen.kt",
        )
    }
}
