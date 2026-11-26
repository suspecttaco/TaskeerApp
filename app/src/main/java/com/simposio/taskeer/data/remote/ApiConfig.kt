package com.simposio.taskeer.data.remote

class ApiConfig(
    baseUrl: String,
    val allowCleartext: Boolean,
    val userAgent: String,
    val connectTimeoutMs: Long = 15_000,
    val readTimeoutMs: Long = 45_000,
    val writeTimeoutMs: Long = 30_000,
    val callTimeoutMs: Long = 60_000,
) {
    val baseUrl: String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    init {
        val isHttps = this.baseUrl.startsWith("https://", ignoreCase = true)
        val isHttp = this.baseUrl.startsWith("http://", ignoreCase = true)
        require(isHttps || (isHttp && allowCleartext)) {
            "La URL base debe usar https (http solo con allowCleartext)"
        }
    }
}
