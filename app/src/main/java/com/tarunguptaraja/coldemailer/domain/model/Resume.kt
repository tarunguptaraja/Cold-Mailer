package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Resume(
    val fileName: String,
    val text: String
)
