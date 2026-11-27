package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.sync.BackoffRules
import com.simposio.taskeer.domain.sync.PauseReason
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncErrorKind
import com.simposio.taskeer.domain.sync.SyncLoss
import com.simposio.taskeer.domain.sync.SyncNotice
import com.simposio.taskeer.domain.sync.SyncNoticeRules
import com.simposio.taskeer.domain.sync.SyncOrderRules
import com.simposio.taskeer.domain.sync.SyncScope
import com.simposio.taskeer.domain.sync.SyncSnapshot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

fun interface RetryScheduler {
    fun schedule(delay: Duration)
}

// Ciclo de sincronización: identificar cuenta → recolectar lo pendiente → enviar. Un Mutex garantiza un ciclo a la vez.
class SyncEngine(
    private val store: SyncLocalStore,
    private val source: TransportSource,
    private val gate: SyncSessionGate,
    private val wiper: AccountWiper,
    private val session: StateFlow<SessionState>,
    private val scopeHolder: SyncScopeHolder,
    private val triggers: SyncTriggers,
    private val retry: RetryScheduler,
    private val clock: () -> Instant,
    private val random: Random = Random.Default,
    private val appScope: Set<SyncEntity> = SyncScope.APP_LISTS_AND_TASKS,
    private val minAutomaticSpacing: Duration = MIN_AUTOMATIC_SPACING,
) {
    private val cycleLock = Mutex()
    private val paused = MutableStateFlow<PauseReason?>(null)
    private val syncing = MutableStateFlow(false)
    private val lastSynced = MutableStateFlow<Instant?>(null)
    private val cycleError = MutableStateFlow<SyncErrorKind?>(null)
    private val accountPrompt = MutableStateFlow(false)
    private val mutableNotices = MutableSharedFlow<SyncNotice>(extraBufferCapacity = 8)
    private var lastCycleStart: Instant? = null
    private var pullFailures = 0

    val notices: SharedFlow<SyncNotice> = mutableNotices.asSharedFlow()

    // Verdadero mientras se espera la decisión del usuario sobre una cuenta distinta a la vinculada.
    val accountSwitchPrompt: StateFlow<Boolean> = accountPrompt.asStateFlow()

    val snapshots: Flow<SyncSnapshot> = combine(
        combine(session, paused, syncing, lastSynced, cycleError) { sessionState, pausedReason, isSyncing, last, error ->
            SyncSnapshot(
                signedIn = sessionState is SessionState.SignedIn,
                paused = pausedReason,
                syncing = isSyncing,
                lastSyncedAt = last,
                cycleError = error,
            )
        },
        store.observePendingCount(),
        store.observeStuckCount(),
    ) { base, pending, stuck -> base.copy(pendingCount = pending, stuckCount = stuck) }.distinctUntilChanged()

    suspend fun run() = coroutineScope {
        lastSynced.value = store.meta(SyncMetaKeys.LAST_SYNCED_AT)?.toLongOrNull()?.let(Instant::ofEpochMilli)
        // La cola refleja cada escritura aunque no haya sesión ni red: es lo que sobrevive al cierre del proceso (R1, R17).
        launch {
            store.observePendingCount().conflate().collect { cycleLock.withLock { store.collect(clock()) } }
        }
        session.map { it.activity() }.distinctUntilChanged().collectLatest { activity ->
            if (activity == Activity.NONE) resetSessionState()
            if (activity != Activity.ACTIVE) return@collectLatest
            triggers.requests().collect { request ->
                if (!request.manual) waitForSpacing()
                runCycle(request.manual)
            }
        }
    }

    suspend fun runCycle(manual: Boolean) {
        cycleLock.withLock {
            lastCycleStart = clock()
            try {
                cycle(manual)
            } finally {
                syncing.value = false
            }
        }
    }

    // Vacía y vuelve a empezar con la cuenta nueva; solo tiene sentido pausada por ACCOUNT_MISMATCH.
    suspend fun confirmAccountWipe() {
        cycleLock.withLock {
            if (paused.value != PauseReason.ACCOUNT_MISMATCH) return
            wiper.wipe()
            paused.value = null
            accountPrompt.value = false
        }
        triggers.manual()
    }

    // Cancelar no borra nada: la sincronización sigue pausada hasta cerrar sesión o confirmar el vaciado.
    fun cancelAccountSwitch() {
        accountPrompt.value = false
    }

    private fun resetSessionState() {
        paused.value = null
        accountPrompt.value = false
        cycleError.value = null
        source.invalidate()
    }

    private suspend fun waitForSpacing() {
        val previous = lastCycleStart ?: return
        val wait = Duration.between(clock(), previous + minAutomaticSpacing)
        if (!wait.isNegative && !wait.isZero) delay(wait.toMillis())
    }

    private suspend fun cycle(manual: Boolean) {
        val ready = when (val acquisition = source.acquire()) {
            is Acquisition.Ready -> acquisition
            is Acquisition.Paused -> {
                paused.value = acquisition.reason
                return
            }
            is Acquisition.Retry -> {
                retry.schedule(acquisition.after ?: BackoffRules.INITIAL)
                return
            }
            Acquisition.Unauthorized -> return
        }
        if (paused.value == PauseReason.SERVER_NOT_READY || paused.value == PauseReason.UPDATE_REQUIRED) paused.value = null
        when (val decision = gate.evaluate(ready.identity)) {
            is GateDecision.Mismatch -> {
                paused.value = PauseReason.ACCOUNT_MISMATCH
                accountPrompt.value = true
                return
            }
            GateDecision.NeedsBinding -> gate.bind(ready.identity.userId)
            GateDecision.Bound -> Unit
        }
        val effective = SyncScope.effective(appScope, ready.identity.supportedEntities)
        scopeHolder.update(effective)
        markIfScopeGrew(effective)
        store.collect(clock())
        val pushReport = push(ready, manual)
        // Sin envío completo no se trae nada: la red o el servidor fallaron y la lectura toparía con lo mismo.
        val pullReport = if (pushReport.stopped) null else pull(ready)
        finish(pushReport, pullReport)
    }

    // Lo que no estaba en alcance conserva pending; al entrar en alcance necesita una marca nueva, como un primer sync parcial.
    private suspend fun markIfScopeGrew(effective: Set<SyncEntity>) {
        val marked = store.meta(SyncMetaKeys.MARKED_SCOPE)?.split(',')?.mapNotNull(SyncEntity::fromWire)?.toSet().orEmpty()
        if (marked.containsAll(effective)) return
        store.markAllUnsyncedPending()
        store.setMeta(SyncMetaKeys.MARKED_SCOPE, (marked + effective).joinToString(",") { it.wireName })
    }

    private class PushReport(val losses: List<SyncLoss>, val stopped: Boolean)

    private class PullReport(val losses: List<SyncLoss>, val stopped: Boolean)

    private enum class Step { CONTINUE, STOP }

    private suspend fun push(ready: Acquisition.Ready, manual: Boolean): PushReport {
        val me = ready.identity.userId
        val losses = mutableListOf<SyncLoss>()
        var allowed: Set<Pair<SyncEntity, String>>? = null
        for (pass in 0..1) {
            val ops = store.eligible(clock(), ignoreBackoff = manual).filter { allowed == null || (it.entity to it.entityId) in allowed!! }
            if (ops.isEmpty()) break
            val prepared = ops.mapNotNull { store.payloadFor(it, me) }.associateBy { it.op.mutationId }
            if (prepared.isEmpty()) break
            syncing.value = true
            val deferred = mutableSetOf<Pair<SyncEntity, String>>()
            val batches = SyncOrderRules.order(prepared.values.map { it.op }).map { batch -> batch.map { prepared.getValue(it.mutationId) } }
            for (batch in batches) {
                if (send(ready.transport, batch, me, retryDependency = pass == 0, deferred, losses) == Step.STOP) {
                    return PushReport(losses, stopped = true)
                }
            }
            if (deferred.isEmpty()) break
            allowed = deferred
        }
        return PushReport(losses, stopped = false)
    }

    private suspend fun send(
        transport: SyncTransport,
        batch: List<PreparedOp>,
        me: String,
        retryDependency: Boolean,
        deferred: MutableSet<Pair<SyncEntity, String>>,
        losses: MutableList<SyncLoss>,
    ): Step {
        when (val result = transport.push(batch.map { it.mutation })) {
            is TransportResult.Ok -> {
                val outcomes = result.value.associateBy { it.clientMutationId }
                var missing = false
                for (prepared in batch) {
                    val outcome = outcomes[prepared.op.mutationId]
                    if (outcome == null) {
                        missing = true
                        scheduleBackoff(prepared, null)
                        continue
                    }
                    val effect = store.applyOutcome(prepared, outcome, me, clock(), retryDependency)
                    if (effect == OutcomeEffect.RETRY_AFTER_PARENT) deferred += prepared.op.entity to prepared.op.entityId else effect.loss?.let(losses::add)
                }
                if (missing) cycleError.value = SyncErrorKind.UNEXPECTED_RESPONSE else cycleError.value = null
                return if (missing) Step.STOP else Step.CONTINUE
            }
            is TransportResult.Retry -> {
                batch.forEach { scheduleBackoff(it, result.after) }
                return Step.STOP
            }
            // Un cuerpo que el servidor no admite: se parte el lote para aislar la entrada culpable (R9).
            is TransportResult.Invalid -> {
                if (batch.size == 1) {
                    store.recordPermanentFailure(batch.single().op, result.code)
                    return Step.CONTINUE
                }
                for (half in batch.chunked((batch.size + 1) / 2)) {
                    if (send(transport, half, me, retryDependency, deferred, losses) == Step.STOP) return Step.STOP
                }
                return Step.CONTINUE
            }
            TransportResult.Unauthorized -> return Step.STOP
            TransportResult.UpdateRequired -> {
                paused.value = PauseReason.UPDATE_REQUIRED
                return Step.STOP
            }
            TransportResult.NotAvailable -> {
                paused.value = PauseReason.SERVER_NOT_READY
                source.invalidate()
                return Step.STOP
            }
        }
    }

    // Páginas hasta `hasMore = false`, cada una en su transacción. Un snapshot (sin cursor, o forzado por el servidor) solo
    // guarda el cursor y reconcilia ausencias al completarse; si se corta, la siguiente vez empieza desde cero.
    private suspend fun pull(ready: Acquisition.Ready): PullReport {
        val me = ready.identity.userId
        val losses = mutableListOf<SyncLoss>()
        var cursor = store.meta(SyncMetaKeys.CURSOR)
        var seen: MutableSet<Pair<SyncEntity, String>>? = null
        var cursorReset = false
        while (true) {
            when (val result = ready.transport.pull(cursor, PULL_PAGE_LIMIT)) {
                is TransportResult.Ok -> {
                    val page = result.value
                    if (page.userId != me) {
                        paused.value = PauseReason.ACCOUNT_MISMATCH
                        accountPrompt.value = true
                        return PullReport(losses, stopped = true)
                    }
                    pullFailures = 0
                    if (page.isSnapshot && seen == null) seen = mutableSetOf()
                    seen?.addAll(page.records.map { it.entity to it.id })
                    val commit = when {
                        seen == null -> PageCommit.SaveCursor
                        page.hasMore -> PageCommit.KeepCursor
                        else -> PageCommit.FinishSnapshot(seen.toSet())
                    }
                    losses += store.applyPage(page, me, clock(), commit).losses
                    if (!page.hasMore) return PullReport(losses, stopped = false)
                    cursor = page.nextCursor
                }
                is TransportResult.Invalid ->
                    if (result.code == SyncCodes.INVALID_CURSOR && !cursorReset) {
                        cursorReset = true
                        store.setMeta(SyncMetaKeys.CURSOR, null)
                        cursor = null
                        seen = null
                    } else {
                        cycleError.value = SyncErrorKind.UNEXPECTED_RESPONSE
                        return PullReport(losses, stopped = true)
                    }
                is TransportResult.Retry -> {
                    retry.schedule(BackoffRules.delayFor(pullFailures++, result.after, BackoffRules.jitterFrom(random)))
                    return PullReport(losses, stopped = true)
                }
                TransportResult.Unauthorized -> return PullReport(losses, stopped = true)
                TransportResult.UpdateRequired -> {
                    paused.value = PauseReason.UPDATE_REQUIRED
                    return PullReport(losses, stopped = true)
                }
                TransportResult.NotAvailable -> {
                    paused.value = PauseReason.SERVER_NOT_READY
                    source.invalidate()
                    return PullReport(losses, stopped = true)
                }
            }
        }
    }

    private suspend fun scheduleBackoff(prepared: PreparedOp, retryAfter: Duration?) {
        val delay = BackoffRules.delayFor(prepared.op.attempts, retryAfter, BackoffRules.jitterFrom(random))
        store.recordTransientFailure(prepared.op, clock() + delay)
    }

    private suspend fun finish(push: PushReport, pull: PullReport?) {
        SyncNoticeRules.summarize(push.losses + pull?.losses.orEmpty())?.let { mutableNotices.tryEmit(it) }
        val now = clock()
        store.nextWake(now)?.let { retry.schedule(maxOf(Duration.between(now, it), Duration.ZERO)) }
        if (pull != null && !pull.stopped && paused.value == null) {
            lastSynced.value = now
            store.setMeta(SyncMetaKeys.LAST_SYNCED_AT, now.toEpochMilli().toString())
        }
    }

    private enum class Activity { NONE, OFFLINE, ACTIVE }

    private fun SessionState.activity(): Activity = when {
        this !is SessionState.SignedIn -> Activity.NONE
        offline -> Activity.OFFLINE
        else -> Activity.ACTIVE
    }

    private companion object {
        val MIN_AUTOMATIC_SPACING: Duration = Duration.ofSeconds(10)
        const val PULL_PAGE_LIMIT = 200
    }
}
