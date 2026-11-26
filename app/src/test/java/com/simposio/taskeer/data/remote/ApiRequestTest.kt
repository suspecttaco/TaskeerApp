package com.simposio.taskeer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApiRequestTest {

    @Test
    fun toString_noIncluyeElCuerpo() {
        val request = ApiRequest(HttpMethod.POST, "auth/x", jsonBody = "{\"password\":\"secreto\"}")

        assertEquals("ApiRequest(POST auth/x)", request.toString())
        assertFalse(request.toString().contains("secreto"))
    }

    @Test
    fun toString_deApiResponseNoIncluyeElCuerpo() {
        assertEquals("ApiResponse(200)", ApiResponse(200, "token-secreto").toString())
    }
}
