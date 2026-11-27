package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountEntrySecretsRulesTest {

    private fun codeLines(path: String): List<String> =
        MainSources.kotlin(path).lines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("import ") }

    private fun uiAccountLines(): List<Pair<String, String>> =
        MainSources.kotlinFiles("ui/account").flatMap { file ->
            file.readLines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("import ") }.map { file.name to it }
        }

    @Test
    fun uiAccountSinLogNiPrintln() {
        val offenders = uiAccountLines().filter { (_, line) -> Regex("\\bLog\\b|println|printStackTrace|Timber").containsMatchIn(line) }

        assertTrue("registro en ui/account: $offenders", offenders.isEmpty())
    }

    @Test
    fun sinPasswordTokenNiCodigosEnUiAccount() {
        val secret = Regex("password|token|recovery|CharArray|RecoveryCodes")
        val offenders = uiAccountLines().filter { (_, line) -> secret.containsMatchIn(line) }

        assertTrue("secretos en ui/account: $offenders", offenders.isEmpty())
    }

    @Test
    fun avatarRulesSinLog() {
        val offenders = codeLines("domain/auth/AvatarRules.kt").filter { Regex("\\bLog\\b|println|printStackTrace").containsMatchIn(it) }

        assertTrue("registro en AvatarRules: $offenders", offenders.isEmpty())
    }
}
