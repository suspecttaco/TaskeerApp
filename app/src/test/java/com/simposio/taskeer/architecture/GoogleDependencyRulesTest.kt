package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDependencyRulesTest {

    private val projectRoot: File = run {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        generateSequence(userDir) { it.parentFile }.first { File(it, "gradle/libs.versions.toml").isFile }
    }

    private val catalog = File(projectRoot, "gradle/libs.versions.toml").readText()
    private val appBuild = File(projectRoot, "app/build.gradle.kts").readText()
    private val forbiddenImports = listOf("androidx.credentials", "com.google.android.libraries")

    private fun libraryLines(): List<String> =
        catalog.substringAfter("[libraries]").substringBefore("[plugins]").lines().filter { it.contains("group =") }

    @Test
    fun soloDataAuthGoogleImportaCredentials() {
        val root = MainSources.kotlinRoot
        val googleDir = File(root, "data/auth/google")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.startsWith(googleDir) }
            .filter { file ->
                file.readLines().any { line -> line.startsWith("import ") && forbiddenImports.any { line.removePrefix("import ").startsWith(it) } }
            }
            .map { it.relativeTo(root).path }
            .toList()

        assertTrue("importan Credential Manager fuera de data/auth/google: $offenders", offenders.isEmpty())
        val importers = MainSources.kotlinFiles("data/auth/google").filter { file ->
            file.readLines().any { line -> line.startsWith("import ") && forbiddenImports.any { line.removePrefix("import ").startsWith(it) } }
        }
        assertEquals(listOf("CredentialManagerGoogleSource.kt"), importers.map { it.name })
    }

    @Test
    fun elCatalogoSoloGanaLasTresDependencias() {
        val google = libraryLines().filter { it.contains("androidx.credentials") || it.contains("com.google") }

        assertEquals(
            setOf(
                "androidx.credentials:credentials",
                "androidx.credentials:credentials-play-services-auth",
                "com.google.android.libraries.identity.googleid:googleid",
            ),
            google.map { line ->
                Regex("group = \"([^\"]+)\", name = \"([^\"]+)\"").find(line)!!.destructured.let { (g, n) -> "$g:$n" }
            }.toSet(),
        )
        assertEquals(3, google.size)
        assertFalse(libraryLines().any { it.contains("supabase") || it.contains("hilt") || it.contains("ktor") || it.contains("retrofit") })
        assertFalse(libraryLines().any { it.contains("name = \"play-services-auth\"") })
    }

    @Test
    fun elBuildDeclaraLasTresDependenciasPorElCatalogo() {
        val lines = appBuild.lines().filter { it.trim().startsWith("implementation(libs.") }

        assertEquals(1, lines.count { it.contains("libs.androidx.credentials)") })
        assertEquals(1, lines.count { it.contains("libs.androidx.credentials.play.services.auth)") })
        assertEquals(1, lines.count { it.contains("libs.googleid)") })
        assertFalse(appBuild.contains("com.google.android.libraries"))
    }

    @Test
    fun elClientIdNoEstaEnElCodigo() {
        val clientId = Regex("\\d{6,}-[a-z0-9]+\\.apps\\.googleusercontent\\.com")
        val tracked = listOf(File(projectRoot, "app/src/main"), File(projectRoot, "app/build.gradle.kts"), File(projectRoot, "gradle.properties"), File(projectRoot, "gradle/libs.versions.toml"))
        val offenders = tracked.flatMap { it.walkTopDown().filter { f -> f.isFile }.toList() }
            .filter { clientId.containsMatchIn(it.readText()) }
            .map { it.name }

        assertTrue("ID de cliente de Google en el repositorio: $offenders", offenders.isEmpty())
        assertTrue(appBuild.contains("providers.gradleProperty(\"taskeer.googleWebClientId\").getOrElse(\"\")"))
        assertEquals(2, Regex("buildConfigField\\(\"String\", \"GOOGLE_WEB_CLIENT_ID\"").findAll(appBuild).count())
        assertTrue(MainSources.kotlin("di/AppContainerFactory.kt").contains("GoogleSignInConfig(BuildConfig.GOOGLE_WEB_CLIENT_ID, exchangeConfigured = remote.googleExchangeConfigured)"))
    }
}
