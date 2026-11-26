package com.simposio.taskeer.data.remote.session

interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(data: ByteArray): ByteArray
}

class TokenCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)
