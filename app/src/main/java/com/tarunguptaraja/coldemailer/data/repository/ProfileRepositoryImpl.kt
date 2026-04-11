package com.tarunguptaraja.coldemailer.data.repository

import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profilePrefs: ProfilePreferenceManager
) : ProfileRepository {

    override fun getProfile(): Profile? = profilePrefs.getProfile()

    override fun saveProfile(profile: Profile) = profilePrefs.saveProfile(profile)

    override fun getName(): String = profilePrefs.getName()

    override fun getSubject(): String = profilePrefs.getSubject()

    override fun getBody(): String = profilePrefs.getBody()

    override fun getResumeName(): String = profilePrefs.getResumeName()

    override fun getResumeText(): String = profilePrefs.getResumeText()

    override fun setResumeName(name: String) = profilePrefs.setResumeName(name)

    override fun setResumeText(text: String) = profilePrefs.setResumeText(text)
}
