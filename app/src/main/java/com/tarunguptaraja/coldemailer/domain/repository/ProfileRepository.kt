package com.tarunguptaraja.coldemailer.domain.repository

import com.tarunguptaraja.coldemailer.domain.model.Profile

import com.tarunguptaraja.coldemailer.domain.model.Resume

interface ProfileRepository {
    fun getProfile(): Profile?
    fun saveProfile(profile: Profile)
    fun getName(): String
    fun deleteRole(roleId: String)
}
