package com.tarunguptaraja.coldemailer.data.repository

import com.tarunguptaraja.coldemailer.DatabaseHelper
import com.tarunguptaraja.coldemailer.GeminiManager
import com.tarunguptaraja.coldemailer.domain.model.EmailHistory
import com.tarunguptaraja.coldemailer.domain.model.JobAnalysis
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.repository.EmailRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailRepositoryImpl @Inject constructor(
    private val dbHelper: DatabaseHelper,
    private val geminiManager: GeminiManager
) : EmailRepository {

    override suspend fun analyzeJob(jdInput: Any, resumeText: String, profile: Profile): JobAnalysis? {
        return geminiManager.analyzeJD(jdInput, resumeText, profile)
    }

    override fun addHistory(email: String, subject: String, dateSent: Long, body: String, followUp: String): Long {
        return dbHelper.addHistory(email, subject, dateSent, body, followUp)
    }

    override fun getAllHistory(): List<EmailHistory> {
        return dbHelper.getAllHistory()
    }

    override fun deleteHistory(id: Long) {
        dbHelper.deleteHistory(id)
    }
}
