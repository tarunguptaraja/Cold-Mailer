package com.tarunguptaraja.coldemailer.domain.repository

import com.tarunguptaraja.coldemailer.domain.model.EmailHistory
import com.tarunguptaraja.coldemailer.domain.model.JobAnalysis
import com.tarunguptaraja.coldemailer.domain.model.Profile

interface EmailRepository {
    suspend fun analyzeJob(jdInput: Any, resumeText: String, profile: Profile, tone: String): JobAnalysis?
    fun addHistory(email: String, subject: String, dateSent: Long, body: String, followUp: String, companyName: String, roleName: String, status: String): Long
    fun getAllHistory(): List<EmailHistory>
    fun deleteHistory(id: Long)
    fun updateHistoryStatus(id: Long, newStatus: String)
}
