package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.domain.sync.SyncTimestamps
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncApiContractTest {

    private fun vector(name: String): JSONObject =
        JSONObject(requireNotNull(javaClass.getResource("/sync-contract/v1/$name.json")) { "falta el vector $name" }.readText())

    internal fun mutationsOf(request: JSONObject): List<PushMutation> {
        val array = request.getJSONArray("mutations")
        return (0 until array.length()).map { i ->
            val node = array.getJSONObject(i)
            PushMutation(
                clientMutationId = node.getString("clientMutationId"),
                entity = SyncEntity.fromWire(node.getString("entity"))!!,
                op = if (node.getString("op") == "delete") SyncOp.DELETE else SyncOp.UPSERT,
                id = node.getString("id"),
                baseUpdatedAt = if (node.isNull("baseUpdatedAt")) null else SyncTimestamps.parse(node.getString("baseUpdatedAt")),
                clientUpdatedAt = SyncTimestamps.parse(node.getString("clientUpdatedAt"))!!,
                data = node.optJSONObject("data")?.let { plain(it) as Map<String, Any?> },
            )
        }
    }

    private fun plain(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().toList().sorted().associateWith { plain(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        else -> value
    }

    @Test
    fun vectores_push_serializacionExacta() {
        for (name in listOf("V05", "V11", "V14", "V16", "V20")) {
            val request = vector(name).getJSONObject("request")

            val sent = SyncApiContract.serializeBatch(mutationsOf(request))

            assertEquals("vector $name", plain(request), plain(JSONObject(sent)))
        }
    }

    @Test
    fun vectores_parseoDeResultados() {
        for (name in listOf("V05", "V11", "V14", "V16", "V20")) {
            val v = vector(name)
            val batch = mutationsOf(v.getJSONObject("request"))
            val expected = v.getJSONObject("response").getJSONArray("results")

            val parsed = SyncApiContract.parsePush(v.getJSONObject("response").toString(), batch) as SyncApiContract.Parsed.Ok

            assertEquals("vector $name", expected.length(), parsed.value.size)
            for (i in parsed.value.indices) {
                val outcome = parsed.value[i]
                val node = expected.getJSONObject(i)
                assertEquals(node.getString("clientMutationId"), outcome.clientMutationId)
                val kind = when (outcome) {
                    is PushOutcome.Applied -> "applied"
                    is PushOutcome.Conflict -> "conflict"
                    is PushOutcome.Rejected -> "rejected"
                }
                assertEquals(node.getString("status"), kind)
            }
        }
        val v11 = vector("V11")
        val tombstone = (SyncApiContract.parsePush(
            v11.getJSONObject("response").toString(),
            mutationsOf(v11.getJSONObject("request")),
        ) as SyncApiContract.Parsed.Ok).value[0] as PushOutcome.Conflict
        assertEquals(SyncTimestamps.parse("2026-11-26T12:00:00.000Z"), tombstone.record.deletedAt)
        val v16 = vector("V16")
        val taken = (SyncApiContract.parsePush(
            v16.getJSONObject("response").toString(),
            mutationsOf(v16.getJSONObject("request")),
        ) as SyncApiContract.Parsed.Ok).value.single() as PushOutcome.Rejected
        assertEquals("LIST_NAME_TAKEN", taken.code)
        assertEquals("Tareas", taken.record!!.fields["name"])
    }

    @Test
    fun serializaDeleteSinData() {
        val request = vector("V11").getJSONObject("request")

        val sent = JSONObject(SyncApiContract.serializeBatch(mutationsOf(request))).getJSONArray("mutations")

        assertTrue(sent.getJSONObject(0).has("data"))
        assertTrue(!sent.getJSONObject(1).has("data"))
        assertEquals("delete", sent.getJSONObject(1).getString("op"))
    }

    @Test
    fun resultadoDesconocido_esRechazoPermanente() {
        val v = vector("V05")
        val response = v.getJSONObject("response")
        response.getJSONArray("results").getJSONObject(0).put("status", "pendiente")

        val parsed = SyncApiContract.parsePush(response.toString(), mutationsOf(v.getJSONObject("request"))) as SyncApiContract.Parsed.Ok

        val outcome = parsed.value.single() as PushOutcome.Rejected
        assertEquals(SyncApiContract.CODE_UNKNOWN_RESULT, outcome.code)
    }

    @Test
    fun ignoraCamposDesconocidos() {
        val v = vector("V05")
        val response = v.getJSONObject("response").put("campoFuturo", JSONObject().put("a", 1))
        response.getJSONArray("results").getJSONObject(0).put("extra", "x")

        val parsed = SyncApiContract.parsePush(response.toString(), mutationsOf(v.getJSONObject("request")))

        assertTrue(parsed is SyncApiContract.Parsed.Ok)
        val identity = SyncApiContract.parseIdentity(vector("V22").getJSONObject("changes").toString()) as SyncApiContract.Parsed.Ok
        assertEquals(setOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK), identity.value.supportedEntities)
    }

    @Test
    fun contractVersionOtraMajor_updateRequired() {
        val v22 = vector("V22")

        assertTrue(SyncApiContract.parseIdentity(v22.getJSONObject("otraMajor").toString()) is SyncApiContract.Parsed.UpdateRequired)
        val v = vector("V05")
        val response = v.getJSONObject("response").put("contractVersion", "2.1")
        assertTrue(SyncApiContract.parsePush(response.toString(), mutationsOf(v.getJSONObject("request"))) is SyncApiContract.Parsed.UpdateRequired)
    }

    @Test
    fun serializacionConservaFechasConMilisegundos() {
        val request = vector("V05").getJSONObject("request")

        val sent = JSONObject(SyncApiContract.serializeBatch(mutationsOf(request))).getJSONArray("mutations").getJSONObject(0)

        assertEquals("2026-11-26T18:29:10.000Z", sent.getString("clientUpdatedAt"))
        assertTrue(sent.isNull("baseUpdatedAt"))
    }
}
