package com.tarunguptaraja.coldemailer.domain.use_case.onboarding

import com.tarunguptaraja.coldemailer.RemoteConfigManager
import javax.inject.Inject

class GetOnboardingTokensUseCase @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager
) {
    operator fun invoke(): Long {
        return remoteConfigManager.getOnboardingTokens()
    }
}
