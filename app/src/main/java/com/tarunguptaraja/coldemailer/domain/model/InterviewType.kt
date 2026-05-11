package com.tarunguptaraja.coldemailer.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class InterviewType {
    TECHNICAL,
    BEHAVIORAL,
    HR,
    MIXED
}
