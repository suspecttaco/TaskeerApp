package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SupabaseDependencyRulesTest {

    private val projectRoot: File = run {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        generateSequence(userDir) { it.parentFile }.first { File(it, "gradle/libs.versions.toml").isFile }
    }
    private val catalog = File(projectRoot, "gradle/libs.versions.toml").readText()
    private val appBuild = File(projectRoot, "app/build.gradle.kts").readText()

    @Test
    fun sinDependenciasNuevas() {
        val libraries = catalog.substringAfter("[libraries]").substringBefore("[plugins]").lines().filter { it.contains("group =") }
        val implementations = appBuild.lines().map { it.trim() }.filter { it.startsWith("implementation(libs.") }

        assertFalse(libraries.any { Regex("supabase|gotrue|ktor|retrofit|hilt|coil", RegexOption.IGNORE_CASE).containsMatchIn(it) })
        assertEquals(
            listOf(
                "androidx.activity.compose", "androidx.compose.material.icons.extended", "androidx.compose.material3",
                "androidx.compose.ui", "androidx.compose.ui.graphics", "androidx.compose.ui.tooling.preview",
                "androidx.core.ktx", "androidx.credentials", "androidx.credentials.play.services.auth",
                "androidx.datastore.preferences", "androidx.lifecycle.runtime.compose", "androidx.lifecycle.runtime.ktx",
                "androidx.lifecycle.viewmodel.compose", "androidx.lifecycle.viewmodel.ktx", "androidx.navigation.compose",
                "androidx.room.runtime", "androidx.work.runtime", "googleid", "okhttp",
            ),
            implementations.map { it.removePrefix("implementation(libs.").removeSuffix(")") },
        )
    }
}
