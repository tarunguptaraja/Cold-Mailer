package com.tarunguptaraja.coldemailer.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.domain.use_case.onboarding.GetOnboardingTokensUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.onboarding.InitializeUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val tokens: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToMain: Boolean = false,
    val showSuccessDialog: Long? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val getOnboardingTokensUseCase: GetOnboardingTokensUseCase,
    private val initializeUserUseCase: InitializeUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadTokens()
    }

    private fun loadTokens() {
        _uiState.value = _uiState.value.copy(tokens = getOnboardingTokensUseCase())
    }

    fun initializeUser(name: String, contact: String) {
        if (name.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Name is required")
            return
        }
        _uiState.value = _uiState.value.copy(error = null, isLoading = true)

        viewModelScope.launch {
            val tokens = initializeUserUseCase(name, contact)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                showSuccessDialog = if (tokens > 0) tokens else null,
                navigateToMain = true
            )
        }
    }

    fun onNavigationHandled() {
        _uiState.value = _uiState.value.copy(navigateToMain = false)
    }

    fun onDialogShown() {
        _uiState.value = _uiState.value.copy(showSuccessDialog = null)
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
