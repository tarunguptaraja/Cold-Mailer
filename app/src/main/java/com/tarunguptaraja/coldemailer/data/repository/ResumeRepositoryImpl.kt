package com.tarunguptaraja.coldemailer.data.repository

import android.net.Uri
import com.tarunguptaraja.coldemailer.ResumeParser
import com.tarunguptaraja.coldemailer.domain.repository.ResumeRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResumeRepositoryImpl @Inject constructor(
    private val resumeParser: ResumeParser
) : ResumeRepository {

    override suspend fun extractText(uri: Uri): String {
        return resumeParser.extractText(uri)
    }
}
