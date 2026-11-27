package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigRulesTest {

    private val appDir: File = run {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        generateSequence(userDir) { it.parentFile }.first { File(it, "gradle/libs.versions.toml").isFile }
    }
    private val appBuild = File(appDir, "app/build.gradle.kts").readText()

    // Las propiedades se arman por partes para que este fichero no se detecte a sí mismo.
    private val urlProperty = "taskeer." + "supabaseUrl"
    private val keyProperty = "taskeer." + "supabaseAnonKey"

    @Test
    fun valoresFueraDelRepo() {
        val assigned = Regex("(${Regex.escape(urlProperty)}|${Regex.escape(keyProperty)})\\s*=\\s*\\S")
        val tracked = listOf(File(appDir, "gradle.properties"), File(appDir, "app/build.gradle.kts")) +
            File(appDir, "app/src").walkTopDown().filter { it.isFile }.toList()
        val offenders = tracked.filter { assigned.containsMatchIn(it.readText()) }.map { it.name }

        assertTrue("valor de Supabase en el repositorio: $offenders", offenders.isEmpty())
        assertFalse(File(appDir, "gradle.properties").readText().contains("supabase", ignoreCase = true))
    }

    @Test
    fun sinUrlNiJwtLiteralesEnElCodigo() {
        val hostedProject = Regex("[a-z0-9]{15,}\\.supabase\\.(co|in)")
        val jwt = Regex("eyJ[A-Za-z0-9_-]{10,}\\.")
        val offenders = MainSources.kotlinFiles("").filter { hostedProject.containsMatchIn(it.readText()) || jwt.containsMatchIn(it.readText()) }

        assertTrue("clave o proyecto de Supabase literal: ${offenders.map { it.name }}", offenders.isEmpty())
    }

    @Test
    fun buildConfigEnDebugYRelease() {
        assertTrue(appBuild.contains("providers.gradleProperty(\"$urlProperty\").getOrElse(\"\").trim()"))
        assertTrue(appBuild.contains("providers.gradleProperty(\"$keyProperty\").getOrElse(\"\").trim()"))
        assertEquals(2, Regex("buildConfigField\\(\"String\", \"SUPABASE_URL\"").findAll(appBuild).count())
        assertEquals(2, Regex("buildConfigField\\(\"String\", \"SUPABASE_ANON_KEY\"").findAll(appBuild).count())
    }

    @Test
    fun lasClavesSeLeenSoloEnLaFabricaRemota() {
        val readers = MainSources.kotlinFiles("").filter { it.readText().contains("BuildConfig.SUPABASE_") }.map { it.name }

        assertEquals(listOf("ApiClientFactory.kt"), readers)
    }
}
