package com.simposio.taskeer.ui

import com.simposio.taskeer.ui.board.Bounds
import com.simposio.taskeer.ui.board.CardBounds
import com.simposio.taskeer.ui.board.DropTarget
import com.simposio.taskeer.ui.board.DropTargetResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DropTargetResolverTest {

    private val columns = mapOf(
        "c1" to Bounds(0f, 0f, 100f, 500f),
        "c2" to Bounds(110f, 0f, 210f, 500f),
        "vacia" to Bounds(220f, 0f, 320f, 500f),
    )
    private val cards = mapOf(
        "c1" to listOf(
            CardBounds("a", Bounds(0f, 0f, 100f, 100f)),
            CardBounds("b", Bounds(0f, 100f, 100f, 200f)),
            CardBounds("c", Bounds(0f, 200f, 100f, 300f)),
        ),
        "c2" to listOf(CardBounds("x", Bounds(110f, 0f, 210f, 100f))),
    )

    @Test
    fun resolve_devuelveLaColumnaQueContieneElPunto() {
        val target = DropTargetResolver.resolve(150f, 10f, columns, cards, "a")

        assertEquals("c2", target?.columnId)
    }

    @Test
    fun resolve_indiceEsElNumeroDeTarjetasConCentroPorEncimaDelPunto() {
        assertEquals(DropTarget("c1", 1), DropTargetResolver.resolve(50f, 120f, columns, cards, "c"))
        assertEquals(DropTarget("c1", 0), DropTargetResolver.resolve(50f, 10f, columns, cards, "c"))
    }

    @Test
    fun resolve_columnaVacia_devuelveIndiceCero() {
        assertEquals(DropTarget("vacia", 0), DropTargetResolver.resolve(250f, 400f, columns, cards, "a"))
    }

    @Test
    fun resolve_porDebajoDeTodas_devuelveElFinalDeLaLista() {
        assertEquals(DropTarget("c2", 1), DropTargetResolver.resolve(150f, 400f, columns, cards, "a"))
    }

    @Test
    fun resolve_fueraDeTodaColumna_devuelveNull() {
        assertNull(DropTargetResolver.resolve(105f, 10f, columns, cards, "a"))
        assertNull(DropTargetResolver.resolve(50f, 600f, columns, cards, "a"))
    }

    @Test
    fun resolve_ignoraLaTarjetaArrastrada() {
        assertEquals(DropTarget("c1", 2), DropTargetResolver.resolve(50f, 450f, columns, cards, "a"))
        assertEquals(DropTarget("c1", 3), DropTargetResolver.resolve(50f, 450f, columns, cards, "zzz"))
    }
}
