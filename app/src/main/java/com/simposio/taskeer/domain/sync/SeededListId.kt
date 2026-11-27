package com.simposio.taskeer.domain.sync

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

// UUIDv5 (RFC 4122, SHA-1): distinto por usuario y idéntico entre dispositivos del mismo usuario (contrato 1.0, D-C2).
object SeededListId {
    private val NAMESPACE: UUID = UUID.fromString("7d1c3a52-4e8b-4b0f-9c6a-2f5e8d1a9b34")
    private const val NAME_PREFIX = "taskeer:seeded-task-list:"

    fun forUser(userId: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(ByteBuffer.allocate(16).putLong(NAMESPACE.mostSignificantBits).putLong(NAMESPACE.leastSignificantBits).array())
        digest.update((NAME_PREFIX + userId).toByteArray(Charsets.UTF_8))
        val hash = digest.digest()
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()
        val buffer = ByteBuffer.wrap(hash, 0, 16)
        return UUID(buffer.long, buffer.long).toString()
    }
}
