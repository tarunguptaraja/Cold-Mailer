package com.tarunguptaraja.coldemailer

data class ProfileData(
    val name: String, 
    val subject: String, 
    val body: String, 
    val resumeName: String, 
    val resumeText: String = "",
    val totalExp: String = "",
    val currentCTC: String = "",
    val expectedCTC: String = "",
    val noticePeriod: String = "",
    val location: String = "",
    val immediateJoiner: String = "Yes"
)
