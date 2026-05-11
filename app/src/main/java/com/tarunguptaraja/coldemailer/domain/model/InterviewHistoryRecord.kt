package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InterviewHistoryRecord(
    val id: String,
    val timestamp: Long,
    val jobRole: String,
    val experience: String,
    val result: InterviewResult
)
