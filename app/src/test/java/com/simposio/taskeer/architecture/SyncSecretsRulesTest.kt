package com.simposio.taskeer.architecture

import com.simposio.taskeer.data.local.SyncBaselineEntity
import com.simposio.taskeer.data.local.SyncQueueEntity
import com.simposio.taskeer.data.remote.sync.SyncApiContract
import com.simposio.taskeer.data.sync.Identity
import com.simposio.taskeer.data.sync.PageCommit
import com.simposio.taskeer.data.sync.PreparedOp
import com.simposio.taskeer.data.sync.PullPage
import com.simposio.taskeer.data.sync.PushMutation
import com.simposio.taskeer.data.sync.PushOutcome
import com.simposio.taskeer.data.sync.ServerRecord
import com.simposio.taskeer.domain.sync.QueuedOp
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncLoss
import com.simposio.taskeer.domain.sync.SyncNotice
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// R29, R32 y R33 de punta a punta: qué entidades viajan, qué se puede ver en un log y qué guarda la cola.
class SyncSecretsRulesTest {

    private val secret = "Comprar leche secreta"
    private val stamp = Instant.parse("2026-11-26T18:30:00Z")
    private val syncFiles = listOf("data/sync", "data/remote/sync", "domain/sync").flatMap(MainSources::kotlinFiles)

    private fun record() = ServerRecord(SyncEntity.TASK, "t1", stamp, null, mapOf("title" to secret, "description" to secret))

    @Test
    fun toStringSinTitulos() {
        val mutation = PushMutation("m1", SyncEntity.TASK, SyncOp.UPSERT, "t1", null, stamp, mapOf("title" to secret))
        val queued = QueuedOp("m1", SyncEntity.TASK, "t1", SyncOp.UPSERT, stamp)
        val rendered = listOf(
            mutation,
            record(),
            PullPage("u1", "1.0", setOf(SyncEntity.TASK), stamp, false, listOf(record()), "cursor-secreto", false),
            PreparedOp(queued, mutation, 1L),
            PushOutcome.Applied("m1", record()),
            PushOutcome.Conflict("m1", record()),
            PushOutcome.Rejected("m1", "INVALID_MUTATION", record()),
            Identity("user-id-secreto", "1.0", setOf(SyncEntity.TASK)),
            queued,
            SyncNotice(mapOf(SyncLoss.REJECTED to 2)),
            PageCommit.FinishSnapshot(setOf(SyncEntity.TASK to "t1")),
        ).map { it.toString() }

        rendered.forEach {
            assertFalse(it, it.contains(secret))
            assertFalse(it, it.contains("cursor-secreto"))
            assertFalse(it, it.contains("user-id-secreto"))
        }
    }

    @Test
    fun sinLogsEnLaCapaDeSincronizacion() {
        val logging = Regex("""\bLog\.[a-z]\(|\bprintln\(|\bprint\(|Timber\.|printStackTrace""")

        val offenders = syncFiles.filter { logging.containsMatchIn(it.readText()) }.map { it.name }

        assertTrue("el log vive solo en di/ tras SyncDebugLog: $offenders", offenders.isEmpty())
    }

    @Test
    fun logDeSyncSoloEnDiYTrasLaCostura() {
        val direct = Regex("""\bLog\.[a-z]\(""")
        val offenders = MainSources.kotlinFiles("").filter { direct.containsMatchIn(it.readText()) }.map { it.name }.sorted()

        assertEquals(listOf("AndroidSyncDebugLog.kt", "DebugHttpLogInterceptor.kt"), offenders.filter { it.contains("Debug") })
        assertTrue(MainSources.kotlin("di/AndroidSyncDebugLog.kt").contains("if (BuildConfig.DEBUG)"))
    }

    @Test
    fun colaSinContenido() = runTest {
        val queueColumns = SyncQueueEntity::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet()
        val baselineColumns = SyncBaselineEntity::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet()
        assertEquals(
            setOf("mutationId", "entity", "entityId", "op", "enqueuedAt", "payloadVersion", "attempts", "failures", "nextAttemptAt", "lastErrorCode", "state"),
            queueColumns,
        )
        assertEquals(setOf("entity", "entityId", "serverUpdatedAt"), baselineColumns)

        val rig = SyncRig()
        rig.bound()
        rig.dao.put(syncTask("t1", listId = rig.listId, title = secret))
        rig.store.collect(rig.now)

        val queue = rig.dao.queueRows.value.values
        assertEquals(1, queue.size)
        assertFalse(queue.toString().contains(secret))
    }

    @Test
    fun contratoSinPreferencias() {
        val forbidden = Regex("theme|region|locale|preference|plan|attachment|notification|dismissed", RegexOption.IGNORE_CASE)

        val offenders = SyncEntity.entries.map { it.wireName }.filter { forbidden.containsMatchIn(it) }

        assertTrue(offenders.toString(), offenders.isEmpty())
        assertEquals(
            setOf("workspace", "boardAccess", "list", "board", "column", "task", "subtask", "myDay", "reminder"),
            SyncEntity.entries.map { it.wireName }.toSet(),
        )
        val body = """{"contractVersion":"1.0","me":{"userId":"u1"},"supportedEntities":["task","preferences"],
            "serverTime":"2026-11-26T18:30:00.000Z","isSnapshot":false,"nextCursor":"c","hasMore":false,
            "changes":{"preferences":[{"id":"p1","updatedAt":"2026-11-26T18:30:00.000Z"}],"attachments":[{"id":"a1","updatedAt":"2026-11-26T18:30:00.000Z"}],"tasks":[]}}"""
        val parsed = SyncApiContract.parsePull(body) as SyncApiContract.Parsed.Ok
        assertTrue(parsed.value.records.isEmpty())
        assertEquals(setOf(SyncEntity.TASK), parsed.value.supportedEntities)
    }

    @Test
    fun interceptorMuestraSyncSoloEnDebug() {
        val source = MainSources.kotlin("data/remote/transport/DebugHttpLogInterceptor.kt")

        assertTrue(source.contains("CONTENT_SEGMENTS = setOf(\"lists\", \"tasks\", \"subtasks\")"))
        assertTrue(source.contains("carriesTaskContent"))
        assertTrue(MainSources.kotlin("data/remote/ApiClientFactory.kt").contains("httpLogging = BuildConfig.DEBUG"))
    }
}
