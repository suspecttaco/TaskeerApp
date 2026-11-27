package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.TransportResult
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncTimestamps
import com.simposio.taskeer.testing.FakeSyncBackend
import com.simposio.taskeer.testing.at
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// El servidor falso no puede divergir del contrato: repite los vectores de push con respuesta (backend-spec §11) y compara estado,
// versión y campos. V06-V13 los cubre ConflictVectorsTest; V01-V04, V21 y V22 (lectura) están en SyncApiContractTest y las
// pruebas de traída; V14 (FORBIDDEN) y V20 (validación de forma) describen reglas del servidor que el falso no modela.
class FakeSyncBackendConformanceTest {

    private val contract = SyncApiContractTest()

    private fun vector(name: String) =
        JSONObject(requireNotNull(javaClass.getResource("/sync-contract/v1/$name.json")) { "falta el vector $name" }.readText())

    private fun backendFor(v: JSONObject, setup: FakeSyncBackend.() -> Unit = {}): FakeSyncBackend {
        val backend = FakeSyncBackend(
            supported = SyncEntity.entries.toSet(),
            serverNow = SyncTimestamps.parse(v.optString("serverNow", "2026-11-26T18:30:00.000Z"))!!,
        )
        backend.setup()
        return backend
    }

    private suspend fun push(backend: FakeSyncBackend, mutation: JSONObject): PushOutcome {
        val request = JSONObject().put("mutations", org.json.JSONArray().put(mutation))
        return (backend.push(contract.mutationsOf(request)) as TransportResult.Ok).value.single()
    }

    private fun recordOf(outcome: PushOutcome) = when (outcome) {
        is PushOutcome.Applied -> outcome.record
        is PushOutcome.Conflict -> outcome.record
        is PushOutcome.Rejected -> outcome.record
    }

    @Test
    fun v18_miDia_renovarAcotarYBorrar() = runTest {
        val v = vector("V18")
        val taskId = v.getString("task")
        val backend = backendFor(v) {
            seed(SyncEntity.TASK, taskId, mapOf("title" to "x"), at("2026-11-20T00:00:00Z"))
            seed(
                SyncEntity.MY_DAY, taskId,
                mapOf("taskId" to taskId, "addedAt" to v.getJSONObject("server").getString("addedAt")),
                SyncTimestamps.parse(v.getJSONObject("server").getString("updatedAt"))!!,
            )
        }
        val expected = v.getJSONObject("response").getJSONArray("results").getJSONObject(0).getJSONObject("record")

        val renewed = push(backend, v.getJSONObject("request").getJSONArray("mutations").getJSONObject(0)) as PushOutcome.Applied

        assertEquals(expected.getString("id"), renewed.record!!.id)
        assertEquals(expected.getString("taskId"), renewed.record!!.fields["taskId"])
        assertEquals(expected.getString("addedAt"), renewed.record!!.fields["addedAt"])
        assertEquals(SyncTimestamps.parse(expected.getString("updatedAt")), renewed.record!!.updatedAt)
        assertNull(renewed.record!!.deletedAt)
        val cases = v.getJSONArray("followUps")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val expect = case.getJSONObject("expect")
            val outcome = push(backend, case.getJSONObject("mutation"))

            assertEquals(case.getString("name"), expect.getString("status"), if (outcome is PushOutcome.Applied) "applied" else "other")
            if (expect.has("updatedAt")) assertEquals(case.getString("name"), SyncTimestamps.parse(expect.getString("updatedAt")), recordOf(outcome)?.updatedAt)
            if (expect.has("addedAt")) assertEquals(case.getString("name"), expect.getString("addedAt"), recordOf(outcome)?.fields?.get("addedAt"))
            if (expect.has("deleted")) assertEquals(case.getString("name"), expect.getBoolean("deleted"), recordOf(outcome)?.isDeleted)
        }
    }

    @Test
    fun v18_borrarSinMarca_esAppliedSinCambios() = runTest {
        val v = vector("V18")
        val backend = backendFor(v)
        val delete = v.getJSONArray("followUps").getJSONObject(2).getJSONObject("mutation")

        val outcome = push(backend, delete)

        assertNotNull(outcome as? PushOutcome.Applied)
    }

    @Test
    fun v19_recordatorio_altaSentYNoVuelveAPending() = runTest {
        val v = vector("V19")
        val taskId = v.getString("task")
        val backend = backendFor(v) { seed(SyncEntity.TASK, taskId, mapOf("title" to "x"), at("2026-11-20T00:00:00Z")) }
        val expected = v.getJSONObject("response").getJSONArray("results").getJSONObject(0).getJSONObject("record")

        val created = push(backend, v.getJSONObject("request").getJSONArray("mutations").getJSONObject(0)) as PushOutcome.Applied

        assertEquals(expected.getString("id"), created.record!!.id)
        assertEquals("MOBILE", created.record!!.fields["origin"])
        assertEquals(expected.getString("status"), created.record!!.fields["status"])
        val cases = v.getJSONArray("followUps")
        val sent = push(backend, cases.getJSONObject(0).getJSONObject("mutation"))
        assertEquals("SENT", recordOf(sent)?.fields?.get("status"))
        assertNotNull(sent as? PushOutcome.Applied)
        val back = push(backend, cases.getJSONObject(1).getJSONObject("mutation")) as PushOutcome.Rejected
        assertEquals(cases.getJSONObject(1).getJSONObject("expect").getString("code"), back.code)
    }

    @Test
    fun v19_recordatorioSinTarea_esDependenciaFaltante() = runTest {
        val v = vector("V19")
        val backend = backendFor(v)

        val outcome = push(backend, v.getJSONObject("request").getJSONArray("mutations").getJSONObject(0)) as PushOutcome.Rejected

        assertEquals("DEPENDENCY_NOT_FOUND", outcome.code)
    }
}
