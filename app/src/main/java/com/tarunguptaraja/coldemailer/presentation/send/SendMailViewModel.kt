package com.tarunguptaraja.coldemailer.presentation.send

import android.util.Log
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.use_case.AddHistoryUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.AnalyzeJobUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GetProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SendMailUiState(
    val profile: Profile? = null,
    val jdText: String = "",
    val emails: String = "",
    val modifiedBody: String? = null,
    val generatedFollowUp: String? = null,
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val screenshot: Bitmap? = null,
    val company: String? = null,
    val role: String? = null
)

@HiltViewModel
class SendMailViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val analyzeJobUseCase: AnalyzeJobUseCase,
    private val addHistoryUseCase: AddHistoryUseCase,
    private val analytics: FirebaseAnalytics
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendMailUiState())
    val uiState: StateFlow<SendMailUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(profile = getProfileUseCase())
    }

    fun onJdTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(jdText = text)
    }

    fun onScreenshotSelected(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(screenshot = bitmap)
    }

    fun analyzeJob() {
        Log.d("SendMailViewModel", "analyzeJob called")
        // Refresh profile from repository
        val currentProfile = getProfileUseCase()
        _uiState.value = _uiState.value.copy(profile = currentProfile)

        val state = _uiState.value
        val profile = state.profile ?: run {
            Log.d("SendMailViewModel", "Profile is null, returning")
            _uiState.value = state.copy(analysisError = "Please set up your profile in settings first")
            return
        }
        val input: Any = state.screenshot ?: state.jdText
        if (input is String && input.isEmpty()) {
            Log.d("SendMailViewModel", "JD Text is empty and no screenshot, returning")
            return
        }

        Log.d("SendMailViewModel", "Starting analysis with input type: ${if (input is Bitmap) "Bitmap" else "String (length=${(input as String).length})"} ")
        _uiState.value = _uiState.value.copy(isAnalyzing = true, analysisError = null)

        viewModelScope.launch {
            Log.d("SendMailViewModel", "Calling analyzeJobUseCase")
            val result = analyzeJobUseCase(input, profile.resumeText, profile)
            Log.d("SendMailViewModel", "analyzeJobUseCase result received")
            if (result != null) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    emails = result.emails.joinToString(", "),
                    modifiedBody = result.initialBody,
                    generatedFollowUp = result.followUpBody,
                    company = result.company,
                    role = result.role
                )
            } else {
                _uiState.value =
                    _uiState.value.copy(isAnalyzing = false, analysisError = "Analysis failed")
            }
        }
    }

    fun saveSentHistory(recipientEmails: String) {
        val state = _uiState.value
        val profile = state.profile ?: return
        val emailsList =
            recipientEmails.split(Regex("[,;]")).map { it.trim() }.filter { it.isNotEmpty() }
        val bodyToSend = state.modifiedBody ?: profile.body
        val dateSent = System.currentTimeMillis()

        emailsList.forEach { email ->
            addHistoryUseCase(
                email,
                profile.subject,
                dateSent,
                bodyToSend,
                state.generatedFollowUp ?: ""
            )

            analytics.logEvent("email_sent") {
                param("recipient_email", email)
                param("company_name", state.company ?: "Unknown")
                param("job_profile", state.role ?: "Unknown")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(analysisError = null)
    }
}
