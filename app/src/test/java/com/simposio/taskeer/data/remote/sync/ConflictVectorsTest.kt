package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.TransportResult
import com.simposio.taskeer.domain.sync.ConflictRules
import com.simposio.taskeer.domain.sync.ConflictVerdict
import com.simposio.taskeer.domain.sync.LocalVersion
import com.simposio.taskeer.domain.sync.ServerVersion
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.domain.sync.SyncTimestamps
import com.simposio.taskeer.testing.FakeSyncBackend
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Vectores V06-V10, V12 y V13 de backend-spec §11: el servidor falso y ConflictRules deben dar el mismo veredicto.
class ConflictVectorsTest {

    private val contract = SyncApiContractTest()

    private fun vector(name: String) =
        JSONObject(requireNotNull(javaClass.getResource("/sync-contract/v1/$name.json")) { "falta el vector $name" }.readText())

    private fun plain(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().toList().sorted().associateWith { plain(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        else -> value
    }

    @Suppress("UNCHECKED_CAST")
    private fun backendFor(case: JSONObject): FakeSyncBackend {
        val backend = FakeSyncBackend(serverNow = SyncTimestamps.parse(case.getString("serverNow"))!!)
        backend.seed(SyncEntity.LIST, case.getString("list"), mapOf("name" to "Tareas"), Instant.EPOCH)
        val server = case.getJSONObject("server")
        backend.seed(
            SyncEntity.TASK,
            server.getString("id"),
            plain(server.getJSONObject("fields")) as Map<String, Any?>,
            SyncTimestamps.parse(server.getString("updatedAt"))!!,
            if (server.isNull("deletedAt")) null else SyncTimestamps.parse(server.getString("deletedAt")),
        )
        return backend
    }

    private fun check(name: String) = runTest {
        val cases = vector(name).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val label = "$name: ${case.getString("name")}"
            val request = JSONObject().put("mutations", JSONArray().put(case.getJSONObject("mutation")))
            val mutation = contract.mutationsOf(request).single()
            val backend = backendFor(case)
            val expect = case.getJSONObject("expect")

            val outcome = (backend.push(listOf(mutation)) as TransportResult.Ok).value.single()

            val record = when (outcome) {
                is PushOutcome.Applied -> outcome.record!!
                is PushOutcome.Conflict -> outcome.record
                is PushOutcome.Rejected -> error("$label: rechazado")
            }
            assertEquals(label, expect.getString("status"), if (outcome is PushOutcome.Applied) "applied" else "conflict")
            assertEquals(label, SyncTimestamps.parse(expect.getString("updatedAt")), record.updatedAt)
            if (expect.has("deleted")) assertEquals(label, expect.getBoolean("deleted"), record.isDeleted)
            if (!isNoOp(case, mutation.data)) assertEquals(label, outcome is PushOutcome.Applied, rulesLetLocalWin(case, mutation))
        }
    }

    private fun isNoOp(case: JSONObject, data: Map<String, Any?>?) =
        data != null && data == plain(case.getJSONObject("server").getJSONObject("fields"))

    private fun rulesLetLocalWin(case: JSONObject, m: PushMutation): Boolean {
        val server = case.getJSONObject("server")
        val serverVersion = ServerVersion(SyncTimestamps.parse(server.getString("updatedAt"))!!, !server.isNull("deletedAt"))
        val verdict = ConflictRules.resolve(
            m.baseUpdatedAt,
            serverVersion,
            LocalVersion(m.clientUpdatedAt, m.op == SyncOp.DELETE),
            SyncTimestamps.parse(case.getString("serverNow"))!!,
        )
        return verdict == ConflictVerdict.LocalWins
    }

    @Test
    fun v06_reenvioIdempotente_mismoResultadoYUnaSolaFila() = runTest {
        val v = vector("V06")
        val batch = contract.mutationsOf(v.getJSONObject("request"))
        val backend = FakeSyncBackend()
        backend.seed(SyncEntity.LIST, v.getString("list"), mapOf("name" to "Tareas"), Instant.EPOCH)

        val first = (backend.push(batch) as TransportResult.Ok).value.single() as PushOutcome.Applied
        backend.serverNow = backend.serverNow.plusSeconds(60)
        val second = (backend.push(batch) as TransportResult.Ok).value.single() as PushOutcome.Applied

        assertEquals(first.record!!.updatedAt, second.record!!.updatedAt)
        assertEquals(1, backend.records.keys.count { it.first == SyncEntity.TASK })
        assertTrue(v.getJSONObject("expect").getBoolean("sameResult"))
    }

    @Test
    fun v07_mismaBaseConRelojAnterior_seAplica() = check("V07")

    @Test
    fun v08_servidorCambioYClienteMasViejo_conflicto() = check("V08")

    @Test
    fun v09_clienteMasReciente_seAplicaConServerNow() = check("V09")

    @Test
    fun v10_empateYRelojAdelantado() = check("V10")

    @Test
    fun v12_borradoMasViejoOMasRecienteQueLaEdicionDelServidor() = check("V12")

    @Test
    fun v13_noOp_conservaUpdatedAt() = check("V13")
}
