package com.tarunguptaraja.coldemailer.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.domain.model.EmailHistory
import com.tarunguptaraja.coldemailer.domain.use_case.DeleteHistoryUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.GetHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.tarunguptaraja.coldemailer.domain.use_case.UpdateHistoryStatusUseCase

data class HistoryUiState(
    val historyList: List<EmailHistory> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getHistoryUseCase: GetHistoryUseCase,
    private val deleteHistoryUseCase: DeleteHistoryUseCase,
    private val updateHistoryStatusUseCase: UpdateHistoryStatusUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val history = getHistoryUseCase()
            _uiState.value = _uiState.value.copy(historyList = history, isLoading = false)
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            deleteHistoryUseCase(id)
            loadHistory()
        }
    }

    fun updateStatus(id: Long, status: String) {
        viewModelScope.launch {
            updateHistoryStatusUseCase(id, status)
            loadHistory()
        }
    }
}
