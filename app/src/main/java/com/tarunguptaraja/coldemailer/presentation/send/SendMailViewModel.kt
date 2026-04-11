package com.tarunguptaraja.coldemailer.presentation.send

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.domain.model.JobAnalysis
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
    val screenshot: Bitmap? = null
)

@HiltViewModel
class SendMailViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val analyzeJobUseCase: AnalyzeJobUseCase,
    private val addHistoryUseCase: AddHistoryUseCase
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
        val state = _uiState.value
        val profile = state.profile ?: return
        
        val input: Any = state.screenshot ?: state.jdText
        if (input is String && input.isEmpty()) return

        _uiState.value = _uiState.value.copy(isAnalyzing = true, analysisError = null)

        viewModelScope.launch {
            val result = analyzeJobUseCase(input, profile.resumeText, profile)
            if (result != null) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    emails = result.emails.joinToString(", "),
                    modifiedBody = result.initialBody,
                    generatedFollowUp = result.followUpBody
                )
            } else {
                _uiState.value = _uiState.value.copy(isAnalyzing = false, analysisError = "Analysis failed")
            }
        }
    }

    fun saveSentHistory(recipientEmails: String) {
        val state = _uiState.value
        val profile = state.profile ?: return
        val emailsList = recipientEmails.split(Regex("[,;]")).map { it.trim() }.filter { it.isNotEmpty() }
        
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
        }
    }
}
