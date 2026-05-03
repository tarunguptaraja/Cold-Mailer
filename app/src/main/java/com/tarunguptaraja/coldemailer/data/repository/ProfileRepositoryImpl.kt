package com.tarunguptaraja.coldemailer.data.repository

import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.model.Resume
import com.tarunguptaraja.coldemailer.domain.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profilePrefs: com.tarunguptaraja.coldemailer.ProfilePreferenceManager,
    private val userManager: com.tarunguptaraja.coldemailer.UserManager
) : ProfileRepository {

    override fun getProfile(): Profile? = profilePrefs.getProfile()

    override fun saveProfile(profile: Profile) {
        profilePrefs.saveProfile(profile)
        userManager.syncProfileToFirestore(profile)
    }

    override fun getName(): String = profilePrefs.getName()

    override fun deleteRole(roleId: String) {
        userManager.deleteRoleFromFirestore(roleId)
    }
}
