package com.simposio.taskeer.domain.auth

object RecoveryCodeInput {
    private const val GROUP = 4
    private val SHAPE = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}$")
    private val EIGHT = Regex("^[A-Z0-9]{8}$")

    // Solo comodidad de teclado: el patrón que acepta el servidor no se relaja.
    fun normalize(raw: String): String? {
        val upper = raw.trim().uppercase()
        val candidate = if (EIGHT.matches(upper)) upper.substring(0, GROUP) + "-" + upper.substring(GROUP) else upper
        return candidate.takeIf { SHAPE.matches(it) }
    }
}
