package com.tarunguptaraja.coldemailer.presentation.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.TokenManager
import com.tarunguptaraja.coldemailer.domain.model.JobRole
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.use_case.ExtractResumeTextUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GetProfileUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.SaveProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val contactNumber: String = "",
    val roles: List<JobRole> = emptyList(),
    val currentRoleId: String? = null,
    val currentRoleName: String = "",
    val currentSubject: String = "",
    val currentBody: String = "",
    val currentResumeName: String = "",
    val currentResumeText: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val isProfileSaved: Boolean = false,
    val remainingTokens: Long = 100000L
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val extractResumeTextUseCase: ExtractResumeTextUseCase,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        observeTokens()
    }

    private fun observeTokens() {
        viewModelScope.launch {
            tokenManager.tokens.collect { tokens ->
                _uiState.value = _uiState.value.copy(remainingTokens = tokens)
            }
        }
    }

    private fun loadProfile() {
        val profile = getProfileUseCase()
        profile?.let {
            _uiState.value = _uiState.value.copy(
                name = it.name,
                contactNumber = it.contactNumber,
                roles = it.roles,
                remainingTokens = tokenManager.getRemainingTokens()
            )
        } ?: run {
            _uiState.value = _uiState.value.copy(
                remainingTokens = tokenManager.getRemainingTokens()
            )
        }
    }

    fun refreshTokens() {
        // Now handled by observeTokens()
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onContactNumberChanged(number: String) {
        _uiState.value = _uiState.value.copy(contactNumber = number)
    }

    fun onRoleNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(currentRoleName = name)
    }

    fun onSubjectChanged(subject: String) {
        _uiState.value = _uiState.value.copy(currentSubject = subject)
    }

    fun onBodyChanged(body: String) {
        _uiState.value = _uiState.value.copy(currentBody = body)
    }

    fun onResumeSelected(uri: Uri, fileName: String) {
        _uiState.value = _uiState.value.copy(currentResumeName = fileName, isLoading = true)
        viewModelScope.launch {
            val text = extractResumeTextUseCase(uri)
            _uiState.value = _uiState.value.copy(
                currentResumeText = text,
                isLoading = false,
                message = if (text.isNotEmpty()) "Resume text extracted" else "Failed to extract text"
            )
        }
    }

    fun addNewRole() {
        _uiState.value = _uiState.value.copy(
            currentRoleId = UUID.randomUUID().toString(),
            currentRoleName = "",
            currentSubject = "",
            currentBody = "",
            currentResumeName = "",
            currentResumeText = ""
        )
    }

    fun editRole(role: JobRole) {
        _uiState.value = _uiState.value.copy(
            currentRoleId = role.id,
            currentRoleName = role.roleName,
            currentSubject = role.subject,
            currentBody = role.body,
            currentResumeName = role.resumeFileName,
            currentResumeText = role.resumeText
        )
    }

    fun deleteRole(roleId: String) {
        val updatedRoles = _uiState.value.roles.filter { it.id != roleId }
        _uiState.value = _uiState.value.copy(roles = updatedRoles)
        saveProfile()
    }

    fun saveCurrentRole() {
        val state = _uiState.value
        if (state.currentRoleName.isBlank()) {
            _uiState.value = state.copy(message = "Please enter a role name")
            return
        }

        val newRole = JobRole(
            id = state.currentRoleId ?: UUID.randomUUID().toString(),
            roleName = state.currentRoleName,
            subject = state.currentSubject,
            body = state.currentBody,
            resumeFileName = state.currentResumeName,
            resumeText = state.currentResumeText
        )

        val updatedRoles = state.roles.toMutableList()
        val index = updatedRoles.indexOfFirst { it.id == newRole.id }
        if (index != -1) {
            updatedRoles[index] = newRole
        } else {
            updatedRoles.add(newRole)
        }

        _uiState.value = state.copy(
            roles = updatedRoles, currentRoleId = null, message = "Role saved"
        )
        saveProfile()
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(currentRoleId = null)
    }

    private fun saveProfile() {
        val state = _uiState.value
        val profile = Profile(state.name, state.contactNumber, state.roles)
        saveProfileUseCase(profile)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}