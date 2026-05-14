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
                "cost_interview_base" to 0L,
                "cost_interview_per_question" to 1L,
                "cost_ats" to 2L,
                "cost_email" to 1L,
                "daily_bonus_tokens" to 5L,
                "shop_product_ids" to "token_pack_100,token_pack_500"
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
        return remoteConfig.getLong("cost_interview_base")
    }

    fun getInterviewTokensPerQuestion(): Long {
        return remoteConfig.getLong("cost_interview_per_question")
    }

    fun getCostAts(): Long {
        return remoteConfig.getLong("cost_ats")
    }

    fun getCostEmail(): Long {
        return remoteConfig.getLong("cost_email")
    }

    fun getDailyBonusTokens(): Long {
        return remoteConfig.getLong("daily_bonus_tokens")
    }

    fun getShopProductIds(): List<String> {
        val ids = remoteConfig.getString("shop_product_ids")
        if (ids.isBlank()) return emptyList()
        return ids.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
