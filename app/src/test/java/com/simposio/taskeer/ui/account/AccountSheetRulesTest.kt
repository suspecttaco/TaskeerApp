package com.simposio.taskeer.ui.account

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.avatarColorIndex
import com.simposio.taskeer.ui.auth.AccountAction
import com.simposio.taskeer.ui.auth.AccountSecurityAction
import com.simposio.taskeer.ui.auth.accountSectionFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSheetRulesTest {

    private val ana = AccountSummary("ana@example.com", "  Ana López ", emailVerified = false)

    @Test
    fun signedOut_noHayHoja() {
        assertNull(accountSheetFor(SessionState.SignedOut()))
        assertNull(accountSheetFor(SessionState.SignedOut(expired = true)))
    }

    @Test
    fun resuming_noHayHoja() {
        assertNull(accountSheetFor(SessionState.Resuming))
    }

    @Test
    fun signedIn_incluyeIdentidadYSeccion() {
        val state = SessionState.SignedIn(ana, offline = false)

        val model = accountSheetFor(state)!!

        assertEquals(AccountIdentity("Ana López", "ana@example.com", "AL", avatarColorIndex("  Ana López ", "ana@example.com")), model.identity)
        assertEquals(accountSectionFor(state), model.section)
    }

    @Test
    fun sinCuenta_sinIdentidad() {
        val model = accountSheetFor(SessionState.SignedIn(null, offline = false))!!

        assertNull(model.identity)
        assertEquals(listOf(AccountAction.SIGN_OUT), model.section.actions)
    }

    @Test
    fun nombreVacio_usaParteLocalDelCorreo() {
        val model = accountSheetFor(SessionState.SignedIn(AccountSummary("zoe@x.com", ""), offline = false))!!

        assertEquals("zoe", model.identity?.displayName)
        assertEquals("zoe@x.com", model.identity?.email)
    }

    @Test
    fun conPasswordFalse_sinFilasDeSeguridad() {
        val state = SessionState.SignedIn(AccountSummary("g@x.com", "G", hasPassword = false), offline = false)

        assertTrue(accountSheetFor(state)!!.section.security.isEmpty())
        assertEquals(
            listOf(AccountSecurityAction.CHANGE_PASSWORD, AccountSecurityAction.REGENERATE_CODES),
            accountSheetFor(SessionState.SignedIn(ana, offline = false))!!.section.security,
        )
    }

    @Test
    fun offline_ofreceReintentar() {
        val model = accountSheetFor(SessionState.SignedIn(ana, offline = true))!!

        assertTrue(AccountAction.RETRY in model.section.actions)
    }

    @Test
    fun visible_soloConSesion() {
        assertTrue(sheetVisible(true, SessionState.SignedIn(ana, offline = false)))
        assertFalse(sheetVisible(true, SessionState.SignedOut()))
        assertFalse(sheetVisible(true, SessionState.Resuming))
    }

    @Test
    fun visible_sinPeticion_esFalso() {
        assertFalse(sheetVisible(false, SessionState.SignedIn(ana, offline = false)))
    }
}
