package com.tarunguptaraja.coldemailer.domain.model

data class GeminiTokenTransaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val inputTokens: Int,
    val outputTokens: Int,
    val feature: String,
    val timestamp: Long = System.currentTimeMillis()
)
