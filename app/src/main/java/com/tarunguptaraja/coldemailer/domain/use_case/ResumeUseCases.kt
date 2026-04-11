package com.tarunguptaraja.coldemailer.domain.use_case

import android.net.Uri
import com.tarunguptaraja.coldemailer.domain.repository.ResumeRepository
import javax.inject.Inject

class ExtractResumeTextUseCase @Inject constructor(
    private val repository: ResumeRepository
) {
    suspend operator fun invoke(uri: Uri): String = repository.extractText(uri)
}
