package com.tarunguptaraja.coldemailer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(@ApplicationContext context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ColdEmailerTokenPrefs"
        private const val KEY_REMAINING_TOKENS = "remaining_tokens"
        private const val KEY_LAST_DAILY_BONUS_DATE = "last_daily_bonus_date"
        private const val DEFAULT_TOKENS = 100000L
    }

    private val _tokens = kotlinx.coroutines.flow.MutableStateFlow(getRemainingTokens())
    val tokens = _tokens.asStateFlow()

    fun getRemainingTokens(): Long {
        return sharedPreferences.getLong(KEY_REMAINING_TOKENS, DEFAULT_TOKENS)
    }

    fun deductTokens(count: Int) {
        val current = getRemainingTokens()
        val next = (current - count).coerceAtLeast(0)
        sharedPreferences.edit {
            putLong(KEY_REMAINING_TOKENS, next)
        }
        _tokens.value = next
    }

    fun hasSufficientTokens(): Boolean {
        return getRemainingTokens() > 0
    }

    fun setTokens(count: Long) {
        sharedPreferences.edit {
            putLong(KEY_REMAINING_TOKENS, count)
        }
        _tokens.value = count
    }

    fun addTokens(count: Long) {
        val current = getRemainingTokens()
        setTokens(current + count)
    }

    fun getLastDailyBonusDate(): String? {
        return sharedPreferences.getString(KEY_LAST_DAILY_BONUS_DATE, null)
    }

    fun setLastDailyBonusDate(dateStr: String) {
        sharedPreferences.edit {
            putString(KEY_LAST_DAILY_BONUS_DATE, dateStr)
        }
    }
}
