package com.tarunguptaraja.coldemailer.domain.use_case.onboarding

import com.tarunguptaraja.coldemailer.UserManager
import javax.inject.Inject

class InitializeUserUseCase @Inject constructor(
    private val userManager: UserManager
) {
    suspend operator fun invoke(name: String, contact: String): Long {
        return userManager.initializeUserIfRequired(name, contact)
    }
}
