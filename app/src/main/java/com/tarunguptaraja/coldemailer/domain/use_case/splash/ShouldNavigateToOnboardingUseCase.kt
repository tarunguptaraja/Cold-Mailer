package com.tarunguptaraja.coldemailer.domain.use_case.splash

import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import javax.inject.Inject

class ShouldNavigateToOnboardingUseCase @Inject constructor(
    private val profilePreferenceManager: ProfilePreferenceManager
) {
    operator fun invoke(): Boolean {
        return !profilePreferenceManager.hasUserRegistered()
    }
}
