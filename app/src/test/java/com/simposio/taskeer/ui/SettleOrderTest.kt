package com.simposio.taskeer.ui

import com.simposio.taskeer.ui.tasks.CompletionSettler
import com.simposio.taskeer.ui.tasks.settleOrder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettleOrderTest {

    private data class Row(val id: String, val done: Boolean = false)

    private fun order(previous: List<Row>, next: List<Row>, settling: Set<String>, retain: Boolean = false) =
        settleOrder(previous, next, settling, { it.id }, if (retain) { row -> row.copy(done = true) } else null)
            .map { it.id }

    @Test
    fun filaEnAsentamiento_conservaSuPosicion() {
        val previous = listOf(Row("a"), Row("b"), Row("c"))
        val next = listOf(Row("b"), Row("c"), Row("a", done = true))

        assertEquals(listOf("a", "b", "c"), order(previous, next, setOf("a")))
    }

    @Test
    fun alSoltar_seMueveAlFinal() {
        val previous = listOf(Row("a"), Row("b"), Row("c"))
        val next = listOf(Row("b"), Row("c"), Row("a", done = true))

        assertEquals(listOf("b", "c", "a"), order(previous, next, emptySet()))
    }

    @Test
    fun filaEnAsentamiento_usaLaVersionNuevaDelElemento() {
        val previous = listOf(Row("a"), Row("b"))
        val next = listOf(Row("b"), Row("a", done = true))

        val result = settleOrder(previous, next, setOf("a"), { it.id })

        assertEquals(Row("a", done = true), result.first())
    }

    @Test
    fun filaQueSaleDeLaVista_seRetieneCompletadaDuranteLaPausa() {
        val previous = listOf(Row("a"), Row("b"))
        val next = listOf(Row("b"))

        val result = settleOrder(previous, next, setOf("a"), { it.id }) { it.copy(done = true) }

        assertEquals(listOf(Row("a", done = true), Row("b")), result)
    }

    @Test
    fun filaQueSaleDeLaVista_sinRetencion_seDescarta() {
        val previous = listOf(Row("a"), Row("b"))

        assertEquals(listOf("b"), order(previous, listOf(Row("b")), setOf("a")))
    }

    @Test
    fun sinAsentamiento_devuelveLaListaNueva() {
        val next = listOf(Row("b"), Row("a"))

        assertEquals(next, settleOrder(listOf(Row("a"), Row("b")), next, emptySet(), { it.id }))
    }

    @Test
    fun asentamientoDeFilaDesconocida_seIgnora() {
        assertEquals(listOf("b", "c"), order(listOf(Row("b")), listOf(Row("b"), Row("c")), setOf("x"), retain = true))
    }

    @Test
    fun settler_retiraLaFilaAlTerminarLaPausa() = runTest {
        val settler = CompletionSettler(TestScope(StandardTestDispatcher(testScheduler)), { 500L })

        settler.begin("a")

        assertEquals(setOf("a"), settler.settling.value)
        advanceTimeBy(499)
        assertEquals(setOf("a"), settler.settling.value)
        advanceTimeBy(1)
        testScheduler.runCurrent()
        assertTrue(settler.settling.value.isEmpty())
    }

    @Test
    fun settler_conPausaCero_noAnadeNada() = runTest {
        val settler = CompletionSettler(TestScope(StandardTestDispatcher(testScheduler)), { 0L })

        settler.begin("a")

        assertTrue(settler.settling.value.isEmpty())
    }
}
