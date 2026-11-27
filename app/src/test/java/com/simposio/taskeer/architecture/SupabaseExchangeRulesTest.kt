package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseExchangeRulesTest {

    @Test
    fun soloElTransporteUsaOkHttp() {
        val offenders = MainSources.kotlinFiles("").filter { it.readText().contains("okhttp3") }.map { it.name }.sorted()

        assertEquals(listOf("DebugHttpLogInterceptor.kt", "OkHttpStreamTransport.kt", "OkHttpTransport.kt"), offenders)
        MainSources.kotlinFiles("data/remote/supabase").forEach { assertFalse(it.name, it.readText().contains("okhttp3")) }
    }

    @Test
    fun elCanjeNoUsaApiClient() {
        MainSources.kotlinFiles("data/remote/supabase").forEach { file ->
            val code = file.readLines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
            listOf("ApiClient", "RealApiClient", "ApiRequest", "AuthSession", "X-Client", "Authorization").forEach {
                assertFalse("${file.name} usa $it", code.contains(it))
            }
        }
    }

    @Test
    fun elSegundoTransporteSeConstruyeSoloEnLaFabrica() {
        val offenders = MainSources.kotlinFiles("").filter { it.readText().contains("::OkHttpTransport") || it.readText().contains("OkHttpTransport(") }
            .map { it.name }.sorted()

        assertEquals(listOf("ApiClientFactory.kt", "OkHttpTransport.kt"), offenders)
        assertEquals(2, Regex("OkHttpTransport").findAll(MainSources.kotlin("data/remote/ApiClientFactory.kt").lines().filterNot { it.startsWith("import") }.joinToString("\n")).count())
    }

    @Test
    fun apiConfigYApiClientSinCambios() {
        listOf("ApiConfig.kt", "ApiClient.kt", "RealApiClient.kt", "HttpTransport.kt", "transport/OkHttpTransport.kt").forEach { path ->
            assertFalse("$path conoce Supabase", MainSources.kotlin("data/remote/$path").contains("upabase"))
        }
        assertTrue(MainSources.kotlin("data/remote/ApiConfig.kt").contains("class ApiConfig("))
    }

    @Test
    fun supabaseSoloEnDataRemoteYDi() {
        listOf("ui", "domain", "data/repository", "data/local", "data/sync", "data/auth/google").forEach { dir ->
            MainSources.kotlinFiles(dir).forEach { file ->
                assertFalse("${file.name} importa data.remote.supabase", file.readText().contains("data.remote.supabase"))
            }
        }
        MainSources.kotlinFiles("ui").forEach { assertFalse("${it.name} menciona Supabase", it.readText().contains("upabase")) }
    }

    @Test
    fun sinSdkDeSupabase() {
        val repo = generateSequence(java.io.File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
            .first { java.io.File(it, "gradle/libs.versions.toml").isFile }
        val catalog = java.io.File(repo, "gradle/libs.versions.toml").readText()
        val build = java.io.File(repo, "app/build.gradle.kts").readText()

        assertFalse(catalog.contains("supabase", ignoreCase = true) || catalog.contains("gotrue", ignoreCase = true))
        assertFalse(build.contains("io.github.jan-tennert") || build.contains("supabase-kt"))
    }
}
