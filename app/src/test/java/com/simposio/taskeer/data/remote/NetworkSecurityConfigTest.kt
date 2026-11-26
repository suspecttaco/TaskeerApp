package com.simposio.taskeer.data.remote

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSecurityConfigTest {

    private val appDir: File = resolveAppDir()
    private val mainManifest = File(appDir, "src/main/AndroidManifest.xml").readText()

    @Test
    fun manifiestoPrincipal_declaraInternet() {
        assertTrue(mainManifest.contains("android.permission.INTERNET"))
    }

    @Test
    fun manifiestoPrincipal_noDeclaraAccessNetworkState() {
        assertFalse(mainManifest.contains("ACCESS_NETWORK_STATE"))
    }

    @Test
    fun manifiestoPrincipal_noPermiteTraficoEnClaro() {
        assertFalse(mainManifest.contains("usesCleartextTraffic"))
        assertFalse(mainManifest.contains("networkSecurityConfig"))
        val mainResources = File(appDir, "src/main/res/xml").listFiles().orEmpty()
        assertFalse(mainResources.any { it.readText().contains("cleartextTrafficPermitted=\"true\"") })
    }

    @Test
    fun debug_permiteClaroSoloParaEmuladorYLocalhost() {
        val debugManifest = File(appDir, "src/debug/AndroidManifest.xml").readText()
        val config = File(appDir, "src/debug/res/xml/network_security_config.xml").readText()

        assertTrue(debugManifest.contains("@xml/network_security_config"))
        val domains = Regex("<domain[^>]*>([^<]+)</domain>").findAll(config).map { it.groupValues[1] }.toList()
        assertEquals(listOf("10.0.2.2", "localhost"), domains)
        assertEquals(1, Regex("cleartextTrafficPermitted=\"true\"").findAll(config).count())
        assertFalse(config.contains("<base-config"))
    }

    @Test
    fun backupRules_excluyeElFicheroDeSesion() {
        val rules = File(appDir, "src/main/res/xml/backup_rules.xml").readText()

        assertTrue(rules.contains("<exclude domain=\"sharedpref\" path=\"taskeer_session.xml\""))
    }

    @Test
    fun dataExtractionRules_excluyeElFicheroDeSesionEnBackupYTransferencia() {
        val rules = File(appDir, "src/main/res/xml/data_extraction_rules.xml").readText()
        val exclusion = "<exclude domain=\"sharedpref\" path=\"taskeer_session.xml\""

        val cloud = rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfer = rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        assertTrue(cloud.contains(exclusion))
        assertTrue(transfer.contains(exclusion))
    }

    @Test
    fun main_noContienePinSetNiConfiguracionDeConfianzaPropia() {
        val offenders = File(appDir, "src/main").walkTopDown()
            .filter { it.isFile && it.extension in setOf("xml", "kt") }
            .filter { file ->
                val text = file.readText()
                listOf("pin-set", "<trust-anchors", "usesCleartextTraffic=\"true\"").any { text.contains(it) }
            }
            .map { it.relativeTo(appDir).path }
            .toList()

        assertTrue("configuración de confianza propia en $offenders", offenders.isEmpty())
    }

    private fun resolveAppDir(): File {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .flatMap { sequenceOf(File(it, "app"), it) }
            .firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }
            ?: error("No encontré app/src/main/AndroidManifest.xml subiendo desde $userDir")
    }
}
