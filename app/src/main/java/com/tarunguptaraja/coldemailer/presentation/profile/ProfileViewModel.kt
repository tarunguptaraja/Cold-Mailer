package com.tarunguptaraja.coldemailer.presentation.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.use_case.ExtractResumeTextUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GetProfileUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.SaveProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val subject: String = "",
    val body: String = "",
    val resumeName: String = "resume.pdf",
    val resumeText: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val isProfileSaved: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val extractResumeTextUseCase: ExtractResumeTextUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val profile = getProfileUseCase()
        profile?.let {
            _uiState.value = _uiState.value.copy(
                name = it.name,
                subject = it.subject,
                body = it.body,
                resumeName = it.resumeName,
                resumeText = it.resumeText
            )
        }
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onSubjectChanged(subject: String) {
        _uiState.value = _uiState.value.copy(subject = subject)
    }

    fun onBodyChanged(body: String) {
        _uiState.value = _uiState.value.copy(body = body)
    }

    fun onResumeSelected(uri: Uri, fileName: String) {
        _uiState.value = _uiState.value.copy(resumeName = fileName, isLoading = true)
        viewModelScope.launch {
            val text = extractResumeTextUseCase(uri)
            _uiState.value = _uiState.value.copy(
                resumeText = text,
                isLoading = false,
                message = if (text.isNotEmpty()) "Resume text extracted" else "Failed to extract text"
            )
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        val profile = Profile(
            state.name,
            state.subject,
            state.body,
            state.resumeName,
            state.resumeText
        )
        saveProfileUseCase(profile)
        _uiState.value = _uiState.value.copy(isProfileSaved = true, message = "Profile saved successfully")
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
