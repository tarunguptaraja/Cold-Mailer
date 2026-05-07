package com.tarunguptaraja.coldemailer.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarunguptaraja.coldemailer.domain.use_case.splash.FetchRemoteConfigUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.splash.ShouldNavigateToOnboardingUseCase
import com.tarunguptaraja.coldemailer.domain.use_case.splash.SyncUserDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SplashNavigation {
    data object Idle : SplashNavigation()
    data object ToOnboarding : SplashNavigation()
    data object ToMain : SplashNavigation()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val fetchRemoteConfigUseCase: FetchRemoteConfigUseCase,
    private val syncUserDataUseCase: SyncUserDataUseCase,
    private val shouldNavigateToOnboardingUseCase: ShouldNavigateToOnboardingUseCase
) : ViewModel() {

    private val _navigation = MutableStateFlow<SplashNavigation>(SplashNavigation.Idle)
    val navigation: StateFlow<SplashNavigation> = _navigation.asStateFlow()

    fun onAnimationComplete() {
        viewModelScope.launch {
            delay(800)
            fetchRemoteConfigUseCase()
            syncUserDataUseCase()
            _navigation.value = if (shouldNavigateToOnboardingUseCase()) {
                SplashNavigation.ToOnboarding
            } else {
                SplashNavigation.ToMain
            }
        }
    }
}
