package com.simposio.taskeer.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarRulesTest {

    private fun initials(name: String, email: String = "") = avatarInitials(name, email)

    @Test
    fun unaPalabra_unaInicial() {
        assertEquals("A", initials("Ana"))
    }

    @Test
    fun dosPalabras_primeraYUltima() {
        assertEquals("AL", initials("Ana López"))
    }

    @Test
    fun tresPalabras_primeraYUltima() {
        assertEquals("AL", initials("Ana María López"))
    }

    @Test
    fun espaciosMultiplesYDeBorde() {
        assertEquals("AL", initials("  Ana \t\n  López  "))
    }

    @Test
    fun minusculasPasanAMayusculas() {
        assertEquals("AL", initials("ana lópez"))
    }

    @Test
    fun acentosYEnie() {
        assertEquals("Ñ", initials("ñandú"))
        assertEquals("Á", initials("Ángel"))
    }

    @Test
    fun emojiAlInicio_seOmite() {
        assertEquals("A", initials("😀 Ana"))
        assertEquals("A", initials("Ana 😀"))
        assertEquals("A", initials("-Ana"))
    }

    @Test
    fun soloEmoji_caeAlCorreo() {
        assertEquals("Z", initials("😀", "zoe@x.com"))
    }

    @Test
    fun soloEspacios_caeAlCorreo() {
        assertEquals("Z", initials("  ", "zoe@x.com"))
        assertEquals("Z", initials("", "zoe@x.com"))
    }

    @Test
    fun todoVacio_esNull() {
        assertNull(initials("", ""))
        assertNull(initials("😀", "@x.com"))
    }

    @Test
    fun nombreCJK_conservaElCaracter() {
        assertEquals("李雷", initials("李 雷"))
    }

    @Test
    fun mayusculaMultiple_unaUnidad() {
        assertEquals("S", initials("ß"))
    }

    @Test
    fun parSustituto_noSeParte() {
        assertEquals("𝐀", initials("𝐀bc"))
    }

    @Test
    fun mismoNombre_mismoIndice() {
        assertEquals(avatarColorIndex("Ana López", "a@x.com"), avatarColorIndex("Ana López", "a@x.com"))
    }

    @Test
    fun indiceDentroDeRango() {
        listOf("Ana", "李 雷", "😀", "zoe", "x".repeat(500)).forEach {
            assertTrue(avatarColorIndex(it, "") in 0 until AVATAR_PALETTE_SIZE)
        }
        assertEquals(0, avatarColorIndex("cualquiera", "", paletteSize = 1))
    }

    @Test
    fun normalizaMayusculasYEspacios() {
        assertEquals(avatarColorIndex("ana lópez", ""), avatarColorIndex("  ANA   López ", ""))
    }

    @Test
    fun sinNombre_usaCorreo() {
        assertEquals(avatarColorIndex("zoe@x.com", ""), avatarColorIndex("  ", " Zoe@X.com "))
    }

    @Test
    fun sinNadaIndice0() {
        assertEquals(0, avatarColorIndex("", ""))
        assertEquals(0, avatarColorIndex("   ", "  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun paletteSizeNoPositivo_lanza() {
        avatarColorIndex("Ana", "", paletteSize = 0)
    }

    // Valores calculados una vez con FNV-1a de 32 bits (módulo 8): cambiarlos cambia el color de identidad de todos los usuarios.
    @Test
    fun valoresFijos_noCambian() {
        assertEquals(5, avatarColorIndex("Ana", ""))
        assertEquals(6, avatarColorIndex("Ana López", ""))
        assertEquals(6, avatarColorIndex("", "zoe@x.com"))
        assertEquals(3, avatarColorIndex("李 雷", ""))
    }

    @Test
    fun rangoCubreVariosIndices() {
        val names = (1..40).map { "Usuario $it" }

        assertTrue(names.map { avatarColorIndex(it, "") }.toSet().size >= 5)
    }
}
