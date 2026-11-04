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
}
