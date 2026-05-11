package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InterviewQuestion(
    val id: String,
    val question: String,
    val expectedAnswer: String,
    val category: String,
    val difficulty: String,
    val topicId: String? = null,
    val isFollowUp: Boolean = false,
    val parentQuestionId: String? = null,
    val followUpReason: String? = null,
    val topicName: String? = null
)
