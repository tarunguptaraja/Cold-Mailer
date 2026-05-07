package com.tarunguptaraja.coldemailer.presentation.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tarunguptaraja.coldemailer.R
import com.tarunguptaraja.coldemailer.SendMailActivity
import com.tarunguptaraja.coldemailer.databinding.ActivitySplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupVersion()
        startLogoAnimation()
        observeNavigation()
    }

    private fun setupVersion() {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        binding.tvVersion.text = getString(R.string.version_format, versionName)
    }

    private fun startLogoAnimation() {
        binding.ivLogo.alpha = 0f
        binding.ivLogo.scaleX = 0.8f
        binding.ivLogo.scaleY = 0.8f
        binding.tvAppName.alpha = 0f
        binding.tvTagline.alpha = 0f

        val logoScaleX = ObjectAnimator.ofFloat(binding.ivLogo, View.SCALE_X, 0.8f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(binding.ivLogo, View.SCALE_Y, 0.8f, 1f)
        val logoAlpha = ObjectAnimator.ofFloat(binding.ivLogo, View.ALPHA, 0f, 1f)
        val nameAlpha = ObjectAnimator.ofFloat(binding.tvAppName, View.ALPHA, 0f, 1f)
        val taglineAlpha = ObjectAnimator.ofFloat(binding.tvTagline, View.ALPHA, 0f, 0.9f)

        AnimatorSet().apply {
            playTogether(logoScaleX, logoScaleY, logoAlpha)
            play(nameAlpha).after(logoAlpha)
            play(taglineAlpha).after(nameAlpha)
            duration = 600
            interpolator = AnticipateInterpolator()
            doOnEnd { viewModel.onAnimationComplete() }
            start()
        }
    }

    private fun observeNavigation() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigation.collect { destination ->
                    when (destination) {
                        SplashNavigation.ToOnboarding -> navigateTo(OnboardingActivity::class.java)
                        SplashNavigation.ToMain -> navigateTo(SendMailActivity::class.java)
                        SplashNavigation.Idle -> {}
                    }
                }
            }
        }
    }

    private fun navigateTo(destination: Class<*>) {
        startActivity(Intent(this, destination))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
