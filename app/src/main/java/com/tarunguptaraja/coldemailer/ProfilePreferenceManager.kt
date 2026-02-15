package com.tarunguptaraja.coldemailer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class ProfilePreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ColdEmailerPrefs"
        private const val KEY_NAME = "name"
        private const val KEY_SUBJECT = "subject"
        private const val KEY_BODY = "body"
        private const val KEY_RESUME_NAME = "resumeName"
    }

    // Setter for ProfileData
    fun saveProfile(profileData: ProfileData) {
        sharedPreferences.edit {
            putString(KEY_NAME, profileData.name)
            putString(KEY_SUBJECT, profileData.subject)
            putString(KEY_BODY, profileData.body)
            putString(KEY_RESUME_NAME, profileData.resumeName)
        }
    }

    // Getter for ProfileData
    fun getProfile(): ProfileData? {
        val name = sharedPreferences.getString(KEY_NAME, null)
        val subject = sharedPreferences.getString(KEY_SUBJECT, null)
        val body = sharedPreferences.getString(KEY_BODY, null)
        val resumeName = sharedPreferences.getString(KEY_RESUME_NAME, null)

        return if (name != null && subject != null && body != null && resumeName != null) {
            ProfileData(name, subject, body, resumeName)
        } else {
            null
        }
    }

    // Individual getters
    fun getName(): String {
        return sharedPreferences.getString(KEY_NAME, "") ?: ""
    }

    fun getSubject(): String {
        return sharedPreferences.getString(KEY_SUBJECT, "") ?: ""
    }

    fun getBody(): String {
        return sharedPreferences.getString(KEY_BODY, "") ?: ""
    }

    fun getResumeName(): String {
        return sharedPreferences.getString(KEY_RESUME_NAME, "") ?: ""
    }

    // Individual setters
    fun setName(name: String) {
        sharedPreferences.edit().putString(KEY_NAME, name).apply()
    }

    fun setSubject(subject: String) {
        sharedPreferences.edit().putString(KEY_SUBJECT, subject).apply()
    }

    fun setBody(body: String) {
        sharedPreferences.edit().putString(KEY_BODY, body).apply()
    }

    fun setResumeName(resumeName: String) {
        sharedPreferences.edit().putString(KEY_RESUME_NAME, resumeName).apply()
    }

    // Clear all data
    fun clearProfile() {
        sharedPreferences.edit().clear().apply()
    }

    // Check if profile exists
    fun hasProfile(): Boolean {
        return sharedPreferences.contains(KEY_NAME) &&
                sharedPreferences.contains(KEY_SUBJECT) &&
                sharedPreferences.contains(KEY_BODY)
    }
}