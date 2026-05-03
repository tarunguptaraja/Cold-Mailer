package com.tarunguptaraja.coldemailer.presentation.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MockInterviewViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _tokens = MutableStateFlow(100000L)
    val tokens: StateFlow<Long> = _tokens.asStateFlow()

    init {
        viewModelScope.launch {
            tokenManager.tokens.collect {
                _tokens.value = it
            }
        }
    }
}
