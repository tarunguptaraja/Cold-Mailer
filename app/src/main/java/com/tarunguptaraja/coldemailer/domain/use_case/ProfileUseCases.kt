package com.tarunguptaraja.coldemailer.domain.use_case

import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.domain.repository.ProfileRepository
import javax.inject.Inject

class GetProfileUseCase @Inject constructor(
    private val repository: ProfileRepository
) {
    operator fun invoke(): Profile? = repository.getProfile()
}

class SaveProfileUseCase @Inject constructor(
    private val repository: ProfileRepository
) {
    operator fun invoke(profile: Profile) = repository.saveProfile(profile)
}
