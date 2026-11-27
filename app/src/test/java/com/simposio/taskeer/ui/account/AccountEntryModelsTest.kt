package com.simposio.taskeer.ui.account

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccountEntryModelsTest {

    private val state = SessionState.SignedIn(AccountSummary("ana@example.com", "Ana López"), offline = true)

    private fun assertRedacted(text: String) {
        listOf("Ana", "López", "example", "ana@", "AL").forEach { assertFalse("filtra $it en $text", text.contains(it)) }
    }

    @Test
    fun toString_noIncluyeNombreNiCorreo() {
        val avatar = accountEntryFor(state)
        val sheet = accountSheetFor(state)!!

        assertEquals("Avatar(offline=true)", avatar.toString())
        assertRedacted(avatar.toString())
        assertEquals("AccountIdentity(redacted)", sheet.identity.toString())
        assertRedacted(sheet.toString())
    }
}
