package com.simposio.taskeer.architecture

import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.LogoutOutcome
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.ui.auth.LoginUiState
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSecretsRulesTest {

    private val authDirectories = listOf(
        "ui/auth", "data/remote/auth", "data/auth", "domain/auth", "ui/register",
        "ui/passwordreset", "ui/accountsecurity",
    )

    @Test
    fun authNoUsaLogNiPrintln() {
        val offenders = authDirectories.flatMap { MainSources.kotlinFiles(it) }
            .filter { file ->
                val text = file.readText()
                listOf("android.util.Log", "Log.d(", "Log.e(", "Log.i(", "Log.w(", "Log.v(", "println(", "printStackTrace")
                    .any { text.contains(it) }
            }
            .map { it.name }

        assertTrue("logging en auth: $offenders", offenders.isEmpty())
    }

    @Test
    fun passwordYTokenNoEstanEnToStringDeTiposPublicos() {
        val account = AccountSummary("ana@example.com", "Ana")
        val rendered = listOf(
            account,
            SessionState.SignedIn(account, offline = true),
            SessionState.SignedOut(expired = true),
            LoginOutcome.SignedIn(account),
            LoginOutcome.Failed(DomainError.Unauthenticated("INVALID_CREDENTIALS", "x")),
            LogoutOutcome(serverRevoked = false),
            LoginUiState(email = "ana@example.com"),
        ).joinToString("\n").lowercase()

        assertFalse(rendered.contains("token"))
        assertFalse(rendered.contains("secreta"))
    }

    @Test
    fun ningunTipoDeUiOContratoTieneCampoDeTokenOContraseña() {
        val types = listOf(
            LoginUiState::class.java,
            SessionState.SignedIn::class.java,
            SessionState.SignedOut::class.java,
            AccountSummary::class.java,
            LoginOutcome.SignedIn::class.java,
            LoginOutcome.Failed::class.java,
            LogoutOutcome::class.java,
        )
        val offenders = types.flatMap { type ->
            type.declaredFields
                .filterNot { Modifier.isStatic(it.modifiers) }
                .filter { it.name.contains("token", ignoreCase = true) || it.name.contains("password", ignoreCase = true) }
                .filterNot { type == LoginUiState::class.java && it.name == "passwordResetCount" }
                .filterNot { type == AccountSummary::class.java && it.name == "hasPassword" && it.type == java.lang.Boolean::class.javaObjectType }
                .map { "${type.simpleName}.${it.name}" }
        }

        assertTrue("campos secretos: $offenders", offenders.isEmpty())
    }

    @Test
    fun elContratoNoDevuelveTokens() {
        val returnTypes = AuthRepository::class.java.methods.map { it.returnType.simpleName.lowercase() }

        assertTrue(returnTypes.none { it.contains("token") })
        assertEquals(0, AuthRepository::class.java.methods.count { it.name.contains("token", ignoreCase = true) })
    }
}
