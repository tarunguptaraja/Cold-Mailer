package com.tarunguptaraja.coldemailer.domain.repository

import android.net.Uri

interface ResumeRepository {
    suspend fun extractText(uri: Uri): String
}
