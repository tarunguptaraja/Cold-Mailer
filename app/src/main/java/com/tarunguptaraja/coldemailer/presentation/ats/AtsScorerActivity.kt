package com.tarunguptaraja.coldemailer.presentation.ats

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tarunguptaraja.coldemailer.databinding.ActivityAtsScorerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.tarunguptaraja.coldemailer.R
import com.tarunguptaraja.coldemailer.BottomNavHelper
import com.tarunguptaraja.coldemailer.presentation.history.TransactionHistoryActivity
import com.tarunguptaraja.coldemailer.presentation.shop.ShopActivity
import javax.inject.Inject

@AndroidEntryPoint
class AtsScorerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAtsScorerBinding
    private val viewModel: AtsScorerViewModel by viewModels()

    @Inject
    lateinit var bottomNavHelper: BottomNavHelper

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Get file name
            val fileName = getFileName(it) ?: "Resume.pdf"
            viewModel.onResumeSelected(it, fileName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        binding = ActivityAtsScorerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        setupUI()
        observeState()
    }
    
    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_ats
    }

    private fun setupUI() {
        bottomNavHelper.setupBottomNav(this, binding.bottomNavigation, R.id.nav_ats)

        binding.etJobProfile.doAfterTextChanged {
            viewModel.onJobProfileChanged(it.toString())
        }

        binding.etExperience.doAfterTextChanged {
            viewModel.onExperienceChanged(it.toString())
        }

        binding.btnUploadResume.setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }

        binding.btnCalculate.setOnClickListener {
            viewModel.calculateAtsScore()
        }

        binding.btnReset.setOnClickListener {
            viewModel.reset()
            binding.resultsContainer.visibility = View.GONE
            binding.inputCard.visibility = View.VISIBLE
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }

        binding.tvTokens.setOnClickListener {
            startActivity(Intent(this, ShopActivity::class.java))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnCalculate.isEnabled = !state.isLoading
                    binding.tvTokens.text = "${state.tokensRemaining} Tokens"
                    
                    val btnText = "Calculate ATS Score   ${state.atsCost}"
                    val spannable = android.text.SpannableStringBuilder(btnText)
                    val icon = androidx.core.content.ContextCompat.getDrawable(this@AtsScorerActivity, R.drawable.ic_token)
                    icon?.let {
                        val size = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._14sdp)
                        it.setBounds(0, 0, size, size)
                        val imageSpan = android.text.style.ImageSpan(it, android.text.style.ImageSpan.ALIGN_CENTER)
                        val iconIndex = btnText.indexOf("  ") + 1
                        spannable.setSpan(imageSpan, iconIndex, iconIndex + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    binding.btnCalculate.text = spannable
                    
                    binding.tvResumeName.text = if (state.resumeFileName.isEmpty()) "No Resume Selected" else state.resumeFileName

                    state.atsReport?.let { report ->
                        binding.inputCard.visibility = View.GONE
                        binding.resultsContainer.visibility = View.VISIBLE
                        
                        binding.scoreProgress.progress = report.score
                        binding.tvScore.text = report.score.toString()
                        binding.tvSummary.text = report.summary
                        
                        binding.tvStrengths.text = report.strengths.joinToString("\n") { "• $it" }
                        binding.tvWeaknesses.text = report.weaknesses.joinToString("\n") { "• $it" }
                        binding.tvKeywords.text = report.missingKeywords.joinToString(", ")
                        binding.tvTips.text = report.improvementTips.joinToString("\n") { "• $it" }
                    }

                    state.error?.let {
                        Toast.makeText(this@AtsScorerActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun getFileName(uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }
}
