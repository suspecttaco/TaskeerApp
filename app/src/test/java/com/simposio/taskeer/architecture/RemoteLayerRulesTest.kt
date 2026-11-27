package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLayerRulesTest {

    private val sourcesRoot: File = resolveSourcesRoot()
    private val remote = File(sourcesRoot, "data/remote")
    private val transportDir = File(remote, "transport")

    @Test
    fun okhttpSoloEnTransport() {
        val offenders = allSources().filter { it.readText().contains("okhttp3") }
            .map { it.relativeTo(sourcesRoot).path }
            .sorted()

        assertEquals(
            listOf(
                "data/remote/transport/DebugHttpLogInterceptor.kt",
                "data/remote/transport/OkHttpStreamTransport.kt",
                "data/remote/transport/OkHttpTransport.kt",
            ),
            offenders,
        )
    }

    @Test
    fun orgJsonSoloEnDataRemote() {
        val offenders = allSources().filter { it.readText().contains("org.json") }
            .filterNot { it.startsWith(remote) }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("org.json fuera de data/remote: $offenders", offenders.isEmpty())
        assertFalse(
            "org.json no debe entrar en el transporte",
            kotlinSources(transportDir).any { it.readText().contains("org.json") },
        )
    }

    @Test
    fun javaNetHttpSoloEnTransport() {
        val offenders = allSources()
            .filterNot { it.startsWith(transportDir) }
            .filter { it.readText().contains("HttpURLConnection") }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("HttpURLConnection fuera de transport: $offenders", offenders.isEmpty())
    }

    @Test
    fun domainUiDiYContratosNoConocenDataRemote() {
        val layers = listOf("domain", "ui", "di").map { File(sourcesRoot, it) }
        val contracts = File(sourcesRoot, "data/repository").listFiles { f -> f.name.endsWith("Repository.kt") }
            .orEmpty().toList()
        val offenders = (layers.flatMap { kotlinSources(it) } + contracts)
            .filter { it.readText().contains("data.remote") || it.readText().contains("data/remote") }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("capas que conocen data.remote: $offenders", offenders.isEmpty())
    }

    @Test
    fun noExisteDataSync() {
        assertFalse(File(sourcesRoot, "data/sync").exists())
    }

    @Test
    fun dataRemote_noImportaRoomNiDataLocal() {
        val offenders = allSources()
            .filter { text -> listOf("androidx.room", "data.local").any { text.readText().contains(it) } }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("data/remote conoce la capa local: $offenders", offenders.isEmpty())
    }

    @Test
    fun sinCertificatePinning() {
        val offenders = allSources()
            .filter { file -> listOf("CertificatePinner", "pin-set").any { file.readText().contains(it) } }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("certificate pinning en $offenders", offenders.isEmpty())
    }

    @Test
    fun sinTrustManagerNiHostnameVerifierPropios() {
        val offenders = allSources()
            .filter { file ->
                listOf("X509TrustManager", "TrustManager", "hostnameVerifier", "HostnameVerifier", "sslSocketFactory")
                    .any { file.readText().contains(it) }
            }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("confianza TLS propia en $offenders", offenders.isEmpty())
    }

    private fun allSources(): List<File> = kotlinSources(remote)

    private fun kotlinSources(directory: File): List<File> =
        directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun resolveSourcesRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
