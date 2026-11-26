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

    private fun signedIn(verified: Boolean?, offline: Boolean = false) =
        accountSectionFor(SessionState.SignedIn(AccountSummary("ana@example.com", "Ana", verified), offline))

    @Test
    fun verificacion_sinVerificarOfreceReenviarYEnlace() {
        assertEquals(EmailVerification.UNVERIFIED, signedIn(false).verification)
    }

    @Test
    fun verificacion_verificadoNoOfreceReenvio() {
        assertEquals(EmailVerification.VERIFIED, signedIn(true).verification)
    }

    @Test
    fun verificacion_desconocidoSinFila() {
        assertNull(signedIn(null).verification)
        assertNull(accountSectionFor(SessionState.SignedIn(null, offline = false)).verification)
    }

    @Test
    fun verificacion_sinSesionSinFila() {
        assertNull(accountSectionFor(SessionState.SignedOut()).verification)
        assertNull(accountSectionFor(SessionState.Resuming).verification)
    }
}
