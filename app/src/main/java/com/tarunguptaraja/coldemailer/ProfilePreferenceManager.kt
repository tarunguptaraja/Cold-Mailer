package com.tarunguptaraja.coldemailer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tarunguptaraja.coldemailer.domain.model.JobRole
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.model.InterviewHistoryRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfilePreferenceManager @Inject constructor(@ApplicationContext context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ColdEmailerPrefs"
        private const val KEY_NAME = "name"
        private const val KEY_CONTACT_NUMBER = "contactNumber"
        private const val KEY_USER_ID = "userId"
        private const val KEY_ROLES_JSON = "rolesJson"
        
        // Legacy keys for migration
        private const val KEY_SUBJECT = "subject"
        private const val KEY_BODY = "body"
        private const val KEY_RESUME_NAME = "resumeName"
        private const val KEY_RESUME_TEXT = "resumeText"
        private const val KEY_LAST_UPDATED = "lastUpdated"
        private const val KEY_TRANSACTIONS_JSON = "transactionsJson"
        private const val KEY_INTERVIEW_HISTORY_JSON = "interviewHistoryJson"
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun saveProfile(profile: Profile) {
        sharedPreferences.edit {
            putString(KEY_NAME, profile.name)
            putString(KEY_CONTACT_NUMBER, profile.contactNumber)
            putString(KEY_USER_ID, profile.userId)
            putString(KEY_ROLES_JSON, json.encodeToString(profile.roles))
            putLong(KEY_LAST_UPDATED, profile.lastUpdated)
        }
    }

    fun getProfile(): Profile? {
        val name = sharedPreferences.getString(KEY_NAME, null)
        val contactNumber = sharedPreferences.getString(KEY_CONTACT_NUMBER, "") ?: ""
        val userId = sharedPreferences.getString(KEY_USER_ID, "") ?: ""
        val rolesJson = sharedPreferences.getString(KEY_ROLES_JSON, null)
        val lastUpdated = sharedPreferences.getLong(KEY_LAST_UPDATED, 0L)

        return if (name != null) {
            val roles = if (!rolesJson.isNullOrEmpty()) {
                try {
                    json.decodeFromString<List<JobRole>>(rolesJson)
                } catch (e: Exception) {
                    migrateLegacyData()
                }
            } else {
                migrateLegacyData()
            }
            Profile(name, contactNumber, userId, roles, lastUpdated)
        } else {
            null
        }
    }

    fun saveTransactions(transactions: List<com.tarunguptaraja.coldemailer.domain.model.TokenTransaction>) {
        sharedPreferences.edit {
            putString(KEY_TRANSACTIONS_JSON, json.encodeToString(transactions))
        }
    }

    fun getTransactions(): List<com.tarunguptaraja.coldemailer.domain.model.TokenTransaction> {
        val jsonStr = sharedPreferences.getString(KEY_TRANSACTIONS_JSON, null) ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addTransaction(transaction: com.tarunguptaraja.coldemailer.domain.model.TokenTransaction) {
        val current = getTransactions().toMutableList()
        current.add(0, transaction) // Add to top
        saveTransactions(current)
    }

    fun saveInterviewHistory(history: List<InterviewHistoryRecord>) {
        sharedPreferences.edit {
            putString(KEY_INTERVIEW_HISTORY_JSON, json.encodeToString(history))
        }
    }

    fun getInterviewHistory(): List<InterviewHistoryRecord> {
        val jsonStr = sharedPreferences.getString(KEY_INTERVIEW_HISTORY_JSON, null) ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addInterviewHistoryRecord(record: InterviewHistoryRecord) {
        val current = getInterviewHistory().toMutableList()
        current.add(0, record)
        saveInterviewHistory(current)
    }

    private fun migrateLegacyData(): List<JobRole> {
        val subject = sharedPreferences.getString(KEY_SUBJECT, null)
        val body = sharedPreferences.getString(KEY_BODY, null)
        val resumeName = sharedPreferences.getString(KEY_RESUME_NAME, null)
        val resumeText = sharedPreferences.getString(KEY_RESUME_TEXT, "") ?: ""

        if (subject != null && body != null && resumeName != null) {
            val defaultRole = JobRole(
                id = "default",
                roleName = "Default Role",
                subject = subject,
                body = body,
                resumeFileName = resumeName,
                resumeText = resumeText
            )
            val roles = listOf(defaultRole)
            // Save migrated data immediately
            sharedPreferences.edit().putString(KEY_ROLES_JSON, json.encodeToString(roles)).apply()
            return roles
        }
        return emptyList()
    }

    fun getName(): String {
        return sharedPreferences.getString(KEY_NAME, "") ?: ""
    }

    fun clearProfile() {
        sharedPreferences.edit().clear().apply()
    }

    fun hasProfile(): Boolean {
        return sharedPreferences.contains(KEY_NAME) && 
                (sharedPreferences.contains(KEY_ROLES_JSON) || sharedPreferences.contains(KEY_SUBJECT))
    }

    fun hasUserRegistered(): Boolean {
        return sharedPreferences.contains(KEY_NAME) && !sharedPreferences.getString(KEY_NAME, "").isNullOrBlank()
    }
}