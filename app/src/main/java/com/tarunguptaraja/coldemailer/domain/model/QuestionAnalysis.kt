package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class QuestionAnalysis(
    val questionId: String,
    val question: String = "",
    val userAnswer: String = "",
    val score: Int,
    val feedback: String,
    val suggestedAnswer: String,
    val tokensUsed: Int = 0
)
