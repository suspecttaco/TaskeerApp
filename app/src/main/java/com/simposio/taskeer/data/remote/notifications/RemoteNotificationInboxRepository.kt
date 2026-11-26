package com.simposio.taskeer.data.remote.notifications

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.repository.NotificationInboxRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.notification.InboxOutcome
import com.simposio.taskeer.domain.notification.InboxRules
import com.simposio.taskeer.domain.notification.InboxState
import com.simposio.taskeer.domain.notification.PendingOps
import com.simposio.taskeer.domain.notification.ServerNotification
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemoteNotificationInboxRepository(
    private val client: ApiClient,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
    private val clock: () -> Instant = Instant::now,
) : NotificationInboxRepository {

    private enum class OpKind { READ, DELETE, ALL_READ, DELETE_READ }

    // Operación local aplicada en memoria; `active` hasta que el servidor responde.
    private class Op(var kind: OpKind, val id: String?, var active: Boolean = true)

    private class Begun(val epoch: Int, val changed: List<Pair<Int, ServerNotification>>, val op: Op)

    // Todo el estado mutable se toca bajo `lock`, sin suspender dentro.
    private val lock = Any()
    private val mutableState = MutableStateFlow(InboxState())
    private val ops = mutableListOf<Op>()
    private val inFlightIds = mutableSetOf<String>()
    private var epoch = 0
    private var lastAttempt: Instant? = null
    private var refreshJob: Deferred<InboxOutcome>? = null
    private var refreshRunning = false
    private var knownEmail: String? = null

    // Eventos en vivo recibidos mientras una petición de lista está en vuelo: la respuesta puede haberse emitido antes que ellos.
    private val liveSeen = mutableListOf<ServerNotification>()
    private var inFlight = 0

    // Ids ya contados mientras la lista no está cargada (solo hay contador): evita sumar dos veces la misma entrega.
    private val knownIds = mutableSetOf<String>()

    override val state: StateFlow<InboxState> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionState.collect { onSession(it) }
        }
    }

    override suspend fun refreshUnread(): InboxOutcome = fetchUnread(force = false)

    override suspend fun catchUp(): InboxOutcome = fetchUnread(force = true)

    override fun applyLive(notification: ServerNotification) {
        synchronized(lock) {
            if (!signedIn()) return
            val current = mutableState.value
            if (current.items.any { it.id == notification.id } || notification.id in knownIds) return
            if (notification.id in pendingOps().deletedIds) return
            if (inFlight > 0) liveSeen += notification
            if (current.listLoaded) {
                val items = InboxRules.mergeLive(current.items, listOf(notification))
                mutableState.value = current.copy(items = items, unreadCount = InboxRules.unreadCount(items))
            } else {
                knownIds += notification.id
                if (notification.isUnread) mutableState.value = current.copy(unreadCount = current.unreadCount + 1)
            }
        }
    }

    private suspend fun fetchUnread(force: Boolean): InboxOutcome {
        if (!signedIn()) return unauthenticated()
        val startEpoch = synchronized(lock) {
            val now = clock()
            if (!force && !InboxRules.shouldRefresh(lastAttempt, now)) return InboxOutcome.Done
            lastAttempt = now
            inFlight++
            epoch
        }
        try {
            val request = ApiRequest(HttpMethod.GET, NotificationsApiContract.LIST_PATH, query = listOf("unread" to "true"), auth = AuthMode.BEARER)
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> InboxOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val unread = NotificationsApiContract.parseList(result.response.body) ?: return unreadable()
                    synchronized(lock) {
                        if (epoch != startEpoch) return stale()
                        val fresh = InboxRules.reapplyPending(
                            InboxRules.mergeLive(InboxRules.sorted(unread), liveSeen),
                            pendingOps(),
                            clock(),
                        )
                        mutableState.value = mutableState.value.let { current ->
                            if (current.listLoaded) {
                                val merged = InboxRules.mergeUnread(current.items, fresh)
                                current.copy(items = merged, unreadCount = InboxRules.unreadCount(merged))
                            } else {
                                knownIds += fresh.map { it.id }
                                current.copy(unreadCount = fresh.size)
                            }
                        }
                    }
                    InboxOutcome.Done
                }
            }
        } finally {
            requestDone()
        }
    }

    private fun requestDone() {
        synchronized(lock) {
            inFlight--
            if (inFlight == 0) liveSeen.clear()
        }
    }

    override suspend fun refresh(): InboxOutcome {
        if (!signedIn()) return unauthenticated()
        val job = synchronized(lock) {
            refreshJob?.takeIf { !it.isCompleted } ?: scope.async(start = CoroutineStart.LAZY) { loadList() }
                .also { refreshJob = it }
        }
        return job.await()
    }

    private suspend fun loadList(): InboxOutcome {
        val startEpoch = synchronized(lock) {
            ops.removeAll { !it.active }
            refreshRunning = true
            inFlight++
            epoch
        }
        try {
            val request = ApiRequest(HttpMethod.GET, NotificationsApiContract.LIST_PATH, auth = AuthMode.BEARER)
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> InboxOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val list = NotificationsApiContract.parseList(result.response.body) ?: return unreadable()
                    synchronized(lock) {
                        if (epoch != startEpoch) return stale()
                        val fresh = InboxRules.reapplyPending(
                            InboxRules.mergeLive(InboxRules.sorted(list), liveSeen),
                            pendingOps(),
                            clock(),
                        )
                        mutableState.value = InboxState(fresh, InboxRules.unreadCount(fresh), listLoaded = true)
                    }
                    InboxOutcome.Done
                }
            }
        } finally {
            synchronized(lock) {
                refreshRunning = false
                ops.removeAll { !it.active }
            }
            requestDone()
        }
    }

    override suspend fun markRead(id: String): InboxOutcome {
        precheck(id)?.let { return it }
        val begun = begin(id, OpKind.READ) { items ->
            val target = items.firstOrNull { it.id == id }
            if (target == null || !target.isUnread) null else items.map { if (it.id == id) it.copy(readAt = clock()) else it }
        } ?: return InboxOutcome.Done
        val result = client.execute(ApiRequest(HttpMethod.PATCH, NotificationsApiContract.readPath(id)))
        return finish(begun, result, id, alreadyGone = true)
    }

    override suspend fun markAllRead(): InboxOutcome {
        if (!signedIn()) return unauthenticated()
        val now = clock()
        val begun = begin(null, OpKind.ALL_READ) { items ->
            if (items.none { it.isUnread }) null else items.map { if (it.isUnread) it.copy(readAt = now) else it }
        } ?: return InboxOutcome.Done
        val result = client.execute(ApiRequest(HttpMethod.PATCH, NotificationsApiContract.READ_ALL_PATH))
        return finish(begun, result, null, alreadyGone = false)
    }

    override suspend fun delete(id: String): InboxOutcome {
        precheck(id)?.let { return it }
        val begun = begin(id, OpKind.DELETE) { items ->
            if (items.none { it.id == id }) null else items.filterNot { it.id == id }
        } ?: return InboxOutcome.Done
        val result = client.execute(ApiRequest(HttpMethod.DELETE, NotificationsApiContract.deletePath(id)))
        return finish(begun, result, id, alreadyGone = true)
    }

    override suspend fun deleteRead(): InboxOutcome {
        if (!signedIn()) return unauthenticated()
        val begun = begin(null, OpKind.DELETE_READ) { items ->
            if (items.all { it.isUnread }) null else items.filter { it.isUnread }
        } ?: return InboxOutcome.Done
        val result = client.execute(ApiRequest(HttpMethod.DELETE, NotificationsApiContract.DELETE_READ_PATH))
        return finish(begun, result, null, alreadyGone = false)
    }

    private fun precheck(id: String): InboxOutcome? = when {
        !signedIn() -> unauthenticated()
        !NotificationsApiContract.isValidId(id) ->
            InboxOutcome.Failed(DomainError.Validation("INVALID_NOTIFICATION_ID", "Identificador de notificación no válido"))
        else -> null
    }

    // Aplica el cambio optimista y guarda lo que cambió para poder revertirlo; null = nada que hacer.
    private fun begin(id: String?, kind: OpKind, change: (List<ServerNotification>) -> List<ServerNotification>?): Begun? =
        synchronized(lock) {
            val current = mutableState.value
            if (id != null && id in inFlightIds) return null
            if (id == null && ops.any { it.active && it.id == null }) return null
            val updated = change(current.items) ?: return null
            val changed = current.items.withIndex()
                .filter { (_, item) -> updated.firstOrNull { it.id == item.id } != item }
                .map { it.index to it.value }
            val op = Op(kind, id)
            ops += op
            if (id != null) inFlightIds += id
            mutableState.value = current.copy(items = updated, unreadCount = InboxRules.unreadCount(updated))
            Begun(epoch, changed, op)
        }

    private fun finish(begun: Begun, result: ApiResult, id: String?, alreadyGone: Boolean): InboxOutcome =
        synchronized(lock) {
            if (id != null) inFlightIds -= id
            begun.op.active = false
            val sameSession = epoch == begun.epoch
            val gone = alreadyGone && result is ApiResult.Failure && result.error.isNotificationNotFound()
            when {
                result is ApiResult.Success || gone -> {
                    if (gone && begun.op.kind == OpKind.READ) {
                        begun.op.kind = OpKind.DELETE
                        if (sameSession) removeItem(id)
                    }
                    if (!refreshRunning) ops.remove(begun.op)
                    if (sameSession) InboxOutcome.Done else stale()
                }
                else -> {
                    ops.remove(begun.op)
                    if (sameSession) restore(begun.changed)
                    val failure = result as ApiResult.Failure
                    InboxOutcome.Failed(failure.error, failure.retryAfterSeconds)
                }
            }
        }

    private fun removeItem(id: String?) {
        val current = mutableState.value
        val items = current.items.filterNot { it.id == id }
        mutableState.value = current.copy(items = items, unreadCount = InboxRules.unreadCount(items))
    }

    private fun restore(changed: List<Pair<Int, ServerNotification>>) {
        val current = mutableState.value
        val restoredIds = changed.mapTo(HashSet()) { it.second.id }
        val items = InboxRules.sorted(current.items.filterNot { it.id in restoredIds } + changed.map { it.second })
        mutableState.value = current.copy(items = items, unreadCount = InboxRules.unreadCount(items))
    }

    private fun pendingOps(): PendingOps = PendingOps(
        readIds = ops.filter { it.kind == OpKind.READ }.mapNotNullTo(HashSet()) { it.id },
        deletedIds = ops.filter { it.kind == OpKind.DELETE }.mapNotNullTo(HashSet()) { it.id },
        allRead = ops.any { it.kind == OpKind.ALL_READ },
        readDeleted = ops.any { it.kind == OpKind.DELETE_READ },
    )

    private fun onSession(session: SessionState) {
        synchronized(lock) {
            when (session) {
                is SessionState.SignedOut -> reset(email = null)
                is SessionState.SignedIn -> {
                    val email = session.account?.email
                    if (knownEmail != null && email != null && email != knownEmail) reset(email) else knownEmail = email ?: knownEmail
                }
                SessionState.Resuming -> Unit
            }
        }
    }

    private fun reset(email: String?) {
        epoch++
        mutableState.value = InboxState()
        lastAttempt = null
        ops.clear()
        inFlightIds.clear()
        liveSeen.clear()
        knownIds.clear()
        knownEmail = email
    }

    private fun signedIn(): Boolean = sessionState.value is SessionState.SignedIn

    private fun DomainError.isNotificationNotFound(): Boolean =
        this is DomainError.NotFound && code == NOTIFICATION_NOT_FOUND

    private fun unauthenticated() =
        InboxOutcome.Failed(DomainError.Unauthenticated("UNAUTHENTICATED", "Inicia sesión para ver tus notificaciones"))

    private fun stale() =
        InboxOutcome.Failed(DomainError.Unauthenticated("SESSION_CHANGED", "La sesión cambió durante la petición"))

    private fun unreadable() =
        InboxOutcome.Failed(DomainError.Unknown("UNREADABLE_RESPONSE", "Respuesta del servidor no válida"))

    private companion object {
        const val NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND"
    }
}
