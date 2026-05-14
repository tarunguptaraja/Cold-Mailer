package com.tarunguptaraja.coldemailer.domain.use_case

import com.tarunguptaraja.coldemailer.GeminiManager
import com.tarunguptaraja.coldemailer.domain.model.InterviewAnswer
import com.tarunguptaraja.coldemailer.domain.model.InterviewConfig
import com.tarunguptaraja.coldemailer.domain.model.InterviewQuestion
import com.tarunguptaraja.coldemailer.domain.model.QuestionAnalysis
import com.tarunguptaraja.coldemailer.domain.model.InterviewTopic
import javax.inject.Inject

class GenerateInterviewTopicsUseCase @Inject constructor(
    private val geminiManager: GeminiManager
) {
    suspend operator fun invoke(config: InterviewConfig) =
        geminiManager.generateInterviewTopics(config)
}

class GenerateNextQuestionUseCase @Inject constructor(
    private val geminiManager: GeminiManager
) {
    suspend operator fun invoke(
        config: InterviewConfig,
        topic: InterviewTopic,
        previousQuestion: InterviewQuestion? = null,
        previousAnswer: InterviewAnswer? = null,
        previousAnalysis: QuestionAnalysis? = null,
        isFollowUp: Boolean = false
    ) = geminiManager.generateNextQuestion(
        config, topic, previousQuestion, previousAnswer, previousAnalysis, isFollowUp
    )
}

class AnalyzeAnswerUseCase @Inject constructor(
    private val geminiManager: GeminiManager
) {
    suspend operator fun invoke(
        config: InterviewConfig,
        question: InterviewQuestion,
        answer: InterviewAnswer,
        topic: InterviewTopic
    ) = geminiManager.evaluateAnswerWithDecision(config, question, answer, topic)
}

class GenerateInterviewReportUseCase @Inject constructor(
    private val geminiManager: GeminiManager
) {
    suspend operator fun invoke(
        config: InterviewConfig,
        answers: List<InterviewAnswer>,
        analyses: List<QuestionAnalysis>
    ) = geminiManager.generateInterviewReport(config, answers, analyses)
}

class SummarizeResumeUseCase @Inject constructor(
    private val geminiManager: GeminiManager
) {
    suspend operator fun invoke(resumeText: String) =
        geminiManager.summarizeResume(resumeText)
}

class SummarizeJobDescriptionUseCase @Inject constructor(
    private val geminiManager: GeminiManager
) {
    suspend operator fun invoke(jdText: String) =
        geminiManager.summarizeJobDescription(jdText)
}
