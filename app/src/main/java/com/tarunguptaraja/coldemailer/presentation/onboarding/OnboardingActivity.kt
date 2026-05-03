package com.tarunguptaraja.coldemailer.presentation.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tarunguptaraja.coldemailer.UserManager
import com.tarunguptaraja.coldemailer.databinding.ActivityOnboardingBinding
import com.tarunguptaraja.coldemailer.presentation.ats.AtsScorerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var remoteConfigManager: com.tarunguptaraja.coldemailer.RemoteConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Use live token value from Remote Config (fetched in Splash)
        val tokensValue = remoteConfigManager.getOnboardingTokens()
        binding.tvBonus.text = "🎁 Create your profile to instantly receive %,d AI Tokens!".format(tokensValue)

        binding.btnGetStarted.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val contact = binding.etContact.text.toString().trim()

            if (name.isEmpty()) {
                binding.tilName.error = "Name is required"
                return@setOnClickListener
            }
            binding.tilName.error = null

            binding.btnGetStarted.isEnabled = false
            binding.btnGetStarted.text = "Setting up..."

            lifecycleScope.launch {
                try {
                    val tokens = userManager.initializeUserIfRequired(name, contact)
                    
                    if (tokens > 0) {
                        val formattedTokens = String.format("%,d", tokens)
                        androidx.appcompat.app.AlertDialog.Builder(this@OnboardingActivity)
                            .setTitle("🎉 Congratulations!")
                            .setMessage("Your profile has been created and $formattedTokens AI Tokens have been added to your account!")
                            .setPositiveButton("Awesome") { _, _ ->
                                val intent = Intent(this@OnboardingActivity, com.tarunguptaraja.coldemailer.SendMailActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        val intent = Intent(this@OnboardingActivity, com.tarunguptaraja.coldemailer.SendMailActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } catch (e: Exception) {
                    binding.btnGetStarted.isEnabled = true
                    binding.btnGetStarted.text = "Get Started"
                    Toast.makeText(this@OnboardingActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
