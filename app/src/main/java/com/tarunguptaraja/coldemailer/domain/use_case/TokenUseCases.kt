package com.tarunguptaraja.coldemailer.domain.use_case

import com.tarunguptaraja.coldemailer.TokenManager
import com.tarunguptaraja.coldemailer.UserManager
import com.tarunguptaraja.coldemailer.domain.model.GeminiTokenTransaction
import com.tarunguptaraja.coldemailer.domain.model.TokenTransaction
import javax.inject.Inject

class DeductTokensUseCase @Inject constructor(
    private val tokenManager: TokenManager,
    private val userManager: UserManager
) {
    suspend operator fun invoke(amount: Int, description: String) {
        tokenManager.deductTokens(amount)
        val tx = TokenTransaction(
            id = java.util.UUID.randomUUID().toString(),
            amount = amount,
            type = "DEDUCTION",
            description = description,
            timestamp = System.currentTimeMillis()
        )
        userManager.addTokenTransaction(tx)
    }
}

class LogGeminiTokensUseCase @Inject constructor(
    private val userManager: UserManager
) {
    suspend operator fun invoke(inputTokens: Int, outputTokens: Int, feature: String) {
        val geminiTx = GeminiTokenTransaction(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            feature = feature
        )
        userManager.logGeminiTokens(geminiTx)
    }
}

class GetRemainingTokensUseCase @Inject constructor(
    private val tokenManager: TokenManager
) {
    operator fun invoke(): Long = tokenManager.getRemainingTokens()
}
