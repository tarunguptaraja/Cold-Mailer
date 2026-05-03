package com.tarunguptaraja.coldemailer.presentation.interview

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.R
import com.tarunguptaraja.coldemailer.databinding.ActivityMockInterviewBinding
import com.tarunguptaraja.coldemailer.BottomNavHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MockInterviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMockInterviewBinding
    private val viewModel: MockInterviewViewModel by viewModels()

    @Inject
    lateinit var profilePreferenceManager: ProfilePreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMockInterviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupUI()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_mock_interview
    }

    private fun setupUI() {
        BottomNavHelper.setupBottomNav(
            this,
            binding.bottomNavigation,
            R.id.nav_mock_interview,
            profilePreferenceManager
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tokens.collect { tokens ->
                    binding.tvTokens.text = "%,d Tokens".format(tokens)
                }
            }
        }
    }
}
