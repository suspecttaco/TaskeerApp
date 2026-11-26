package com.simposio.taskeer.data.remote

interface StreamTransport {
    // `onLine` solo se invoca tras un 2xx; cancelar la corrutina cierra la conexión.
    suspend fun stream(request: HttpRequest, onLine: suspend (String) -> Unit): StreamResult
}

sealed interface StreamResult {
    data object Ended : StreamResult

    // `body` acotado a 4 KiB: solo sirve para leer el código de error.
    class Rejected(val status: Int, val headers: Map<String, String>, val body: String) : StreamResult {
        override fun toString(): String = "Rejected($status)"
    }

    class Failure(val kind: TransportFailureKind) : StreamResult
}
