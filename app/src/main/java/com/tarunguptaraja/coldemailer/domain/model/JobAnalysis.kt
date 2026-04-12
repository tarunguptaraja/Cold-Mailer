package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class JobAnalysis(
    val emails: List<String>,
    val company: String? = null,
    val role: String? = null,
    val initialBody: String,
    val followUpBody: String
)
