package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.sync.PullPage
import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.SyncCodes
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.domain.sync.SyncTimestamps
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        for (name in listOf("V05", "V11", "V14", "V16", "V17", "V18", "V19", "V20")) {
            val request = vector(name).getJSONObject("request")

            val sent = SyncApiContract.serializeBatch(mutationsOf(request))

            assertEquals("vector $name", plain(request), plain(JSONObject(sent)))
        }
    }

    @Test
    fun vectores_parseoDeResultados() {
        for (name in listOf("V05", "V11", "V14", "V16", "V17", "V18", "V19", "V20")) {
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
    fun appliedConRecordNulo_seAceptaSinInvalidarElLote() {
        val v = vector("V11")
        val batch = mutationsOf(v.getJSONObject("request"))
        val results = JSONArray()
        batch.forEach {
            results.put(JSONObject().put("clientMutationId", it.clientMutationId).put("status", "applied").put("record", JSONObject.NULL))
        }
        val response = v.getJSONObject("response").put("results", results)

        val parsed = SyncApiContract.parsePush(response.toString(), batch) as SyncApiContract.Parsed.Ok

        assertEquals(batch.size, parsed.value.size)
        assertTrue(parsed.value.all { it is PushOutcome.Applied && it.record == null })
    }

    @Test
    fun conflictSinRecord_seTrataComoFalloReintentableSinInvalidarElLote() {
        val v = vector("V05")
        val response = v.getJSONObject("response")
        response.getJSONArray("results").getJSONObject(0).put("status", "conflict").put("record", JSONObject.NULL)

        val parsed = SyncApiContract.parsePush(response.toString(), mutationsOf(v.getJSONObject("request"))) as SyncApiContract.Parsed.Ok

        assertEquals(SyncCodes.VALIDATION_ERROR, (parsed.value.single() as PushOutcome.Rejected).code)
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

    private fun pull(body: JSONObject): PullPage =
        (SyncApiContract.parsePull(body.toString()) as SyncApiContract.Parsed.Ok).value

    private fun minimalPage(changes: JSONObject? = JSONObject()): JSONObject = JSONObject()
        .put("contractVersion", "1.0")
        .put("supportedEntities", JSONArray(listOf("list", "task")))
        .put("me", JSONObject().put("userId", "11111111-1111-4111-8111-111111111111"))
        .put("serverTime", "2026-11-26T18:30:00.000Z")
        .put("isSnapshot", false)
        .apply { if (changes != null) put("changes", changes) }
        .put("nextCursor", "C1")
        .put("hasMore", false)

    @Test
    fun vectoresV01aV04_lectura() {
        val v01 = pull(vector("V01").getJSONObject("expect").getJSONObject("body"))
        assertTrue(v01.isSnapshot)
        assertEquals(setOf(SyncEntity.LIST, SyncEntity.TASK), v01.supportedEntities)
        assertEquals(listOf(SyncEntity.LIST, SyncEntity.TASK), v01.records.map { it.entity })
        assertEquals("<opaco>", v01.nextCursor)

        val pages = vector("V02").getJSONArray("pages").let { a -> (0 until a.length()).map { pull(a.getJSONObject(it)) } }
        assertEquals(listOf(true, false, false), pages.map { it.hasMore })
        assertEquals(listOf(1, 1, 0), pages.map { it.records.size })
        assertFalse(pages[0].records.single().isDeleted)
        assertTrue(pages[1].records.single().isDeleted)
        assertTrue(pages.none { it.isSnapshot })

        val v03 = vector("V03")
        val v03Body = v03.getJSONObject("expect").getJSONObject("body")
        val v03Page = JSONObject(v03.getJSONObject("complemento").toString())
            .put("isSnapshot", v03Body.getBoolean("isSnapshot"))
            .put("changes", v03Body.getJSONObject("changes"))
        val retired = pull(v03Page).records
        assertEquals(listOf(SyncEntity.WORKSPACE, SyncEntity.BOARD_ACCESS), retired.map { it.entity })
        assertEquals("20000000-0000-4000-8000-000000000001", retired.last().id)
        assertTrue(retired.all { it.isDeleted })
        assertEquals("VIEWER", retired.last().fields["effectiveRole"])

        val v04 = pull(vector("V04").getJSONObject("alternativa"))
        assertTrue(v04.isSnapshot)
        assertEquals(listOf(SyncEntity.BOARD_ACCESS, SyncEntity.BOARD, SyncEntity.COLUMN, SyncEntity.TASK), v04.records.map { it.entity })
    }

    @Test
    fun parseaPaginaCompleta() {
        val page = pull(vector("V01").getJSONObject("expect").getJSONObject("body"))

        assertEquals("11111111-1111-4111-8111-111111111111", page.userId)
        assertEquals("1.0", page.contractVersion)
        assertEquals(SyncTimestamps.parse("2026-11-26T18:30:00.000Z"), page.serverTime)
        assertFalse(page.hasMore)
        val list = page.records.first { it.entity == SyncEntity.LIST }
        assertEquals("Tareas", list.fields["name"])
        assertEquals(SyncTimestamps.parse("2026-11-20T10:00:00.000Z"), list.updatedAt)
        assertNull(list.deletedAt)
    }

    @Test
    fun clavesAusentes_sonVacias() {
        val sinClaves = pull(minimalPage(JSONObject().put("tasks", JSONArray())))
        val sinChanges = pull(minimalPage(changes = null))

        assertTrue(sinClaves.records.isEmpty())
        assertTrue(sinChanges.records.isEmpty())
        assertEquals("C1", sinChanges.nextCursor)
    }

    @Test
    fun entidadDesconocida_seIgnora() {
        val changes = JSONObject()
            .put("widgets", JSONArray().put(JSONObject().put("id", "w1").put("updatedAt", "2026-11-26T18:00:00.000Z")))
            .put("gadgets", JSONArray().put(JSONObject().put("id", "g1").put("updatedAt", "2026-11-26T18:00:00.000Z")))
        val body = minimalPage(changes).put("supportedEntities", JSONArray(listOf("list", "task", "gadget")))

        val page = pull(body)

        assertTrue(page.records.isEmpty())
        assertEquals(setOf(SyncEntity.LIST, SyncEntity.TASK), page.supportedEntities)
    }

    @Test
    fun fechasInvalidas_descartanElRegistro() {
        val good = JSONObject().put("id", "t-ok").put("listId", "l").put("updatedAt", "2026-11-26T18:00:00.000Z").put("deletedAt", JSONObject.NULL)
        val badUpdated = JSONObject().put("id", "t-1").put("updatedAt", "ayer")
        val badDeleted = JSONObject().put("id", "t-2").put("updatedAt", "2026-11-26T18:00:00.000Z").put("deletedAt", "nunca")
        val changes = JSONObject().put("tasks", JSONArray().put(badUpdated).put(good).put(badDeleted))

        val page = pull(minimalPage(changes))

        assertEquals(listOf("t-ok"), page.records.map { it.id })
    }

    @Test
    fun tareaConListId_yColumnIdNulo() {
        val record = pull(vector("V01").getJSONObject("expect").getJSONObject("body")).records.first { it.entity == SyncEntity.TASK }

        assertEquals("25ad9060-2206-569c-beab-fc24c3b923e5", record.fields["listId"])
        assertTrue(record.fields.containsKey("columnId"))
        assertNull(record.fields["columnId"])
    }

    @Test
    fun myDayPorTaskId() {
        val entry = JSONObject().put("id", "30000000-0000-4000-8000-000000000001").put("taskId", "30000000-0000-4000-8000-000000000001")
            .put("addedAt", "2026-11-26T08:00:00.000Z").put("updatedAt", "2026-11-26T08:00:00.000Z").put("deletedAt", JSONObject.NULL)

        val record = pull(minimalPage(JSONObject().put("myDay", JSONArray().put(entry)))).records.single()

        assertEquals(SyncEntity.MY_DAY, record.entity)
        assertEquals(record.fields["taskId"], record.id)
        assertEquals("2026-11-26T08:00:00.000Z", record.fields["addedAt"])
    }

    @Test
    fun invalidCursor_esInvalid() {
        assertEquals("INVALID_CURSOR", SyncApiContract.errorCode("""{"error":{"code":"INVALID_CURSOR","message":"El cursor ya no es válido"}}"""))
        assertTrue(SyncApiContract.parsePull("""{"contractVersion":"1.0"}""") is SyncApiContract.Parsed.Malformed)
        assertTrue(SyncApiContract.parsePull(minimalPage().put("contractVersion", "2.0").toString()) is SyncApiContract.Parsed.UpdateRequired)
    }

    @Test
    fun workspacesYBoardAccess_seLeenConSuIdentificador() {
        val changes = JSONObject()
            .put("workspaces", JSONArray().put(JSONObject().put("id", "ws").put("name", "Equipo").put("isPersonal", false).put("myRole", "EDITOR").put("updatedAt", "2026-11-26T18:00:00.000Z")))
            .put("boardAccess", JSONArray().put(JSONObject().put("boardId", "b1").put("workspaceId", "ws").put("effectiveRole", "VIEWER").put("updatedAt", "2026-11-26T18:00:00.000Z").put("deletedAt", JSONObject.NULL)))

        val page = pull(minimalPage(changes))

        assertEquals(listOf(SyncEntity.WORKSPACE, SyncEntity.BOARD_ACCESS), page.records.map { it.entity })
        assertEquals("b1", page.records.last().id)
        assertEquals("VIEWER", page.records.last().fields["effectiveRole"])
        assertEquals("EDITOR", page.records.first().fields["myRole"])
    }

    @Test
    fun v17_altaDeTableroConColumnas_lecturaDeLaRespuesta() {
        val v = vector("V17")
        val batch = mutationsOf(v.getJSONObject("request"))

        val parsed = (SyncApiContract.parsePush(v.getJSONObject("response").toString(), batch) as SyncApiContract.Parsed.Ok).value

        val board = (parsed[0] as PushOutcome.Applied).record!!
        assertEquals(SyncEntity.BOARD, board.entity)
        assertEquals(2, (board.fields["columns"] as List<*>).size)
        assertEquals("a0000000-0000-4000-8000-000000000001", board.fields["workspaceId"])
        assertEquals(SyncEntity.COLUMN, (parsed[1] as PushOutcome.Applied).record!!.entity)
    }

    @Test
    fun v18v19_miDiaYRecordatorio_lecturaDeLaRespuesta() {
        val v18 = vector("V18")
        val mark = ((SyncApiContract.parsePush(v18.getJSONObject("response").toString(), mutationsOf(v18.getJSONObject("request"))) as SyncApiContract.Parsed.Ok)
            .value.single() as PushOutcome.Applied).record!!
        assertEquals(SyncEntity.MY_DAY, mark.entity)
        assertEquals("30000000-0000-4000-8000-000000000001", mark.id)
        assertEquals("2026-11-26T08:00:00.000Z", mark.fields["addedAt"])

        val v19 = vector("V19")
        val reminder = ((SyncApiContract.parsePush(v19.getJSONObject("response").toString(), mutationsOf(v19.getJSONObject("request"))) as SyncApiContract.Parsed.Ok)
            .value.single() as PushOutcome.Applied).record!!
        assertEquals(SyncEntity.REMINDER, reminder.entity)
        assertEquals("MOBILE", reminder.fields["origin"])
        assertEquals("PENDING", reminder.fields["status"])
    }
}
