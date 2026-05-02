package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class JobAnalysis(
    val emails: List<String>,
    val company: String? = null,
    val role: String? = null,
    val initialBody: String,
    val followUpBody: String,
    val subject: String? = null,
    val atsScore: Int? = null,
    val atsFeedback: List<String>? = null,
    val tokensUsed: Int = 0
)
