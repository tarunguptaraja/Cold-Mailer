package com.tarunguptaraja.coldemailer.domain.repository

import com.tarunguptaraja.coldemailer.domain.model.Profile

interface ProfileRepository {
    fun getProfile(): Profile?
    fun saveProfile(profile: Profile)
    fun getName(): String
    fun getSubject(): String
    fun getBody(): String
    fun getResumeName(): String
    fun getResumeText(): String
    fun setResumeName(name: String)
    fun setResumeText(text: String)
}
