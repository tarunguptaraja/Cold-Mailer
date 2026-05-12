package com.tarunguptaraja.coldemailer.presentation.interview

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.DocumentTextExtractor
import com.tarunguptaraja.coldemailer.GeminiManager
import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.RemoteConfigManager
import com.tarunguptaraja.coldemailer.TokenManager
import com.tarunguptaraja.coldemailer.UserManager
import com.tarunguptaraja.coldemailer.domain.model.AnswerType
import com.tarunguptaraja.coldemailer.domain.model.InterviewAnswer
import com.tarunguptaraja.coldemailer.domain.model.InterviewConfig
import com.tarunguptaraja.coldemailer.domain.model.InterviewQuestion
import com.tarunguptaraja.coldemailer.domain.model.InterviewResult
import com.tarunguptaraja.coldemailer.domain.model.InterviewSessionState
import com.tarunguptaraja.coldemailer.domain.model.InterviewTopic
import com.tarunguptaraja.coldemailer.domain.model.InterviewType
import com.tarunguptaraja.coldemailer.domain.model.QuestionAnalysis
import com.tarunguptaraja.coldemailer.domain.model.TokenTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
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
        val progress: String,
        val sessionTokensUsed: Int
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
    val error: String? = null
) {
    val canStartInterview: Boolean
        get() = jobRole.isNotBlank() && 
                experience.isNotBlank() && 
                (resumeText.isNotBlank() || resumeUri != null)
    
    fun estimatedTokens(): Int {
        // Rough estimate based on typical Gemini usage
        val topicGen = 300
        val perQuestion = 400
        val perAnswerEval = 300
        val reportGen = 500
        return topicGen + (questionCount * perQuestion) + (questionCount * perAnswerEval) + reportGen
    }
}

// ==================== TOKEN TRACKING ====================

data class TokenUsageBreakdown(
    val estimatedTotal: Int = 0,
    val topicGeneration: Int = 0,
    val questionGeneration: Int = 0,
    val answerEvaluations: Map<String, Int> = emptyMap(),
    val finalReport: Int = 0
) {
    val totalUsed: Int
        get() = topicGeneration + questionGeneration + answerEvaluations.values.sum() + finalReport
}

