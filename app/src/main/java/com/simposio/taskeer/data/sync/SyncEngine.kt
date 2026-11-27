package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.sync.BackoffRules
import com.simposio.taskeer.domain.sync.CycleOutcome
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
    private val appScope: Set<SyncEntity> = SyncScope.APP_FULL,
    private val minAutomaticSpacing: Duration = MIN_AUTOMATIC_SPACING,
    private val onRemindersChanged: suspend () -> Unit = {},
    private val debugLog: SyncDebugLog = SyncDebugLog.None,
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

    private val mutableManualOutcomes = MutableSharedFlow<CycleOutcome>(extraBufferCapacity = 4)

    val manualOutcomes: SharedFlow<CycleOutcome> = mutableManualOutcomes.asSharedFlow()

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
                val outcome = loggedCycle(manual)
                if (manual) mutableManualOutcomes.tryEmit(outcome)
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

    // Mismo ciclo que el primer plano, con el resultado para quien no observa el estado (el trabajo en segundo plano).
    suspend fun runScheduledCycle(): CycleOutcome = cycleLock.withLock {
        lastCycleStart = clock()
        try {
            loggedCycle(manual = false)
        } finally {
            syncing.value = false
        }
    }

    private suspend fun loggedCycle(manual: Boolean): CycleOutcome {
        val start = clock()
        debugLog.line("cycle start manual=$manual")
        val outcome = cycle(manual)
        val millis = Duration.between(start, clock()).toMillis()
        debugLog.line("cycle end outcome=$outcome paused=${paused.value} error=${cycleError.value} ms=$millis")
        return outcome
    }

    private suspend fun cycle(manual: Boolean): CycleOutcome {
        val ready = when (val acquisition = source.acquire()) {
            is Acquisition.Ready -> acquisition
            is Acquisition.Paused -> {
                debugLog.line("acquire paused reason=${acquisition.reason}")
                paused.value = acquisition.reason
                return CycleOutcome.PAUSED
            }
            is Acquisition.Retry -> {
                debugLog.line("acquire retry after=${acquisition.after}")
                retry.schedule(acquisition.after ?: BackoffRules.INITIAL)
                return CycleOutcome.DEFERRED
            }
            Acquisition.Unauthorized -> {
                debugLog.line("acquire unauthorized")
                return CycleOutcome.NO_SESSION
            }
        }
        if (paused.value == PauseReason.SERVER_NOT_READY || paused.value == PauseReason.UPDATE_REQUIRED) paused.value = null
        when (val decision = gate.evaluate(ready.identity)) {
            is GateDecision.Mismatch -> {
                debugLog.line("gate account mismatch")
                paused.value = PauseReason.ACCOUNT_MISMATCH
                accountPrompt.value = true
                return CycleOutcome.PAUSED
            }
            GateDecision.NeedsBinding -> gate.bind(ready.identity.userId)
            GateDecision.Bound -> Unit
        }
        val effective = SyncScope.effective(appScope, ready.identity.supportedEntities)
        scopeHolder.update(effective)
        markIfScopeGrew(effective)
        store.collect(clock())
        val knewPersonalWorkspace = store.personalWorkspaceId() != null
        var pushReport = push(ready, manual)
        // Sin envío completo no se trae nada: la red o el servidor fallaron y la lectura toparía con lo mismo.
        val pullReport = if (pushReport.stopped) null else pull(ready)
        // Los tableros nuevos esperan al workspace personal, que llega con la primera traída: se envían ya, no en el ciclo siguiente.
        if (pullReport != null && !pullReport.stopped && !knewPersonalWorkspace && store.personalWorkspaceId() != null) {
            store.collect(clock())
            val second = push(ready, manual)
            pushReport = PushReport(pushReport.losses + second.losses, second.stopped)
        }
        return finish(pushReport, pullReport)
    }

    // Lo que no estaba en alcance conserva pending; al entrar en alcance necesita una marca nueva, como un primer sync parcial.
    // El cursor también se descarta: lo emitido mientras la entidad estaba fuera de alcance se ignoró y no volvería en el flujo incremental.
    private suspend fun markIfScopeGrew(effective: Set<SyncEntity>) {
        val marked = store.meta(SyncMetaKeys.MARKED_SCOPE)?.split(',')?.mapNotNull(SyncEntity::fromWire)?.toSet().orEmpty()
        if (marked.containsAll(effective)) return
        store.markAllUnsyncedPending()
        store.setMeta(SyncMetaKeys.CURSOR, null)
        store.setMeta(SyncMetaKeys.MARKED_SCOPE, (marked + effective).joinToString(",") { it.wireName })
    }

    private class PushReport(val losses: List<SyncLoss>, val stopped: Boolean)

    private class PullReport(val losses: List<SyncLoss>, val stopped: Boolean)

    private enum class Step { CONTINUE, STOP }

    private suspend fun push(ready: Acquisition.Ready, manual: Boolean): PushReport {
        val me = ready.identity.userId
        val losses = mutableListOf<SyncLoss>()
        var allowed: Set<Pair<SyncEntity, String>>? = null
        val tally = PushTally()
        for (pass in 0..1) {
            val ops = store.eligible(clock(), ignoreBackoff = manual).filter { allowed == null || (it.entity to it.entityId) in allowed!! }
            if (ops.isEmpty()) break
            val prepared = ops.mapNotNull { store.payloadFor(it, me) }.associateBy { it.op.mutationId }
            if (prepared.isEmpty()) break
            syncing.value = true
            val deferred = mutableSetOf<Pair<SyncEntity, String>>()
            val batches = SyncOrderRules.order(prepared.values.map { it.op }).map { batch -> batch.map { prepared.getValue(it.mutationId) } }
            for (batch in batches) {
                if (send(ready.transport, batch, me, retryDependency = pass == 0, deferred, losses, tally) == Step.STOP) {
                    debugLog.line("push summary $tally stopped=true")
                    return PushReport(losses, stopped = true)
                }
            }
            if (deferred.isEmpty()) break
            allowed = deferred
        }
        debugLog.line("push summary $tally stopped=false")
        return PushReport(losses, stopped = false)
    }

    private class PushTally {
        var applied = 0
        var rejected = 0
        var conflict = 0
        var missing = 0

        override fun toString() = "applied=$applied rejected=$rejected conflict=$conflict missing=$missing"
    }

    private fun logMutation(prepared: PreparedOp, outcome: PushOutcome?, effect: OutcomeEffect?) {
        val m = prepared.mutation
        val head = "push mutation entity=${m.entity.wireName} op=${m.op.name.lowercase()} id=${m.id} mid=${m.clientMutationId} base=${m.baseUpdatedAt}"
        if (outcome == null) {
            debugLog.line("$head verdict=missing data=${m.data}")
            return
        }
        val line = "$head verdict=${describe(outcome)} effect=$effect"
        if (outcome is PushOutcome.Applied) {
            debugLog.line(line)
        } else {
            debugLog.line("$line data=${m.data} server=${outcomeRecord(outcome)?.let(::describe)}")
        }
    }

    private suspend fun send(
        transport: SyncTransport,
        batch: List<PreparedOp>,
        me: String,
        retryDependency: Boolean,
        deferred: MutableSet<Pair<SyncEntity, String>>,
        losses: MutableList<SyncLoss>,
        tally: PushTally,
    ): Step {
        debugLog.line("push batch size=${batch.size} retryDependency=$retryDependency")
        val result = transport.push(batch.map { it.mutation })
        if (result !is TransportResult.Ok) debugLog.line("push batch result=${describe(result)}")
        when (result) {
            is TransportResult.Ok -> {
                val outcomes = result.value.associateBy { it.clientMutationId }
                var missing = false
                for (prepared in batch) {
                    val outcome = outcomes[prepared.op.mutationId]
                    if (outcome == null) {
                        missing = true
                        tally.missing++
                        logMutation(prepared, null, null)
                        scheduleBackoff(prepared, null)
                        continue
                    }
                    val effect = store.applyOutcome(prepared, outcome, me, clock(), retryDependency)
                    when (outcome) {
                        is PushOutcome.Applied -> tally.applied++
                        is PushOutcome.Conflict -> tally.conflict++
                        is PushOutcome.Rejected -> tally.rejected++
                    }
                    logMutation(prepared, outcome, effect)
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
                    if (send(transport, half, me, retryDependency, deferred, losses, tally) == Step.STOP) return Step.STOP
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
        var pages = 0
        while (true) {
            val result = ready.transport.pull(cursor, PULL_PAGE_LIMIT)
            if (result !is TransportResult.Ok) debugLog.line("pull result=${describe(result)} cursor=$cursor")
            when (result) {
                is TransportResult.Ok -> {
                    val page = result.value
                    pages++
                    val perEntity = page.records.groupingBy { it.entity.wireName }.eachCount()
                    debugLog.line(
                        "pull page=$pages cursor=$cursor next=${page.nextCursor} snapshot=${page.isSnapshot} " +
                            "hasMore=${page.hasMore} records=${page.records.size} byEntity=$perEntity",
                    )
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
                    val effect = store.applyPage(page, me, clock(), commit)
                    losses += effect.losses
                    if (effect.remindersChanged) onRemindersChanged()
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

    private suspend fun finish(push: PushReport, pull: PullReport?): CycleOutcome {
        SyncNoticeRules.summarize(push.losses + pull?.losses.orEmpty())?.let { mutableNotices.tryEmit(it) }
        val now = clock()
        val wake = store.nextWake(now)
        wake?.let { retry.schedule(maxOf(Duration.between(now, it), Duration.ZERO)) }
        val completed = pull != null && !pull.stopped && paused.value == null
        if (completed) {
            lastSynced.value = now
            store.setMeta(SyncMetaKeys.LAST_SYNCED_AT, now.toEpochMilli().toString())
        }
        return when {
            paused.value != null -> CycleOutcome.PAUSED
            cycleError.value == SyncErrorKind.UNEXPECTED_RESPONSE -> CycleOutcome.STOPPED_BY_ERROR
            wake != null || !completed -> CycleOutcome.DEFERRED
            else -> CycleOutcome.COMPLETED
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
