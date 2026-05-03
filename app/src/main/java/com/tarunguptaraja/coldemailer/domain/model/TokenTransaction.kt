package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenTransaction(
    val id: String,
    val amount: Int,
    val type: String, // "DEDUCTION" or "AWARD"
    val description: String,
    val timestamp: Long
)
