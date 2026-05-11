package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InterviewTopic(
    val id: String,
    val name: String,
    val description: String = "",
    val currentDepth: Int = 1, // 1=basic, 2=intermediate, 3=advanced
    val isComplete: Boolean = false,
    val questionsAsked: Int = 0,
    val maxDepthForTopic: Int = 3
) {
    fun getDifficultyForNextQuestion(): String {
        return when (currentDepth) {
            1 -> "Easy"
            2 -> "Medium"
            else -> "Hard"
        }
    }
    
    fun canGoDeeper(): Boolean {
        return currentDepth < maxDepthForTopic
    }
    
    fun goDeeper(): InterviewTopic {
        return copy(currentDepth = currentDepth + 1)
    }
    
    fun markComplete(): InterviewTopic {
        return copy(isComplete = true)
    }
    
    fun incrementQuestionsAsked(): InterviewTopic {
        return copy(questionsAsked = questionsAsked + 1)
    }
}
