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
import com.tarunguptaraja.coldemailer.domain.use_case.DeleteRoleUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.CheckDailyBonusUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.ClaimDailyBonusUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GetRemainingTokensUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val originalName: String = "",
    val contactNumber: String = "",
    val originalContactNumber: String = "",
    val userId: String = "",
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
    val hasChanges: Boolean = false,
    val remainingTokens: Long = 100000L,
    val transactions: List<com.tarunguptaraja.coldemailer.domain.model.TokenTransaction> = emptyList(),
    val dailyBonusAmount: Long? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val deleteRoleUseCase: DeleteRoleUseCase,
    private val extractResumeTextUseCase: ExtractResumeTextUseCase,
    private val checkDailyBonusUseCase: CheckDailyBonusUseCase,
    private val claimDailyBonusUseCase: ClaimDailyBonusUseCase,
    private val getRemainingTokensUseCase: GetRemainingTokensUseCase,
    private val profilePreferenceManager: com.tarunguptaraja.coldemailer.ProfilePreferenceManager,
    private val tokenManager: TokenManager,
    private val userManager: com.tarunguptaraja.coldemailer.UserManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        observeTokens()
        loadTransactions()
        checkDailyBonus()
    }

    private fun loadTransactions() {
        _uiState.value = _uiState.value.copy(transactions = profilePreferenceManager.getTransactions())
    }

    private fun checkDailyBonus() {
        viewModelScope.launch {
            val bonus = checkDailyBonusUseCase()
            if (bonus != null) {
                _uiState.value = _uiState.value.copy(dailyBonusAmount = bonus)
            }
        }
    }

    fun claimDailyBonus() {
        val amount = _uiState.value.dailyBonusAmount ?: return
        
        viewModelScope.launch {
            claimDailyBonusUseCase(amount)
            _uiState.value = _uiState.value.copy(
                dailyBonusAmount = null,
                message = "Claimed $amount daily bonus tokens!"
            )
        }
    }

    fun dismissDailyBonus() {
        _uiState.value = _uiState.value.copy(dailyBonusAmount = null)
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
                originalName = it.name,
                contactNumber = it.contactNumber,
                originalContactNumber = it.contactNumber,
                userId = it.userId.ifBlank { UUID.randomUUID().toString() },
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
        val state = _uiState.value
        _uiState.value = state.copy(
            name = name,
            hasChanges = name != state.originalName || state.contactNumber != state.originalContactNumber
        )
    }

    fun onContactNumberChanged(number: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            contactNumber = number,
            hasChanges = state.name != state.originalName || number != state.originalContactNumber
        )
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
        deleteRoleUseCase(roleId)
        saveProfile()
    }

    fun saveCurrentRole() {
        val state = _uiState.value
        
        if (state.isLoading) {
            _uiState.value = state.copy(message = "Please wait, extracting resume text...")
            return
        }

        if (state.currentRoleName.isBlank()) {
            _uiState.value = state.copy(message = "Please enter a role name")
            return
        }

        if (state.currentResumeText.isBlank()) {
            _uiState.value = state.copy(message = "Please select a resume and wait for extraction")
            return
        }

        val newRole = JobRole(
            id = state.currentRoleId ?: UUID.randomUUID().toString(),
            roleName = state.currentRoleName,
            subject = state.currentSubject,
            body = state.currentBody,
            resumeFileName = state.currentResumeName,
            resumeText = state.currentResumeText,
            lastUpdated = System.currentTimeMillis()
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
        val profile = Profile(state.name, state.contactNumber, state.userId, updatedRoles, System.currentTimeMillis())
        saveProfileUseCase(profile)
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(currentRoleId = null)
    }

    private fun saveProfile() {
        val state = _uiState.value
        val profile = Profile(state.name, state.contactNumber, state.userId, state.roles, System.currentTimeMillis())
        saveProfileUseCase(profile)
    }

    fun saveProfileInfo() {
        saveProfile()
        val state = _uiState.value
        _uiState.value = state.copy(
            originalName = state.name,
            originalContactNumber = state.contactNumber,
            hasChanges = false,
            message = "Profile information updated"
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}