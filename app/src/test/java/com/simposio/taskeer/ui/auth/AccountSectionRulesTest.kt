package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSectionRulesTest {

    @Test
    fun signedOut_ofreceIniciarSesion() {
        val model = accountSectionFor(SessionState.SignedOut())

        assertEquals(AccountStatus.SIGNED_OUT, model.status)
        assertEquals(listOf(AccountAction.SIGN_IN), model.actions)
    }

    @Test
    fun resuming_muestraComprobando() {
        val model = accountSectionFor(SessionState.Resuming)

        assertEquals(AccountStatus.RESUMING, model.status)
        assertEquals(emptyList<AccountAction>(), model.actions)
    }

    @Test
    fun signedInConectado_ofreceCerrarSesion() {
        val model = accountSectionFor(SessionState.SignedIn(null, offline = false))

        assertEquals(AccountStatus.SIGNED_IN, model.status)
        assertEquals(listOf(AccountAction.SIGN_OUT), model.actions)
    }

    @Test
    fun signedInOffline_ofreceReintentarYCerrarSesion() {
        val model = accountSectionFor(SessionState.SignedIn(null, offline = true))

        assertEquals(AccountStatus.SIGNED_IN_OFFLINE, model.status)
        assertEquals(listOf(AccountAction.RETRY, AccountAction.SIGN_OUT), model.actions)
    }

    @Test
    fun signedInConCorreo_loMuestra() {
        val model = accountSectionFor(SessionState.SignedIn(AccountSummary("ana@example.com", "Ana"), offline = false))

        assertEquals("ana@example.com", model.email)
    }

    @Test
    fun signedInSinCorreo_muestraTextoGenerico() {
        assertNull(accountSectionFor(SessionState.SignedIn(null, offline = false)).email)
        assertNull(accountSectionFor(SessionState.SignedIn(AccountSummary("", ""), offline = false)).email)
    }
}
