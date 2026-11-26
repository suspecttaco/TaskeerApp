package com.simposio.taskeer.testing

import com.simposio.taskeer.data.remote.HttpRequest
import com.simposio.taskeer.data.remote.StreamResult
import com.simposio.taskeer.data.remote.StreamTransport
import com.simposio.taskeer.data.remote.TransportFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

class FakeStreamTransport : StreamTransport {

    sealed interface Step {
        // `tail` = null deja el flujo abierto hasta que se cancela; `holdMillis` simula el tiempo que dura abierto.
        class Lines(
            val lines: List<String>,
            val holdMillis: Long = 0,
            val tail: StreamResult? = null,
        ) : Step

        class Reject(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap()) : Step

        class Fail(val kind: TransportFailureKind) : Step
    }

    val requests = mutableListOf<HttpRequest>()
    private val script = ArrayDeque<Step>()
    var active = 0
        private set
    var maxActive = 0
        private set
    var cancelled = 0
        private set

    fun enqueue(vararg steps: Step) {
        steps.forEach { script.addLast(it) }
    }

    override suspend fun stream(request: HttpRequest, onLine: suspend (String) -> Unit): StreamResult {
        requests += request
        active++
        maxActive = maxOf(maxActive, active)
        try {
            return when (val step = script.removeFirstOrNull() ?: Step.Lines(emptyList())) {
                is Step.Lines -> {
                    step.lines.forEach { onLine(it) }
                    delay(step.holdMillis)
                    step.tail ?: awaitCancellation()
                }
                is Step.Reject -> StreamResult.Rejected(step.status, step.headers, step.body)
                is Step.Fail -> StreamResult.Failure(step.kind)
            }
        } catch (e: CancellationException) {
            cancelled++
            throw e
        } finally {
            active--
        }
    }
}
