package com.tarunguptaraja.coldemailer.presentation.interview

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.DocumentTextExtractor
import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.RemoteConfigManager
import com.tarunguptaraja.coldemailer.TokenManager
import com.tarunguptaraja.coldemailer.domain.model.AnswerType
import com.tarunguptaraja.coldemailer.domain.model.InterviewAnswer
import com.tarunguptaraja.coldemailer.domain.model.InterviewConfig
import com.tarunguptaraja.coldemailer.domain.model.InterviewQuestion
import com.tarunguptaraja.coldemailer.domain.model.InterviewResult
import com.tarunguptaraja.coldemailer.domain.model.InterviewSessionState
import com.tarunguptaraja.coldemailer.domain.model.InterviewType
import com.tarunguptaraja.coldemailer.domain.model.QuestionAnalysis
import com.tarunguptaraja.coldemailer.domain.use_case.AnalyzeAnswerUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.DeductTokensUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GenerateInterviewReportUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GenerateInterviewTopicsUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GenerateNextQuestionUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GetRemainingTokensUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.LogGeminiTokensUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.SummarizeJobDescriptionUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.SummarizeResumeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==================== UI STATES ====================

sealed class InterviewUiState {
    data object Setup : InterviewUiState()
    data class Loading(val message: String) : InterviewUiState()
    data class InProgress(
        val sessionState: InterviewSessionState,
        val currentQuestion: InterviewQuestion?,
        val sessionTokensUsed: Int
    ) : InterviewUiState()

    data class Evaluating(
        val progress: String, val sessionTokensUsed: Int
    ) : InterviewUiState()

    data class Completed(
        val result: InterviewResult,
        val sessionState: InterviewSessionState,
        val totalTokensUsed: Int
    ) : InterviewUiState()
}

// ==================== SETUP STATE ====================

data class InterviewSetupState(
    val jobRole: String = "",
    val experience: String = "",
    val jobDescriptionText: String = "",
    val jobDescriptionUri: Uri? = null,
    val jobDescriptionFileName: String = "",
    val resumeText: String = "",
    val resumeUri: Uri? = null,
    val resumeFileName: String = "",
    val interviewType: InterviewType = InterviewType.MIXED,
    val questionCount: Int = 10,
    val isLoading: Boolean = false,
    val error: String? = null,
    val baseCost: Int = 0,
    val perQuestionCost: Int = 1
) {
    val canStartInterview: Boolean
        get() = jobRole.isNotBlank() && experience.isNotBlank() && (resumeText.isNotBlank() || resumeUri != null)
    val estimatedCost: Int
        get() = baseCost + (questionCount * perQuestionCost)
}

// ==================== TOKEN TRACKING ====================

data class TokenUsageBreakdown(
    val estimatedTotal: Int = 0, val inputTokens: Int = 0, val outputTokens: Int = 0
) {
    val totalUsed: Int
        get() = inputTokens + outputTokens
}

