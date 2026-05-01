package com.tarunguptaraja.coldemailer.domain.model

import com.tarunguptaraja.coldemailer.domain.model.JobRole
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val name: String,
    val roles: List<JobRole> = emptyList()
)
