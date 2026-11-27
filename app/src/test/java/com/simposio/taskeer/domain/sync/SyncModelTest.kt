package com.simposio.taskeer.domain.sync

import com.simposio.taskeer.testing.MainSources
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelTest {

    @Test
    fun sinDependenciasDeAndroid() {
        val forbidden = listOf("android.", "androidx.", "com.simposio.taskeer.data", "com.simposio.taskeer.ui", "com.simposio.taskeer.di")
        val files = MainSources.kotlinFiles("domain/sync")

        val violations = files.flatMap { file ->
            file.readLines().map { it.trim() }.filter { it.startsWith("import ") }
                .map { it.removePrefix("import ") }
                .filter { imported -> forbidden.any { imported.startsWith(it) } }
                .map { "${file.name} importa $it" }
        }

        assertTrue(files.isNotEmpty())
        assertTrue("domain/sync debe ser Kotlin puro: $violations", violations.isEmpty())
    }

    @Test
    fun toStringDeLaCola_noIncluyeIds() {
        val op = QueuedOp("m-secreto", SyncEntity.TASK, "tarea-secreta", SyncOp.UPSERT, Instant.EPOCH)

        assertFalse(op.toString().contains("tarea-secreta"))
        assertFalse(op.toString().contains("m-secreto"))
    }

    @Test
    fun marcasDeTiempo_llevanMilisegundosYZ() {
        assertTrue(SyncTimestamps.format(Instant.parse("2026-11-26T18:29:10Z")) == "2026-11-26T18:29:10.000Z")
    }
}
