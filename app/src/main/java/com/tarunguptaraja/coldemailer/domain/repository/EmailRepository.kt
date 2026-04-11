package com.tarunguptaraja.coldemailer.domain.repository

import com.tarunguptaraja.coldemailer.domain.model.EmailHistory
import com.tarunguptaraja.coldemailer.domain.model.JobAnalysis
import com.tarunguptaraja.coldemailer.domain.model.Profile

interface EmailRepository {
    suspend fun analyzeJob(jdInput: Any, resumeText: String, profile: Profile): JobAnalysis?
    fun addHistory(email: String, subject: String, dateSent: Long, body: String, followUp: String): Long
    fun getAllHistory(): List<EmailHistory>
    fun deleteHistory(id: Long)
}
