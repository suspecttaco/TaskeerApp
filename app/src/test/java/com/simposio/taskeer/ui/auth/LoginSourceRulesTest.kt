package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginSourceRulesTest {

    private val authSources = MainSources.kotlinFiles("ui/auth")

    @Test
    fun loginNoUsaSavedStateHandle() {
        assertTrue(authSources.isNotEmpty())
        assertTrue(authSources.none { it.readText().contains("SavedStateHandle") })
    }

    @Test
    fun loginNoUsaRememberSaveableNiRememberTextFieldStateParaLaPassword() {
        val offenders = authSources.filter {
            val text = it.readText()
            text.contains("rememberSaveable") || text.contains("rememberTextFieldState") || text.contains("mutableStateOf(password")
        }

        assertTrue("persistencia de la contraseña en ${offenders.map { it.name }}", offenders.isEmpty())
    }
}
