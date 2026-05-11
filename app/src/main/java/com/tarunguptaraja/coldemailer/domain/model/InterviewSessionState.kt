package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InterviewSessionState(
    val topics: List<InterviewTopic> = emptyList(),
    val currentTopicId: String? = null,
    val questionsAsked: List<InterviewQuestion> = emptyList(),
    val answersGiven: List<InterviewAnswer> = emptyList(),
    val totalQuestionsCount: Int = 0,
    val maxQuestions: Int = 10,
    val isFollowUpMode: Boolean = false,
    val isInterviewComplete: Boolean = false,
    val currentQuestionStartTime: Long = 0L
) {
    val currentTopic: InterviewTopic?
        get() = topics.find { it.id == currentTopicId }
    
    val completedTopics: List<InterviewTopic>
        get() = topics.filter { it.isComplete }
    
    val remainingTopics: List<InterviewTopic>
        get() = topics.filter { !it.isComplete }
    
    fun canAskMoreQuestions(): Boolean {
        return totalQuestionsCount < maxQuestions && remainingTopics.isNotEmpty()
    }
    
    fun getNextTopic(): InterviewTopic? {
        return remainingTopics.firstOrNull()
    }
    
    fun updateTopic(updatedTopic: InterviewTopic): InterviewSessionState {
        return copy(
            topics = topics.map { if (it.id == updatedTopic.id) updatedTopic else it }
        )
    }
    
    fun addQuestion(question: InterviewQuestion): InterviewSessionState {
        return copy(
            questionsAsked = questionsAsked + question,
            totalQuestionsCount = totalQuestionsCount + 1,
            isFollowUpMode = question.isFollowUp,
            currentQuestionStartTime = System.currentTimeMillis()
        )
    }
    
    fun addAnswer(answer: InterviewAnswer): InterviewSessionState {
        return copy(answersGiven = answersGiven + answer)
    }
    
    fun moveToNextTopic(): InterviewSessionState {
        val nextTopic = getNextTopic()
        return copy(
            currentTopicId = nextTopic?.id,
            isFollowUpMode = false
        )
    }
    
    fun completeInterview(): InterviewSessionState {
        return copy(isInterviewComplete = true)
    }
}
