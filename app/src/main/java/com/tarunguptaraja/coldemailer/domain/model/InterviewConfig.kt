package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InterviewConfig(
    val jobRole: String,
    val experience: String,
    val resumeText: String,
    val jobDescription: String,
    val interviewType: InterviewType,
    val questionCount: Int,
    val resumeSummary: String? = null,
    val jobSpecSummary: String? = null
)
