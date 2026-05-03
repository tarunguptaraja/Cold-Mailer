package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class JobRole(
    val id: String,
    val roleName: String,
    val subject: String,
    val body: String,
    val resumeFileName: String,
    val resumeText: String,
    val lastUpdated: Long = 0L
)
