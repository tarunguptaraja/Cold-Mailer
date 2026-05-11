package com.tarunguptaraja.coldemailer

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigManager @Inject constructor() {

    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0) // 0 for immediate updates during dev
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // Defaults
        remoteConfig.setDefaultsAsync(
            mapOf(
                "onboarding_tokens" to 100000L,
                "gemini_model_name" to "gemini-2.5-flash",
                "interview_base_tokens" to 800L,
                "interview_tokens_per_question" to 700L
            )
        )
    }

    suspend fun fetchAndActivate() {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            // Log or ignore, it will use defaults
        }
    }

    fun getOnboardingTokens(): Long {
        return remoteConfig.getLong("onboarding_tokens")
    }

    fun getGeminiModelName(): String {
        return remoteConfig.getString("gemini_model_name")
    }

    fun getInterviewBaseTokens(): Long {
        return remoteConfig.getLong("interview_base_tokens")
    }

    fun getInterviewTokensPerQuestion(): Long {
        return remoteConfig.getLong("interview_tokens_per_question")
    }
}