@HiltViewModel
class MockInterviewViewModel @Inject constructor(
    private val generateInterviewTopicsUseCase: GenerateInterviewTopicsUseCase,
    private val generateNextQuestionUseCase: GenerateNextQuestionUseCase,
    private val analyzeAnswerUseCase: AnalyzeAnswerUseCase,
    private val generateInterviewReportUseCase: GenerateInterviewReportUseCase,
    private val summarizeResumeUseCase: SummarizeResumeUseCase,
    private val summarizeJobDescriptionUseCase: SummarizeJobDescriptionUseCase,
    private val deductTokensUseCase: DeductTokensUseCase,
    private val logGeminiTokensUseCase: LogGeminiTokensUseCase,
    private val getRemainingTokensUseCase: GetRemainingTokensUseCase,
    private val documentTextExtractor: DocumentTextExtractor,
    private val profilePreferenceManager: ProfilePreferenceManager,
    private val tokenManager: TokenManager,
    private val remoteConfigManager: RemoteConfigManager
) : ViewModel() {

    // ==================== STATE FLOWS ====================

    private val _uiState = MutableStateFlow<InterviewUiState>(InterviewUiState.Setup)
    val uiState: StateFlow<InterviewUiState> = _uiState.asStateFlow()

    private val _setupState = MutableStateFlow(
        InterviewSetupState(
            baseCost = remoteConfigManager.getInterviewBaseTokens().toInt(),
            perQuestionCost = remoteConfigManager.getInterviewTokensPerQuestion().toInt()
        )
    )
    val setupState: StateFlow<InterviewSetupState> = _setupState.asStateFlow()

    private val _tokenBreakdown = MutableStateFlow(TokenUsageBreakdown())
    val tokenBreakdown: StateFlow<TokenUsageBreakdown> = _tokenBreakdown.asStateFlow()

    private val _tokens = MutableStateFlow(getRemainingTokensUseCase())
    val tokens: StateFlow<Long> = _tokens.asStateFlow()

    private val questionAnalyses = mutableListOf<QuestionAnalysis>()

    // ==================== SETUP METHODS ====================

    fun onJobRoleChanged(role: String) {
        _setupState.value = _setupState.value.copy(jobRole = role)
    }

    fun onExperienceChanged(experience: String) {
        _setupState.value = _setupState.value.copy(experience = experience)
    }

    fun onJobDescriptionTextChanged(text: String) {
        _setupState.value = _setupState.value.copy(jobDescriptionText = text)
    }

    fun onResumeTextChanged(text: String) {
        _setupState.value = _setupState.value.copy(resumeText = text)
    }

    fun onInterviewTypeChanged(type: InterviewType) {
        _setupState.value = _setupState.value.copy(interviewType = type)
    }

    fun onQuestionCountChanged(count: Int) {
        _setupState.value = _setupState.value.copy(questionCount = count)
    }

    fun onJobDescriptionFileSelected(uri: Uri, fileName: String) {
        _setupState.value = _setupState.value.copy(
            jobDescriptionUri = uri, jobDescriptionFileName = fileName, jobDescriptionText = ""
        )
    }

    fun onResumeFileSelected(uri: Uri, fileName: String) {
        _setupState.value = _setupState.value.copy(
            resumeUri = uri, resumeFileName = fileName, resumeText = ""
        )
    }

    fun clearJobDescription() {
        _setupState.value = _setupState.value.copy(
            jobDescriptionUri = null, jobDescriptionFileName = "", jobDescriptionText = ""
        )
    }

    fun clearResume() {
        _setupState.value = _setupState.value.copy(
            resumeUri = null, resumeFileName = "", resumeText = ""
        )
    }

    // ==================== INTERVIEW FLOW ====================

    fun startInterview() {
        val state = _setupState.value
        if (!state.canStartInterview) {
            _setupState.value = state.copy(error = "Please fill in all required fields")
            return
        }

        viewModelScope.launch {
            val estimatedCost = state.estimatedCost
            if (getRemainingTokensUseCase() < estimatedCost) {
                _setupState.value =
                    state.copy(error = "Not enough tokens. Required: $estimatedCost")
                return@launch
            }

            _uiState.value = InterviewUiState.Loading("Planning interview topics...")

            _tokenBreakdown.value = TokenUsageBreakdown(estimatedTotal = estimatedCost)
            questionAnalyses.clear()

            try {
                // ... (deduction moved to individual steps)

                // Extract text from files if needed
                // Extract text from files if needed
                val jdText = if (state.jobDescriptionText.isNotBlank()) {
                    state.jobDescriptionText
                } else if (state.jobDescriptionUri != null) {
                    documentTextExtractor.extractText(
                        state.jobDescriptionUri, state.jobDescriptionFileName
                    ).text
                } else {
                    ""
                }

                val resumeText = if (state.resumeText.isNotBlank()) {
                    state.resumeText
                } else if (state.resumeUri != null) {
                    documentTextExtractor.extractText(state.resumeUri, state.resumeFileName).text
                } else {
                    ""
                }

                // Generate context summaries first
                val resumeSummaryResult =
                    if (resumeText.isNotBlank()) summarizeResumeUseCase(resumeText) else null
                val jdSummaryResult =
                    if (jdText.isNotBlank()) summarizeJobDescriptionUseCase(jdText) else null

                val resumeSummary = resumeSummaryResult?.text
                val jdSummary = jdSummaryResult?.text

                val summarizationInput =
                    (resumeSummaryResult?.inputTokens ?: 0) + (jdSummaryResult?.inputTokens ?: 0)
                val summarizationOutput =
                    (resumeSummaryResult?.outputTokens ?: 0) + (jdSummaryResult?.outputTokens ?: 0)

                // Create config
                val config = InterviewConfig(
                    jobRole = state.jobRole,
                    experience = state.experience,
                    jobDescription = jdText,
                    resumeText = resumeText,
                    interviewType = state.interviewType,
                    questionCount = state.questionCount,
                    resumeSummary = resumeSummary,
                    jobSpecSummary = jdSummary
                )

                // Generate interview topics
                val topicsResult = generateInterviewTopicsUseCase(config)
                if (topicsResult == null) {
                    _setupState.value = state.copy(error = "Failed to generate interview topics")
                    _uiState.value = InterviewUiState.Setup
                    return@launch
                }

                // Update token breakdown
                _tokenBreakdown.value = _tokenBreakdown.value.copy(
                    inputTokens = _tokenBreakdown.value.inputTokens + topicsResult.inputTokens + summarizationInput,
                    outputTokens = _tokenBreakdown.value.outputTokens + topicsResult.outputTokens + summarizationOutput
                )

                // Initialize session state
                val sessionState = InterviewSessionState(
                    topics = topicsResult.topics,
                    currentTopicId = topicsResult.topics.firstOrNull()?.id,
                    maxQuestions = state.questionCount
                )

                // Generate first question
                generateNextQuestion(config, sessionState)

            } catch (e: Exception) {
                _setupState.value = state.copy(error = "Error starting interview: ${e.message}")
                _uiState.value = InterviewUiState.Setup
            }
        }
    }

    private suspend fun generateNextQuestion(
        config: InterviewConfig,
        sessionState: InterviewSessionState,
        previousQuestion: InterviewQuestion? = null,
        previousAnswer: InterviewAnswer? = null,
        previousAnalysis: QuestionAnalysis? = null,
        isFollowUp: Boolean = false
    ) {
        _uiState.value = InterviewUiState.Loading("Generating next question...")

        val currentTopic = sessionState.currentTopic
        if (currentTopic == null || !sessionState.canAskMoreQuestions()) {
            // Interview complete
            completeInterview(config, sessionState)
            return
        }

        val questionResult = generateNextQuestionUseCase(
            config = config,
            topic = currentTopic,
            previousQuestion = previousQuestion,
            previousAnswer = previousAnswer,
            previousAnalysis = previousAnalysis,
            isFollowUp = isFollowUp
        )

        if (questionResult == null) {
            _uiState.value = InterviewUiState.Setup
            return
        }

        // Deduct total tokens on first question
        if (sessionState.questionsAsked.isEmpty()) {
            val totalCost = _setupState.value.estimatedCost
            deductTokensUseCase(totalCost, "Full Mock Interview: ${config.jobRole}")
        }

        // Update token breakdown
        val updatedBreakdown = _tokenBreakdown.value.copy(
            inputTokens = _tokenBreakdown.value.inputTokens + questionResult.inputTokens,
            outputTokens = _tokenBreakdown.value.outputTokens + questionResult.outputTokens
        )
        _tokenBreakdown.value = updatedBreakdown

        // Update session state
        val updatedSessionState = sessionState.addQuestion(questionResult.question)
        if (isFollowUp) {
            val updatedTopic = currentTopic.goDeeper().incrementQuestionsAsked()
            val sessionWithUpdatedTopic = updatedSessionState.updateTopic(updatedTopic)
            _uiState.value = InterviewUiState.InProgress(
                sessionState = sessionWithUpdatedTopic,
                currentQuestion = questionResult.question,
                sessionTokensUsed = updatedBreakdown.totalUsed
            )
        } else {
            val updatedTopic = currentTopic.incrementQuestionsAsked()
            val sessionWithUpdatedTopic = updatedSessionState.updateTopic(updatedTopic)
            _uiState.value = InterviewUiState.InProgress(
                sessionState = sessionWithUpdatedTopic,
                currentQuestion = questionResult.question,
                sessionTokensUsed = updatedBreakdown.totalUsed
            )
        }
    }

    fun submitAnswer(answerText: String, isVoice: Boolean) {
        val currentState = _uiState.value as? InterviewUiState.InProgress
        val currentQuestion = currentState?.currentQuestion

        if (currentQuestion == null) return

        viewModelScope.launch {
            _uiState.value = InterviewUiState.Evaluating(
                progress = "Evaluating your answer...",
                sessionTokensUsed = currentState.sessionTokensUsed
            )

            val answer = InterviewAnswer(
                questionId = currentQuestion.id,
                answerText = answerText,
                answerType = if (isVoice) AnswerType.VOICE else AnswerType.TEXT,
                timestamp = System.currentTimeMillis()
            )

            val currentTopic = currentState.sessionState.currentTopic
            if (currentTopic == null) {
                _uiState.value = InterviewUiState.Setup
                return@launch
            }

            // Evaluate answer with decision
            val evaluationResult = analyzeAnswerUseCase(
                config = InterviewConfig(
                    jobRole = _setupState.value.jobRole,
                    experience = _setupState.value.experience,
                    jobDescription = _setupState.value.jobDescriptionText,
                    resumeText = _setupState.value.resumeText,
                    interviewType = _setupState.value.interviewType,
                    questionCount = _setupState.value.questionCount,
                    resumeSummary = null,
                    jobSpecSummary = null
                ), question = currentQuestion, answer = answer, topic = currentTopic
            )

            if (evaluationResult == null) {
                _uiState.value = InterviewUiState.Setup
                return@launch
            }

            // Save the analysis for the final report
            questionAnalyses.add(evaluationResult.analysis)

            // Update token breakdown
            _tokenBreakdown.value = _tokenBreakdown.value.copy(
                inputTokens = _tokenBreakdown.value.inputTokens + evaluationResult.inputTokens,
                outputTokens = _tokenBreakdown.value.outputTokens + evaluationResult.outputTokens
            )

            // Update session state with answer
            val sessionWithAnswer = currentState.sessionState.addAnswer(answer)

            // Decide next action
            if (evaluationResult.shouldFollowUp && currentTopic.canGoDeeper()) {
                // Ask follow-up question
                generateNextQuestion(
                    config = InterviewConfig(
                        jobRole = _setupState.value.jobRole,
                        experience = _setupState.value.experience,
                        jobDescription = _setupState.value.jobDescriptionText,
                        resumeText = _setupState.value.resumeText,
                        interviewType = _setupState.value.interviewType,
                        questionCount = _setupState.value.questionCount
                    ),
                    sessionState = sessionWithAnswer,
                    previousQuestion = currentQuestion,
                    previousAnswer = answer,
                    previousAnalysis = evaluationResult.analysis,
                    isFollowUp = true
                )
            } else {
                // Move to next topic
                val completedTopic = currentTopic.markComplete()
                val sessionWithCompletedTopic = sessionWithAnswer.updateTopic(completedTopic)
                val sessionWithNextTopic = sessionWithCompletedTopic.moveToNextTopic()

                if (sessionWithNextTopic.canAskMoreQuestions()) {
                    generateNextQuestion(
                        config = InterviewConfig(
                            jobRole = _setupState.value.jobRole,
                            experience = _setupState.value.experience,
                            jobDescription = _setupState.value.jobDescriptionText,
                            resumeText = _setupState.value.resumeText,
                            interviewType = _setupState.value.interviewType,
                            questionCount = _setupState.value.questionCount,
                            resumeSummary = null,
                            jobSpecSummary = null
                        ), sessionState = sessionWithNextTopic
                    )
                } else {
                    completeInterview(
                        config = InterviewConfig(
                            jobRole = _setupState.value.jobRole,
                            experience = _setupState.value.experience,
                            jobDescription = _setupState.value.jobDescriptionText,
                            resumeText = _setupState.value.resumeText,
                            interviewType = _setupState.value.interviewType,
                            questionCount = _setupState.value.questionCount,
                            resumeSummary = null,
                            jobSpecSummary = null
                        ), sessionState = sessionWithNextTopic
                    )
                }
            }
        }
    }

    private suspend fun completeInterview(
        config: InterviewConfig, sessionState: InterviewSessionState
    ) {
        _uiState.value = InterviewUiState.Evaluating(
            progress = "Generating final interview report...",
            sessionTokensUsed = _tokenBreakdown.value.totalUsed
        )

        // Generate final report
        val reportResult = generateInterviewReportUseCase(
            config = config,
            answers = sessionState.answersGiven,
            analyses = questionAnalyses.toList()
        )

        if (reportResult == null) {
            _uiState.value = InterviewUiState.Setup
            return
        }

        // Base cost already deducted at start
        /*
        val baseCost = _setupState.value.baseCost
        if (baseCost > 0) {
            deductTokensUseCase(baseCost, "Interview Report/Setup")
        }
        */

        // Update token breakdown
        _tokenBreakdown.value = _tokenBreakdown.value.copy(
            inputTokens = _tokenBreakdown.value.inputTokens + reportResult.inputTokens,
            outputTokens = _tokenBreakdown.value.outputTokens + reportResult.outputTokens
        )

        // Log gemini usage
        logGeminiTokensUseCase(
            _tokenBreakdown.value.inputTokens, _tokenBreakdown.value.outputTokens, "MockInterview"
        )

        // Mark interview as complete
        val completedSession = sessionState.completeInterview()

        val record = com.tarunguptaraja.coldemailer.domain.model.InterviewHistoryRecord(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            jobRole = config.jobRole,
            experience = config.experience,
            result = reportResult.result
        )
        profilePreferenceManager.addInterviewHistoryRecord(record)

        _uiState.value = InterviewUiState.Completed(
            result = reportResult.result,
            sessionState = completedSession,
            totalTokensUsed = _tokenBreakdown.value.totalUsed
        )
    }

    fun skipQuestion() {
        val currentState = _uiState.value as? InterviewUiState.InProgress
        val currentQuestion = currentState?.currentQuestion

        if (currentQuestion == null) return

        viewModelScope.launch {
            _uiState.value = InterviewUiState.Evaluating(
                progress = "Skipping question...",
                sessionTokensUsed = currentState.sessionTokensUsed
            )

            val currentTopic = currentState.sessionState.currentTopic
            if (currentTopic == null) {
                _uiState.value = InterviewUiState.Setup
                return@launch
            }

            val answer = InterviewAnswer(
                questionId = currentQuestion.id,
                answerText = "Skipped",
                answerType = AnswerType.TEXT,
                timestamp = System.currentTimeMillis()
            )

            val sessionWithAnswer = currentState.sessionState.addAnswer(answer)

            // Record skip in analysis
            questionAnalyses.add(
                QuestionAnalysis(
                    questionId = currentQuestion.id,
                    question = currentQuestion.question,
                    userAnswer = "Skipped",
                    feedback = "Question was skipped by the user. No response was provided for evaluation.",
                    score = 0,
                    suggestedAnswer = "User skipped this question."
                )
            )

            // Move to next topic directly since we skipped
            val completedTopic = currentTopic.markComplete()
            val sessionWithCompletedTopic = sessionWithAnswer.updateTopic(completedTopic)
            val sessionWithNextTopic = sessionWithCompletedTopic.moveToNextTopic()

            if (sessionWithNextTopic.canAskMoreQuestions()) {
                generateNextQuestion(
                    config = InterviewConfig(
                        jobRole = _setupState.value.jobRole,
                        experience = _setupState.value.experience,
                        jobDescription = _setupState.value.jobDescriptionText,
                        resumeText = _setupState.value.resumeText,
                        interviewType = _setupState.value.interviewType,
                        questionCount = _setupState.value.questionCount
                    ), sessionState = sessionWithNextTopic
                )
            } else {
                completeInterview(
                    config = InterviewConfig(
                        jobRole = _setupState.value.jobRole,
                        experience = _setupState.value.experience,
                        jobDescription = _setupState.value.jobDescriptionText,
                        resumeText = _setupState.value.resumeText,
                        interviewType = _setupState.value.interviewType,
                        questionCount = _setupState.value.questionCount
                    ), sessionState = sessionWithNextTopic
                )
            }
        }
    }

    fun reset() {
        _uiState.value = InterviewUiState.Setup
        _setupState.value = InterviewSetupState(
            baseCost = remoteConfigManager.getInterviewBaseTokens().toInt(),
            perQuestionCost = remoteConfigManager.getInterviewTokensPerQuestion().toInt()
        )
        _tokenBreakdown.value = TokenUsageBreakdown()
    }

    // ==================== TOKEN OBSERVATION ====================

    init {
        viewModelScope.launch {
            remoteConfigManager.fetchAndActivate()
            _setupState.value = _setupState.value.copy(
                baseCost = remoteConfigManager.getInterviewBaseTokens().toInt(),
                perQuestionCost = remoteConfigManager.getInterviewTokensPerQuestion().toInt()
            )
        }
        viewModelScope.launch {
            tokenManager.tokens.collect { tokens ->
                _tokens.value = tokens.toLong()
            }
        }
    }
}
