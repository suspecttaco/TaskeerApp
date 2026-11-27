package com.simposio.taskeer.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SeededListIdTest {

    private val userA = "11111111-1111-4111-8111-111111111111"
    private val userB = "22222222-2222-4222-8222-222222222222"

    @Test
    fun vectorV15_usuarioA() {
        assertEquals("25ad9060-2206-569c-beab-fc24c3b923e5", SeededListId.forUser(userA))
    }

    @Test
    fun vectorV15_usuarioB() {
        assertEquals("2d409db8-03d9-5d09-a7c1-cc8cef5142eb", SeededListId.forUser(userB))
    }

    @Test
    fun distintoPorUsuario() {
        assertNotEquals(SeededListId.forUser(userA), SeededListId.forUser(userB))
    }

    @Test
    fun mismoUsuarioMismoId() {
        assertEquals(SeededListId.forUser(userA), SeededListId.forUser(userA))
    }
}
