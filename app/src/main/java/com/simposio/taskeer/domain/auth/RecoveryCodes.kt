package com.simposio.taskeer.domain.auth

// No es `data class`: el `toString()` derivado mostraría los códigos en cualquier log o aserción.
class RecoveryCodes(val values: List<String>) {
    val count: Int get() = values.size

    override fun toString(): String = "RecoveryCodes(count=$count)"

    companion object {
        const val EXPECTED_COUNT = 10
    }
}
