package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InterviewResult(
    val overallScore: Int,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val questionAnalysis: List<QuestionAnalysis>,
    val tokensUsed: Int = 0
)
