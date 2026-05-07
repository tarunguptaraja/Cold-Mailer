package com.tarunguptaraja.coldemailer.presentation.onboarding

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tarunguptaraja.coldemailer.SendMailActivity
import com.tarunguptaraja.coldemailer.databinding.ActivityOnboardingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        setupUI()
        observeState()
    }

    private fun setupUI() {
        binding.btnGetStarted.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val contact = binding.etContact.text.toString().trim()
            viewModel.initializeUser(name, contact)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvBonus.text = "🎁 Create your profile to instantly receive %,d AI Tokens!".format(state.tokens)

                    state.error?.let {
                        binding.tilName.error = it
                        viewModel.onErrorShown()
                    } ?: run { binding.tilName.error = null }

                    binding.btnGetStarted.isEnabled = !state.isLoading
                    binding.btnGetStarted.text = if (state.isLoading) "Setting up..." else "Get Started"

                    state.showSuccessDialog?.let { tokens ->
                        val formattedTokens = String.format("%,d", tokens)
                        androidx.appcompat.app.AlertDialog.Builder(this@OnboardingActivity)
                            .setTitle("🎉 Congratulations!")
                            .setMessage("Your profile has been created and $formattedTokens AI Tokens have been added to your account!")
                            .setPositiveButton("Awesome") { _, _ ->
                                viewModel.onDialogShown()
                                navigateToMain()
                            }
                            .setCancelable(false)
                            .show()
                    }

                    if (state.navigateToMain && state.showSuccessDialog == null) {
                        navigateToMain()
                        viewModel.onNavigationHandled()
                    }
                }
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, SendMailActivity::class.java))
        finish()
    }
}
