package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.testing.uuid
import com.simposio.taskeer.testing.workspace
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceRulesTest {

    @Test
    fun validaNombre_conUnCaracter_esMuyCorto() {
        assertEquals(NameCheck.TooShort, WorkspaceRules.validateName("a"))
    }

    @Test
    fun validaNombre_conDosCaracteres_esValido() {
        assertEquals(NameCheck.Valid("ab"), WorkspaceRules.validateName("ab"))
    }

    @Test
    fun validaNombre_con150_esValido() {
        val name = "a".repeat(150)

        assertEquals(NameCheck.Valid(name), WorkspaceRules.validateName(name))
    }

    @Test
    fun validaNombre_con151_esMuyLargo() {
        assertEquals(NameCheck.TooLong, WorkspaceRules.validateName("a".repeat(151)))
    }

    @Test
    fun validaNombre_recortaEspacios() {
        assertEquals(NameCheck.Valid("Equipo"), WorkspaceRules.validateName("  Equipo \n"))
    }

    @Test
    fun validaNombre_soloEspacios_esMuyCorto() {
        assertEquals(NameCheck.TooShort, WorkspaceRules.validateName("     "))
    }

    @Test
    fun ordena_personalPrimeroLuegoNombreSinMayusculasYId() {
        val personal = workspace(id = uuid(9), name = "Zeta", isPersonal = true)
        val beta = workspace(id = uuid(2), name = "beta")
        val alfa = workspace(id = uuid(3), name = "Alfa")
        val betaMayus = workspace(id = uuid(1), name = "BETA")

        val result = WorkspaceRules.sorted(listOf(beta, alfa, personal, betaMayus)).map { it.id }

        assertEquals(listOf(uuid(9), uuid(3), uuid(1), uuid(2)), result)
    }

    @Test
    fun canRename_ownerYAdmin_si() {
        assertEquals(listOf(true, true), listOf(WorkspaceRole.OWNER, WorkspaceRole.ADMIN).map(WorkspaceRules::canRename))
    }

    @Test
    fun canRename_editorViewerYUnknown_no() {
        val roles = listOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER, WorkspaceRole.UNKNOWN)

        assertEquals(listOf(false, false, false), roles.map(WorkspaceRules::canRename))
    }

    @Test
    fun canDelete_ownerNoPersonal_si() {
        assertEquals(true, WorkspaceRules.canDelete(WorkspaceRole.OWNER, isPersonal = false))
    }

    @Test
    fun canDelete_ownerPersonal_no() {
        assertEquals(false, WorkspaceRules.canDelete(WorkspaceRole.OWNER, isPersonal = true))
    }

    @Test
    fun canDelete_adminEditorViewerUnknown_no() {
        val roles = listOf(WorkspaceRole.ADMIN, WorkspaceRole.EDITOR, WorkspaceRole.VIEWER, WorkspaceRole.UNKNOWN)

        assertEquals(listOf(false, false, false, false), roles.map { WorkspaceRules.canDelete(it, isPersonal = false) })
    }

    @Test
    fun reapply_quitaLosBorrados() {
        val fresh = listOf(workspace(id = uuid(1), name = "A1"), workspace(id = uuid(2), name = "B2"))

        val result = WorkspaceRules.reapply(fresh, ConfirmedOps(deletedIds = setOf(uuid(1))))

        assertEquals(listOf(uuid(2)), result.map { it.id })
    }

    @Test
    fun reapply_reaplicaLosNombres() {
        val fresh = listOf(workspace(id = uuid(1), name = "Viejo"), workspace(id = uuid(2), name = "Otro"))

        val result = WorkspaceRules.reapply(fresh, ConfirmedOps(renamed = mapOf(uuid(1) to "Nuevo")))

        assertEquals(listOf("Nuevo", "Otro"), result.map { it.name })
    }

    @Test
    fun roleOf_nombreDesconocidoONulo_esUnknown() {
        assertEquals(
            listOf(WorkspaceRole.ADMIN, WorkspaceRole.UNKNOWN, WorkspaceRole.UNKNOWN, WorkspaceRole.UNKNOWN),
            listOf("ADMIN", "SUPERUSER", null, "UNKNOWN").map(WorkspaceRules::roleOf),
        )
    }

    @Test
    fun reapply_anadeLosCreadosQueElRefrescoNoTrae() {
        val fresh = listOf(workspace(id = uuid(1), name = "Uno"))
        val created = workspace(id = uuid(2), name = "Creado")

        val result = WorkspaceRules.reapply(fresh, ConfirmedOps(created = listOf(created)))

        assertEquals(listOf("Creado", "Uno"), result.map { it.name })
    }
}
