package com.tarunguptaraja.coldemailer.domain.use_case

import android.util.Log
import com.tarunguptaraja.coldemailer.domain.model.JobAnalysis
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.repository.EmailRepository
import javax.inject.Inject

class AnalyzeJobUseCase @Inject constructor(
    private val repository: EmailRepository
) {
    suspend operator fun invoke(jdInput: Any, resumeText: String, profile: Profile, tone: String = "Professional"): JobAnalysis? {
        Log.d("AnalyzeJobUseCase", "Invoking analysis")
        return repository.analyzeJob(jdInput, resumeText, profile, tone)
    }
}

class GetHistoryUseCase @Inject constructor(
    private val repository: EmailRepository
) {
    operator fun invoke() = repository.getAllHistory()
}

class DeleteHistoryUseCase @Inject constructor(
    private val repository: EmailRepository
) {
    operator fun invoke(id: Long) = repository.deleteHistory(id)
}

class AddHistoryUseCase @Inject constructor(
    private val repository: EmailRepository
) {
    operator fun invoke(email: String, subject: String, dateSent: Long, body: String, followUp: String, companyName: String, roleName: String, status: String = "Applied") {
        repository.addHistory(email, subject, dateSent, body, followUp, companyName, roleName, status)
    }
}

class UpdateHistoryStatusUseCase @Inject constructor(
    private val repository: EmailRepository
) {
    operator fun invoke(id: Long, newStatus: String) = repository.updateHistoryStatus(id, newStatus)
}
