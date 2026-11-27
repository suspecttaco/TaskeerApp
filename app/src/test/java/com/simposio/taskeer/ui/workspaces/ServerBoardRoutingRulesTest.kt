package com.simposio.taskeer.ui.workspaces

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerBoardRoutingRulesTest {

    @Test
    fun conCopiaLocal_abreKanban() {
        assertEquals(ServerBoardRoute.OpenKanban("b1"), ServerBoardRoutingRules.route(true, "b1", setOf("b1", "b2")))
    }

    @Test
    fun sinCopia_avisaSincroniza() {
        assertEquals(ServerBoardRoute.SyncFirst, ServerBoardRoutingRules.route(true, "b1", setOf("b2")))
    }

    @Test
    fun sinSesion_noOfrece() {
        assertEquals(ServerBoardRoute.NotOffered, ServerBoardRoutingRules.route(false, "b1", setOf("b1")))
    }
}
