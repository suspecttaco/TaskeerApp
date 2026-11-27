package com.simposio.taskeer.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeEmphasisTest {

    @Test
    fun emphasized_usaSemiBold() {
        assertEquals(FontWeight.SemiBold, TextStyle.Default.emphasized().fontWeight)
    }

    @Test
    fun unread_noLeidaEsBoldYLeidaEsNormal() {
        assertEquals(FontWeight.Bold, TextStyle.Default.unread(true).fontWeight)
        assertEquals(FontWeight.Normal, TextStyle.Default.unread(false).fontWeight)
    }

    @Test
    fun boldIf_respetaLaCondicion() {
        assertEquals(FontWeight.Bold, TextStyle.Default.boldIf(true).fontWeight)
        assertEquals(FontWeight.Normal, TextStyle.Default.boldIf(false).fontWeight)
    }
}
