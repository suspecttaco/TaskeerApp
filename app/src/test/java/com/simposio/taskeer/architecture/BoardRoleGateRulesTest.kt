package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRoleGateRulesTest {

    @Test
    fun losTresRepositoriosConsultanLaBarreraDeRol() {
        listOf("TaskRepositoryImpl", "ColumnRepositoryImpl", "BoardRepositoryImpl").forEach { name ->
            val source = MainSources.kotlin("data/repository/$name.kt")
            assertTrue("$name debe recibir BoardPermissionGate", source.contains("BoardPermissionGate"))
            assertTrue("$name debe consultar la barrera", source.contains("gate.deny"))
        }
    }

    @Test
    fun laBarreraNoCalculaRolesPorSuCuenta() {
        val source = MainSources.kotlin("data/repository/BoardPermissionGate.kt")

        assertTrue(source.contains("BoardRoleRules"))
        assertFalse("el rol efectivo lo resuelve el servidor", source.contains("WorkspaceRole.OWNER"))
    }

    @Test
    fun soloElPurgadorBorraFisicamenteUnTablero() {
        val users = MainSources.kotlinFiles("data").filter { it.readText().contains("purgeBoard(") }.map { it.name }.toSet()

        assertEquals(setOf("SyncDao.kt", "LocalBoardPurger.kt"), users)
    }

    @Test
    fun laUiNoConoceElAccesoLocal() {
        val offenders = MainSources.kotlinFiles("ui").filter {
            val text = it.readText()
            text.contains("BoardAccessDao") || text.contains("BoardAccessEntity")
        }.map { it.name }

        assertTrue("la UI no debe tocar el acceso local: $offenders", offenders.isEmpty())
    }
}
