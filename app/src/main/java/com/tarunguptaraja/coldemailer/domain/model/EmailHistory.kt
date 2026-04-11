package com.tarunguptaraja.coldemailer.domain.model

data class EmailHistory(
    val id: Long = 0,
    val email: String,
    val subject: String,
    val dateSent: Long,
    val body: String = "",
    val followUp: String = ""
)
