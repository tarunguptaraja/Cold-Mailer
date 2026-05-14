package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AtsReport(
    val score: Int,
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val missingKeywords: List<String>,
    val improvementTips: List<String>,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)
