package com.tarunguptaraja.coldemailer.domain.use_case

import com.tarunguptaraja.coldemailer.GeminiManager
import com.tarunguptaraja.coldemailer.domain.model.AtsReport
import javax.inject.Inject

class CalculateAtsScoreUseCase @Inject constructor(
    private val geminiManager: GeminiManager
) {
    suspend operator fun invoke(
        jobProfile: String,
        experience: String,
        resumeText: String
    ): AtsReport? {
        return geminiManager.calculateAtsScore(jobProfile, experience, resumeText)
    }
}
