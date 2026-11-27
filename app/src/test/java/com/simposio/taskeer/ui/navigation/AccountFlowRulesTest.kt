package com.simposio.taskeer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountFlowRulesTest {

    @Test
    fun conSettings_anclaEnSettings() {
        val routes = listOf(AppDestination.HOME.route, AppDestination.SETTINGS.route, AppDestination.LOGIN.route)

        assertEquals(AppDestination.SETTINGS.route, accountFlowAnchor(routes))
    }

    @Test
    fun sinSettingsConHome_anclaEnHome() {
        val routes = listOf(AppDestination.START.route, AppDestination.HOME.route, AppDestination.LOGIN.route)

        assertEquals(AppDestination.HOME.route, accountFlowAnchor(routes))
    }

    @Test
    fun sinNinguno_anclaEnInicio() {
        assertEquals(AppDestination.START.route, accountFlowAnchor(listOf(AppDestination.LOGIN.route)))
        assertEquals(AppDestination.START.route, accountFlowAnchor(emptyList()))
    }

    @Test
    fun ignoraNulos() {
        assertEquals(AppDestination.HOME.route, accountFlowAnchor(listOf(null, AppDestination.HOME.route, null)))
    }
}
