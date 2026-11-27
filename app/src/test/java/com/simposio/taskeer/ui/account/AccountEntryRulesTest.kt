package com.simposio.taskeer.ui.account

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.avatarColorIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountEntryRulesTest {

    private val ana = AccountSummary("ana@example.com", "Ana López")

    @Test
    fun signedOut_esSignIn() {
        assertEquals(AccountEntry.SignIn, accountEntryFor(SessionState.SignedOut()))
    }

    @Test
    fun signedOutExpired_esSignIn() {
        assertEquals(AccountEntry.SignIn, accountEntryFor(SessionState.SignedOut(expired = true)))
    }

    @Test
    fun resuming_esIndicador_yNoOfreceIniciarSesion() {
        val entry = accountEntryFor(SessionState.Resuming)

        assertEquals(AccountEntry.Resuming, entry)
        assertFalse(entry == AccountEntry.SignIn)
    }

    @Test
    fun signedIn_esAvatarConInicialesYColor() {
        val entry = accountEntryFor(SessionState.SignedIn(ana, offline = false)) as AccountEntry.Avatar

        assertEquals("AL", entry.initials)
        assertEquals(avatarColorIndex("Ana López", "ana@example.com"), entry.colorIndex)
        assertEquals("Ana López", entry.displayName)
    }

    @Test
    fun signedInSinCuenta_esAvatarGenerico() {
        val entry = accountEntryFor(SessionState.SignedIn(null, offline = false)) as AccountEntry.Avatar

        assertNull(entry.initials)
        assertNull(entry.displayName)
        assertEquals(0, entry.colorIndex)
    }

    @Test
    fun signedInOffline_marcaOffline() {
        assertTrue((accountEntryFor(SessionState.SignedIn(ana, offline = true)) as AccountEntry.Avatar).offline)
        assertFalse((accountEntryFor(SessionState.SignedIn(ana, offline = false)) as AccountEntry.Avatar).offline)
    }

    @Test
    fun nombreVacio_usaLaParteLocalDelCorreo() {
        val entry = accountEntryFor(SessionState.SignedIn(AccountSummary("zoe@x.com", "  "), offline = false)) as AccountEntry.Avatar

        assertEquals("Z", entry.initials)
        assertEquals("zoe", entry.displayName)
    }
}
