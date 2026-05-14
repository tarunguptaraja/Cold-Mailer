package com.tarunguptaraja.coldemailer.presentation.ats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.ResumeParser
import com.tarunguptaraja.coldemailer.domain.model.AtsReport
import com.tarunguptaraja.coldemailer.domain.use_case.CalculateAtsScoreUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.DeductTokensUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.LogGeminiTokensUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GetRemainingTokensUseCase
import com.tarunguptaraja.coldemailer.TokenManager
import com.tarunguptaraja.coldemailer.RemoteConfigManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AtsScorerUiState(
    val isLoading: Boolean = false,
    val jobProfile: String = "",
    val experience: String = "",
    val resumeUri: Uri? = null,
    val resumeFileName: String = "",
    val resumeText: String = "",
    val atsReport: AtsReport? = null,
    val error: String? = null,
    val tokensRemaining: Long = 100000L,
    val atsCost: Int = 2
)

@HiltViewModel
class AtsScorerViewModel @Inject constructor(
    private val calculateAtsScoreUseCase: CalculateAtsScoreUseCase,
    private val deductTokensUseCase: DeductTokensUseCase,
    private val logGeminiTokensUseCase: LogGeminiTokensUseCase,
    private val getRemainingTokensUseCase: GetRemainingTokensUseCase,
    private val resumeParser: ResumeParser,
    private val profilePreferenceManager: ProfilePreferenceManager,
    private val tokenManager: TokenManager,
    private val remoteConfigManager: RemoteConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AtsScorerUiState())
    val uiState: StateFlow<AtsScorerUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
        observeTokens()
    }

    private fun observeTokens() {
        viewModelScope.launch {
            tokenManager.tokens.collect { tokens ->
                _uiState.value = _uiState.value.copy(tokensRemaining = tokens)
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            remoteConfigManager.fetchAndActivate()
            _uiState.value = _uiState.value.copy(
                atsCost = remoteConfigManager.getCostAts().toInt()
            )
            val profile = profilePreferenceManager.getProfile()
            profile?.roles?.firstOrNull()?.let { role ->
                _uiState.value = _uiState.value.copy(
                    resumeFileName = role.resumeFileName,
                    resumeText = role.resumeText,
                    tokensRemaining = getRemainingTokensUseCase()
                )
            } ?: run {
                _uiState.value = _uiState.value.copy(
                    tokensRemaining = getRemainingTokensUseCase()
                )
            }
        }
    }

    fun onJobProfileChanged(profile: String) {
        _uiState.value = _uiState.value.copy(jobProfile = profile)
    }

    fun onExperienceChanged(exp: String) {
        _uiState.value = _uiState.value.copy(experience = exp)
    }

    fun onResumeSelected(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val text = resumeParser.extractText(uri)
            _uiState.value = _uiState.value.copy(
                resumeUri = uri,
                resumeFileName = fileName,
                resumeText = text,
                isLoading = false
            )
        }
    }

    fun calculateAtsScore() {
        val state = _uiState.value
        if (state.jobProfile.isEmpty() || state.experience.isEmpty() || state.resumeText.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Please fill all fields and upload a resume")
            return
        }

        viewModelScope.launch {
            val cost = remoteConfigManager.getCostAts()
            if (getRemainingTokensUseCase() < cost) {
                _uiState.value = _uiState.value.copy(error = "Not enough tokens. Required: $cost")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, atsReport = null)
            val report = calculateAtsScoreUseCase(
                jobProfile = state.jobProfile,
                experience = state.experience,
                resumeText = state.resumeText
            )
            
            if (report != null) {
                _uiState.value = _uiState.value.copy(atsReport = report, isLoading = false)
                deductTokensUseCase(cost.toInt(), "ATS Analysis: ${state.jobProfile}")
                logGeminiTokensUseCase(report.inputTokens, report.outputTokens, "ATS")
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to calculate ATS score. Please try again.")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun reset() {
        _uiState.value = _uiState.value.copy(atsReport = null)
    }
}
