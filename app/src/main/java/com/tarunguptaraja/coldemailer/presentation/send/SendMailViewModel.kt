package com.tarunguptaraja.coldemailer.presentation.send

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.domain.model.JobRole
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.use_case.AddHistoryUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.AnalyzeJobUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GetProfileUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.ScrapeUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SendMailUiState(
    val profile: Profile? = null,
    val roles: List<JobRole> = emptyList(),
    val selectedRole: JobRole? = null,
    val jdText: String = "",
    val emails: String = "",
    val modifiedSubject: String? = null,
    val modifiedBody: String? = null,
    val generatedFollowUp: String? = null,
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val screenshot: Bitmap? = null,
    val company: String? = null,
    val role: String? = null,
    val atsScore: Int? = null,
    val atsFeedback: List<String>? = null
)

@HiltViewModel
class SendMailViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val analyzeJobUseCase: AnalyzeJobUseCase,
    private val addHistoryUseCase: AddHistoryUseCase,
    private val scrapeUrlUseCase: ScrapeUrlUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendMailUiState())
    val uiState: StateFlow<SendMailUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val profile = getProfileUseCase()
        _uiState.value = _uiState.value.copy(
            profile = profile,
            roles = profile?.roles ?: emptyList(),
            selectedRole = profile?.roles?.firstOrNull()
        )
    }

    fun onJdTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(jdText = text)
    }

    fun onRoleSelected(role: JobRole) {
        _uiState.value = _uiState.value.copy(selectedRole = role)
    }

    fun onScreenshotSelected(bitmap: Bitmap?) {
        _uiState.value = _uiState.value.copy(screenshot = bitmap)
    }

    fun analyzeJob(tone: String = "Professional") {
        Log.d("SendMailViewModel", "analyzeJob called with tone: $tone")
        val state = _uiState.value
        val jdText = state.jdText.trim()

        if (jdText.startsWith("http")) {
            Log.d("SendMailViewModel", "Input is a URL, scraping first")
            _uiState.value = _uiState.value.copy(isAnalyzing = true, analysisError = null)
            viewModelScope.launch {
                val scrapeResult = scrapeUrlUseCase(jdText)
                scrapeResult.onSuccess { text ->
                    Log.d("SendMailViewModel", "Scrape success, proceeding to analysis")
                    performAnalysis(text, tone)
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        analysisError = "Failed to scrape URL: ${error.message}"
                    )
                }
            }
        } else {
            performAnalysis(jdText, tone)
        }
    }

    private fun performAnalysis(input: Any, tone: String) {
        // Refresh profile from repository
        val currentProfile = getProfileUseCase()
        _uiState.value = _uiState.value.copy(
            profile = currentProfile, 
            roles = currentProfile?.roles ?: emptyList(),
            isAnalyzing = true, 
            analysisError = null
        )

        val state = _uiState.value
        val profile = state.profile ?: run {
            _uiState.value = state.copy(analysisError = "Please set up your profile first", isAnalyzing = false)
            return
        }
        
        val targetRole = state.selectedRole ?: profile.roles.firstOrNull() ?: run {
            _uiState.value = state.copy(analysisError = "Please add at least one role in profile", isAnalyzing = false)
            return
        }

        val targetResumeText = targetRole.resumeText
        if (targetResumeText.isBlank()) {
            _uiState.value = state.copy(analysisError = "No resume text found for this role", isAnalyzing = false)
            return
        }

        val finalInput = state.screenshot ?: input

        viewModelScope.launch {
            val result = analyzeJobUseCase(finalInput, targetResumeText, profile, tone)
            if (result != null) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    emails = result.emails.joinToString(", "),
                    modifiedSubject = result.subject,
                    modifiedBody = result.initialBody,
                    generatedFollowUp = result.followUpBody,
                    company = result.company,
                    role = result.role,
                    atsScore = result.atsScore,
                    atsFeedback = result.atsFeedback
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    analysisError = "Analysis failed. Please check your connection."
                )
            }
        }
    }

    fun saveSentHistory(recipientEmails: String) {
        val state = _uiState.value
        val role = state.selectedRole ?: return
        
        val emailsList = recipientEmails.split(Regex("[,;]")).map { it.trim() }.filter { it.isNotEmpty() }
        val bodyToSend = state.modifiedBody ?: role.body
        val subjectToSend = state.modifiedSubject ?: role.subject
        val dateSent = System.currentTimeMillis()

        emailsList.forEach { email ->
            addHistoryUseCase(
                email,
                subjectToSend,
                dateSent,
                bodyToSend,
                state.generatedFollowUp ?: "",
                state.company ?: "Unknown Company",
                state.role ?: "Unknown Role"
            )
        }
    }
}
