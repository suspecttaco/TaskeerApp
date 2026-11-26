package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLiveLayeringTest {

    private val liveRemoteFiles = listOf("RemoteNotificationLiveChannel.kt", "SseParser.kt")

    @Test
    fun contratoNoExponeTiposRemotosNiDeAndroid() {
        val contract = MainSources.kotlin("data/repository/NotificationLiveChannel.kt")

        assertTrue(
            listOf("data.remote", "org.json", "okhttp3", "android.").none { contract.contains(it) },
        )
    }

    @Test
    fun dominioDelCanalEsKotlinPuro() {
        val files = MainSources.kotlinFiles("domain/notification").filter { it.name in setOf("LiveChannelState.kt", "LiveReconnectRules.kt") }
        val forbidden = listOf("android.", "androidx.", "org.json", "okhttp3", "com.simposio.taskeer.data")

        val violations = files.flatMap { file ->
            file.readLines().filter { it.startsWith("import ") }
                .filter { line -> forbidden.any { line.removePrefix("import ").startsWith(it) } }
                .map { "${file.name}: $it" }
        }

        assertTrue("dominio con dependencias: $violations", violations.isEmpty())
        assertTrue(files.size == 2)
    }

    @Test
    fun elCanalNoUsaRoomNiDataStore() {
        val files = MainSources.kotlinFiles("data/remote/notifications").filter { it.name in liveRemoteFiles }

        val violations = files.flatMap { file ->
            listOf("androidx.room", "androidx.datastore", "data.local", "SharedPreferences")
                .filter { file.readText().contains(it) }
                .map { "${file.name} referencia $it" }
        }

        assertTrue("el canal vive solo en memoria: $violations", violations.isEmpty())
        assertTrue(files.size == 2)
    }

    @Test
    fun laInterfazDeUsuarioSoloConoceElContrato() {
        val violations = MainSources.kotlinFiles("ui").flatMap { file ->
            listOf("data.remote", "RemoteNotificationLiveChannel", "StreamTransport")
                .filter { file.readText().contains(it) }
                .map { "${file.name} referencia $it" }
        }

        assertTrue("ui no debe conocer el canal remoto: $violations", violations.isEmpty())
    }

    @Test
    fun elTransporteDeFlujoNoUsaJsonNiDominio() {
        val files = listOf(
            MainSources.kotlin("data/remote/StreamTransport.kt"),
            MainSources.kotlin("data/remote/transport/OkHttpStreamTransport.kt"),
        )

        assertTrue(files.none { it.contains("org.json") || it.contains("com.simposio.taskeer.domain") })
    }
}
