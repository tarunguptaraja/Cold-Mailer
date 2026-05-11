package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InterviewAnswer(
    val questionId: String,
    val answerText: String,
    val answerType: AnswerType,
    val timestamp: Long = System.currentTimeMillis()
)
