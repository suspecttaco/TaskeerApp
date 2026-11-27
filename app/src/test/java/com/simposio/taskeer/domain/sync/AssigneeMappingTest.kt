package com.simposio.taskeer.domain.sync

import com.simposio.taskeer.domain.model.LocalUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssigneeMappingTest {

    private val me = "11111111-1111-4111-8111-111111111111"
    private val other = "22222222-2222-4222-8222-222222222222"

    @Test
    fun localUserAServidor() {
        assertEquals(me, AssigneeMapping.toServer(LocalUser.ID, me))
        assertNull(AssigneeMapping.toServer(null, me))
    }

    @Test
    fun propioAServidor_aLocalUser() {
        assertEquals(LocalUser.ID, AssigneeMapping.toLocal(me, me))
    }

    @Test
    fun ajenoSeConserva() {
        assertEquals(other, AssigneeMapping.toServer(other, me))
        assertEquals(other, AssigneeMapping.toLocal(other, me))
    }
}
