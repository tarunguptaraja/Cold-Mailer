package com.tarunguptaraja.coldemailer.domain.use_case.splash

import com.tarunguptaraja.coldemailer.RemoteConfigManager
import javax.inject.Inject

class FetchRemoteConfigUseCase @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager
) {
    suspend operator fun invoke() {
        remoteConfigManager.fetchAndActivate()
    }
}
