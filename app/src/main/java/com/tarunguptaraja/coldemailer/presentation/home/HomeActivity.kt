package com.tarunguptaraja.coldemailer.presentation.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tarunguptaraja.coldemailer.MainActivity
import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.SendMailActivity
import com.tarunguptaraja.coldemailer.TokenManager
import com.tarunguptaraja.coldemailer.databinding.ActivityHomeBinding
import com.tarunguptaraja.coldemailer.presentation.ats.AtsScorerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var profilePreferenceManager: ProfilePreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        observeTokens()
    }

    override fun onResume() {
        super.onResume()
        setupUI()
    }

    private fun setupUI() {
        val userName = profilePreferenceManager.getName()
        binding.tvGreeting.text = if (userName.isNotBlank()) "Hi, $userName!" else "Hi there!"

        binding.cardAts.setOnClickListener {
            startActivity(Intent(this, AtsScorerActivity::class.java))
        }

        binding.cardMailer.setOnClickListener {
            if (profilePreferenceManager.hasProfile()) {
                startActivity(Intent(this, SendMailActivity::class.java))
            } else {
                showProfileRequiredDialog()
            }
        }

        binding.cardMockInterview.setOnClickListener {
            Toast.makeText(this, "AI Mock Interviews are coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.cardProfile.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun showProfileRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Profile Required")
            .setMessage("You need to set up at least one Job Role in your profile before you can use the Smart Cold Mailer. Let's do that now!")
            .setPositiveButton("Set Up Profile") { _, _ ->
                startActivity(Intent(this, MainActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeTokens() {
        lifecycleScope.launch {
            tokenManager.tokens.collect { tokens ->
                binding.tvTokens.text = String.format("%,d Tokens", tokens)
            }
        }
    }
}
