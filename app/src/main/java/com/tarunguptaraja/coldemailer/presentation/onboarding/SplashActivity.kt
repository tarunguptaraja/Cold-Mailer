package com.tarunguptaraja.coldemailer.presentation.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.R
import com.tarunguptaraja.coldemailer.RemoteConfigManager
import com.tarunguptaraja.coldemailer.SendMailActivity
import com.tarunguptaraja.coldemailer.UserManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var profilePreferenceManager: ProfilePreferenceManager

    @Inject
    lateinit var remoteConfigManager: RemoteConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        setupVersion()
        startLogoAnimation()
    }

    private fun setupVersion() {
        val versionText = findViewById<TextView>(R.id.tv_version)
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        versionText.text = getString(R.string.version_format, versionName)
    }

    private fun startLogoAnimation() {
        val logo = findViewById<View>(R.id.iv_logo)
        val appName = findViewById<View>(R.id.tv_app_name)
        val tagline = findViewById<View>(R.id.tv_tagline)

        // Initial states
        logo.alpha = 0f
        logo.scaleX = 0.8f
        logo.scaleY = 0.8f
        appName.alpha = 0f
        tagline.alpha = 0f

        // Logo scale and fade animation
        val logoScaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.8f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.8f, 1f)
        val logoAlpha = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f)

        // App name fade in
        val nameAlpha = ObjectAnimator.ofFloat(appName, View.ALPHA, 0f, 1f)

        // Tagline fade in
        val taglineAlpha = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 0.9f)

        // Combine animations
        val animatorSet = AnimatorSet().apply {
            playTogether(logoScaleX, logoScaleY, logoAlpha)
            play(nameAlpha).after(logoAlpha)
            play(taglineAlpha).after(nameAlpha)
            duration = 600
            interpolator = AnticipateInterpolator()
        }

        animatorSet.doOnEnd {
            proceedToNextScreen()
        }

        animatorSet.start()
    }

    private fun proceedToNextScreen() {
        lifecycleScope.launch {
            // Minimum display time for branding (1.5 seconds total)
            delay(800)

            // Fetch Remote Config
            remoteConfigManager.fetchAndActivate()

            // Sync with Firestore
            userManager.performFullSync()

            // Navigate to appropriate screen
            val destination = if (profilePreferenceManager.hasUserRegistered()) {
                SendMailActivity::class.java
            } else {
                OnboardingActivity::class.java
            }

            startActivity(Intent(this@SplashActivity, destination))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
