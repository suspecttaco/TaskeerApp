package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginSourceRulesTest {

    private val authSources = MainSources.kotlinFiles("ui/auth")

    @Test
    fun soloElViewModelDeLoginUsaSavedStateHandleYSoloParaElIndicadorDeCodigos() {
        assertTrue(authSources.isNotEmpty())
        assertEquals(listOf("LoginViewModel.kt"), authSources.filter { it.readText().contains("SavedStateHandle") }.map { it.name })
        val uses = MainSources.kotlin("ui/auth/LoginViewModel.kt").lines()
            .filter { it.contains("savedState.") || it.contains("savedState[") }
        assertTrue(uses.isNotEmpty() && uses.all { it.contains("KEY_CODES_PENDING") })
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
