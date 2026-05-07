package com.tarunguptaraja.coldemailer.domain.use_case.splash

import com.tarunguptaraja.coldemailer.UserManager
import javax.inject.Inject

class SyncUserDataUseCase @Inject constructor(
    private val userManager: UserManager
) {
    suspend operator fun invoke() {
        userManager.performFullSync()
    }
}
