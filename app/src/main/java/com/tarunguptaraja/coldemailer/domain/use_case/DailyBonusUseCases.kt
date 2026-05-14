package com.tarunguptaraja.coldemailer.domain.use_case

import com.tarunguptaraja.coldemailer.TokenManager
import com.tarunguptaraja.coldemailer.UserManager
import com.tarunguptaraja.coldemailer.domain.model.TokenTransaction
import javax.inject.Inject

class ClaimDailyBonusUseCase @Inject constructor(
    private val tokenManager: TokenManager,
    private val userManager: UserManager
) {
    suspend operator fun invoke(amount: Long) {
        val utcZone = java.util.TimeZone.getTimeZone("UTC")
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = utcZone
        val currentDateStr = dateFormat.format(java.util.Date())

        tokenManager.setLastDailyBonusDate(currentDateStr)
        tokenManager.addTokens(amount)
        
        val tx = TokenTransaction(
            id = java.util.UUID.randomUUID().toString(),
            amount = amount.toInt(),
            type = "AWARD",
            description = "Daily Bonus",
            timestamp = System.currentTimeMillis()
        )
        userManager.addTokenTransaction(tx)
    }
}

class CheckDailyBonusUseCase @Inject constructor(
    private val tokenManager: TokenManager,
    private val remoteConfigManager: com.tarunguptaraja.coldemailer.RemoteConfigManager
) {
    suspend operator fun invoke(): Long? {
        remoteConfigManager.fetchAndActivate()
        val bonusTokens = remoteConfigManager.getDailyBonusTokens()
        if (bonusTokens > 0) {
            val utcZone = java.util.TimeZone.getTimeZone("UTC")
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            dateFormat.timeZone = utcZone
            val currentDateStr = dateFormat.format(java.util.Date())

            val lastClaimDate = tokenManager.getLastDailyBonusDate()
            if (lastClaimDate != currentDateStr) {
                return bonusTokens
            }
        }
        return null
    }
}
