package com.tarunguptaraja.coldemailer.presentation.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.UserManager
import com.tarunguptaraja.coldemailer.presentation.ats.AtsScorerActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.tarunguptaraja.coldemailer.R
import com.tarunguptaraja.coldemailer.SendMailActivity

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var profilePreferenceManager: ProfilePreferenceManager

    @Inject
    lateinit var remoteConfigManager: com.tarunguptaraja.coldemailer.RemoteConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        lifecycleScope.launch {
            // Fetch Remote Config first
            remoteConfigManager.fetchAndActivate()
            
            // Always sync with Firestore on startup
            val syncSuccess = userManager.performFullSync()
            
            if (profilePreferenceManager.hasUserRegistered()) {
                startActivity(Intent(this@SplashActivity, SendMailActivity::class.java))
            } else {
                startActivity(Intent(this@SplashActivity, OnboardingActivity::class.java))
            }
            finish()
        }
    }
}
