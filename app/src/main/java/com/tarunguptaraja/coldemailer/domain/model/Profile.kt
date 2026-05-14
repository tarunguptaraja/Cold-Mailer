package com.tarunguptaraja.coldemailer.domain.model

import com.tarunguptaraja.coldemailer.domain.model.JobRole
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val name: String,
    val contactNumber: String = "",
    val userId: String = "",
    val roles: List<JobRole> = emptyList(),
    val lastUpdated: Long = 0L
)
