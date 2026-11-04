package com.simposio.taskeer.ui

import com.simposio.taskeer.ui.permission.NotificationPermissionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {

    private fun shouldRequest(
        somethingToNotify: Boolean = true,
        granted: Boolean = false,
        sdkInt: Int = 34,
        alreadyAsked: Boolean = false,
    ) = NotificationPermissionPolicy.shouldRequest(somethingToNotify, granted, sdkInt, alreadyAsked)

    @Test
    fun shouldRequest_sinNadaQueAvisar_noPide() {
        assertFalse(shouldRequest(somethingToNotify = false))
    }

    @Test
    fun shouldRequest_conPermisoConcedido_noPide() {
        assertFalse(shouldRequest(granted = true))
    }

    @Test
    fun shouldRequest_enAndroidAnterior33_noPide() {
        assertFalse(shouldRequest(sdkInt = 32))
    }

    @Test
    fun shouldRequest_enAndroid33ConAlgoQueAvisarYSinConceder_pide() {
        assertTrue(shouldRequest(sdkInt = 33))
    }

    @Test
    fun shouldRequest_yaPreguntadoEnLaTransicion_noRepite() {
        assertFalse(shouldRequest(alreadyAsked = true))
    }

    private fun isBlocked(
        somethingToNotify: Boolean = true,
        granted: Boolean = false,
        sdkInt: Int = 34,
        answered: Boolean = true,
    ) = NotificationPermissionPolicy.isBlocked(somethingToNotify, granted, sdkInt, answered)

    @Test
    fun isBlocked_api32ConNotificacionesDesactivadas_esVerdadero() {
        assertTrue(isBlocked(sdkInt = 32, answered = false))
    }

    @Test
    fun isBlocked_api33SinResponderElDialogo_esFalso() {
        assertFalse(isBlocked(sdkInt = 33, answered = false))
    }

    @Test
    fun isBlocked_api33RespondidoYDenegado_esVerdadero() {
        assertTrue(isBlocked(sdkInt = 33, answered = true))
    }

    @Test
    fun isBlocked_sinNadaQueAvisar_esFalso() {
        assertFalse(isBlocked(somethingToNotify = false))
        assertFalse(isBlocked(somethingToNotify = false, sdkInt = 32))
    }

    @Test
    fun isBlocked_conPermisoConcedido_esFalso() {
        assertFalse(isBlocked(granted = true))
        assertFalse(isBlocked(granted = true, sdkInt = 32))
    }
}