@HiltViewModel
class MockInterviewViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val geminiManager: GeminiManager,
    private val documentTextExtractor: DocumentTextExtractor,
    private val profilePreferenceManager: ProfilePreferenceManager,
    private val userManager: UserManager,
    private val remoteConfigManager: RemoteConfigManager
) : ViewModel() {

    // ==================== STATE FLOWS ====================

    private val _uiState = MutableStateFlow<InterviewUiState>(InterviewUiState.Setup)
    val uiState: StateFlow<InterviewUiState> = _uiState.asStateFlow()

    private val _setupState = MutableStateFlow(InterviewSetupState())
    val setupState: StateFlow<InterviewSetupState> = _setupState.asStateFlow()

    private val _tokenBreakdown = MutableStateFlow(TokenUsageBreakdown())
    val tokenBreakdown: StateFlow<TokenUsageBreakdown> = _tokenBreakdown.asStateFlow()

    private val _tokens = MutableStateFlow(tokenManager.getRemainingTokens())
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
            jobDescriptionUri = uri,
            jobDescriptionFileName = fileName,
            jobDescriptionText = ""
        )
    }

    fun onResumeFileSelected(uri: Uri, fileName: String) {
        _setupState.value = _setupState.value.copy(
            resumeUri = uri,
            resumeFileName = fileName,
            resumeText = ""
        )
    }

    fun clearJobDescription() {
        _setupState.value = _setupState.value.copy(
            jobDescriptionUri = null,
            jobDescriptionFileName = "",
            jobDescriptionText = ""
        )
    }

    fun clearResume() {
        _setupState.value = _setupState.value.copy(
            resumeUri = null,
            resumeFileName = "",
            resumeText = ""
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
            _uiState.value = InterviewUiState.Loading("Planning interview topics...")
            
            val estimatedCost = (remoteConfigManager.getInterviewBaseTokens() + 
                                (state.questionCount * remoteConfigManager.getInterviewTokensPerQuestion())).toInt()
            
            _tokenBreakdown.value = TokenUsageBreakdown(estimatedTotal = estimatedCost)
            questionAnalyses.clear()

            // Deduct estimated upfront
            tokenManager.deductTokens(estimatedCost)
            val estTx = TokenTransaction(
                id = UUID.randomUUID().toString(),
                amount = estimatedCost,
                type = "DEDUCTION",
                description = "Interview: Estimated Cost for ${state.questionCount} Questions",
                timestamp = System.currentTimeMillis()
            )
            userManager.addTokenTransaction(estTx)

            try {
                // Extract text from files if needed
                val jdText = if (state.jobDescriptionText.isNotBlank()) {
                    state.jobDescriptionText
                } else if (state.jobDescriptionUri != null) {
                    documentTextExtractor.extractText(state.jobDescriptionUri, state.jobDescriptionFileName).text
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
                val resumeSummaryResult = if (resumeText.isNotBlank()) geminiManager.summarizeResume(resumeText) else null
                val jdSummaryResult = if (jdText.isNotBlank()) geminiManager.summarizeJobDescription(jdText) else null

                val resumeSummary = resumeSummaryResult?.first
                val jdSummary = jdSummaryResult?.first
                
                val summarizationTokens = (resumeSummaryResult?.second ?: 0) + (jdSummaryResult?.second ?: 0)

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
                val topicsResult = geminiManager.generateInterviewTopics(config)
                if (topicsResult == null) {
                    _setupState.value = state.copy(error = "Failed to generate interview topics")
                    _uiState.value = InterviewUiState.Setup
                    return@launch
                }

                // Update token breakdown
                _tokenBreakdown.value = _tokenBreakdown.value.copy(
                    topicGeneration = topicsResult.tokensUsed + summarizationTokens
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

        val questionResult = geminiManager.generateNextQuestion(
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

        // Update token breakdown
        val updatedBreakdown = _tokenBreakdown.value.copy(
            questionGeneration = _tokenBreakdown.value.questionGeneration + questionResult.tokensUsed
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
            val evaluationResult = geminiManager.evaluateAnswerWithDecision(
                config = InterviewConfig(
                    jobRole = _setupState.value.jobRole,
                    experience = _setupState.value.experience,
                    jobDescription = _setupState.value.jobDescriptionText,
                    resumeText = _setupState.value.resumeText,
                    interviewType = _setupState.value.interviewType,
                    questionCount = _setupState.value.questionCount,
                    resumeSummary = null,
                    jobSpecSummary = null
                ),
                question = currentQuestion,
                answer = answer,
                topic = currentTopic
            )

            if (evaluationResult == null) {
                _uiState.value = InterviewUiState.Setup
                return@launch
            }

            // Save the analysis for the final report
            questionAnalyses.add(evaluationResult.analysis)

            // Update token breakdown
            val updatedEvaluations = _tokenBreakdown.value.answerEvaluations.toMutableMap()
            updatedEvaluations[currentQuestion.id] = evaluationResult.tokensUsed
            _tokenBreakdown.value = _tokenBreakdown.value.copy(
                answerEvaluations = updatedEvaluations
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
                        ),
                        sessionState = sessionWithNextTopic
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
                        ),
                        sessionState = sessionWithNextTopic
                    )
                }
            }
        }
    }

    private suspend fun completeInterview(
        config: InterviewConfig,
        sessionState: InterviewSessionState
    ) {
        _uiState.value = InterviewUiState.Evaluating(
            progress = "Generating final interview report...",
            sessionTokensUsed = _tokenBreakdown.value.totalUsed
        )

        // Generate final report
        val reportResult = geminiManager.generateInterviewReport(
            config = config,
            answers = sessionState.answersGiven,
            questionAnalyses = questionAnalyses.toList()
        )

        if (reportResult == null) {
            _uiState.value = InterviewUiState.Setup
            return
        }

        // Update token breakdown
        _tokenBreakdown.value = _tokenBreakdown.value.copy(
            finalReport = reportResult.tokensUsed
        )

        val totalUsed = _tokenBreakdown.value.totalUsed
        val estimatedTotal = _tokenBreakdown.value.estimatedTotal

        if (totalUsed > estimatedTotal) {
            val extra = totalUsed - estimatedTotal
            tokenManager.deductTokens(extra)
            val tx = TokenTransaction(
                id = UUID.randomUUID().toString(),
                amount = extra,
                type = "DEDUCTION",
                description = "Interview: Cost Adjustment",
                timestamp = System.currentTimeMillis()
            )
            userManager.addTokenTransaction(tx)
        } else if (totalUsed < estimatedTotal) {
            val refund = estimatedTotal - totalUsed
            val currentTokens = tokenManager.getRemainingTokens()
            tokenManager.setTokens(currentTokens + refund)
            val tx = TokenTransaction(
                id = UUID.randomUUID().toString(),
                amount = refund,
                type = "REFUND",
                description = "Interview: Unused Tokens Refund",
                timestamp = System.currentTimeMillis()
            )
            userManager.addTokenTransaction(tx)
        }

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
            
            // For skip, we record an empty answer
            val answer = InterviewAnswer(
                questionId = currentQuestion.id,
                answerText = "Skipped",
                answerType = AnswerType.TEXT,
                timestamp = System.currentTimeMillis()
            )
            
            val sessionWithAnswer = currentState.sessionState.addAnswer(answer)
            
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
                    ),
                    sessionState = sessionWithNextTopic
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
                    ),
                    sessionState = sessionWithNextTopic
                )
            }
        }
    }

    fun reset() {
        _uiState.value = InterviewUiState.Setup
        _setupState.value = InterviewSetupState()
        _tokenBreakdown.value = TokenUsageBreakdown()
    }

    // ==================== TOKEN OBSERVATION ====================

    init {
        viewModelScope.launch {
            tokenManager.tokens.collect { tokens ->
                _tokens.value = tokens.toLong()
            }
        }
    }
}
