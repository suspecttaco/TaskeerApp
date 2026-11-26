package com.simposio.taskeer.ui

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskeerApplicationSourceTest {

    @Test
    fun onCreate_lanzaResumeEnSegundoPlano() {
        val source = MainSources.kotlin("TaskeerApplication.kt")

        assertTrue(source.contains("scope.launch { container.authRepository.resume() }"))
        assertTrue(source.contains("CoroutineScope(SupervisorJob() + Dispatchers.IO)"))
        assertFalse(source.contains("runBlocking"))
    }
}
