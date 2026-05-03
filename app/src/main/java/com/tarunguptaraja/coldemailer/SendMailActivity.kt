package com.tarunguptaraja.coldemailer

import android.content.Intent
import com.tarunguptaraja.coldemailer.R

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tarunguptaraja.coldemailer.databinding.ActivitySendEmailBinding
import com.tarunguptaraja.coldemailer.domain.model.JobRole
import com.tarunguptaraja.coldemailer.domain.model.Profile
import com.tarunguptaraja.coldemailer.presentation.send.SendMailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

@AndroidEntryPoint
class SendMailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySendEmailBinding
    private val viewModel: SendMailViewModel by viewModels()

    private var currentRoleName: String? = null
    private var lastModifiedBody: String? = null
    private var lastModifiedSubject: String? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            viewModel.onScreenshotSelected(bitmap)
            binding.btnScreenshot.setImageBitmap(bitmap)
            Toast.makeText(this, getString(R.string.msg_screenshot_selected), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySendEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToneSpinner()
        setupListeners()
        observeState()
    }

    private fun setupToneSpinner() {
        val tones = arrayOf("Professional", "Enthusiastic", "Casual", "Direct", "Creative")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tones)
        binding.spinnerTone.setAdapter(adapter)
    }

    private fun setupListeners() {
        binding.etJdText.etJdTextChanged { viewModel.onJdTextChanged(it.toString()) }

        binding.sendEmail.setOnClickListener {
            val emailText = binding.etEmail.text.toString()
            if (emailText.isEmpty()) {
                Toast.makeText(this, getString(R.string.err_enter_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val state = viewModel.uiState.value
            val role = state.selectedRole ?: state.roles.firstOrNull()
            state.profile?.let { profile ->
                val pdfFile = role?.resumeFileName?.let { getLatestPdf(it) }
                if (pdfFile != null) {
                    val subjectToSend = binding.etSubject.text.toString()
                    val bodyToSend = binding.etEmailBody.text.toString()
                    
                    if (subjectToSend.isBlank() || bodyToSend.isBlank()) {
                        Toast.makeText(this, "Subject and Body cannot be empty", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    sendPdfInGmail(pdfFile, profile.copy(name = profile.name), subjectToSend, bodyToSend)
                    viewModel.saveSentHistory(emailText)
                } else {
                    Toast.makeText(this, "No resume found for this role", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        binding.btnScreenshot.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnAnalyze.setOnClickListener {
            val tone = binding.spinnerTone.text.toString()
            viewModel.analyzeJob(tone)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressAnalysis.visibility = if (state.isAnalyzing) View.VISIBLE else View.INVISIBLE
                    binding.btnAnalyze.isEnabled = !state.isAnalyzing

                    // Role Dropdown
                    if (state.roles.isNotEmpty()) {
                        val roleNames = state.roles.map { it.roleName }
                        val adapter = ArrayAdapter(this@SendMailActivity, android.R.layout.simple_dropdown_item_1line, roleNames)
                        binding.spinnerRole.setAdapter(adapter)
                        
                        if (binding.spinnerRole.text.isNullOrEmpty() || !roleNames.contains(binding.spinnerRole.text.toString())) {
                            val defaultRole = state.selectedRole ?: state.roles.first()
                            binding.spinnerRole.setText(defaultRole.roleName, false)
                        }
                    }

                    binding.spinnerRole.setOnItemClickListener { _, _, position, _ ->
                        viewModel.onRoleSelected(state.roles[position])
                    }

                    // JD Text Sync
                    if (state.jdText != binding.etJdText.text.toString() && !state.isAnalyzing) {
                        binding.etJdText.setText(state.jdText)
                    }

                    // Email Data Population
                    val activeRole = state.selectedRole ?: state.roles.firstOrNull()
                    
                    // Handle Role Change (populate defaults if no AI modifications exist)
                    if (activeRole != null && activeRole.roleName != currentRoleName) {
                        currentRoleName = activeRole.roleName
                        // Role changed, apply role defaults
                        binding.etSubject.setText(activeRole.subject)
                        binding.etEmailBody.setText(activeRole.body)
                        lastModifiedSubject = null
                        lastModifiedBody = null
                    } else {
                        // Handle AI Analysis Results
                        if (state.modifiedSubject != null && state.modifiedSubject != lastModifiedSubject) {
                            lastModifiedSubject = state.modifiedSubject
                            binding.etSubject.setText(state.modifiedSubject)
                        }
                        if (state.modifiedBody != null && state.modifiedBody != lastModifiedBody) {
                            lastModifiedBody = state.modifiedBody
                            binding.etEmailBody.setText(state.modifiedBody)
                        }
                    }

                    if (state.emails.isNotEmpty() && binding.etEmail.text.isNullOrEmpty()) {
                        binding.etEmail.setText(state.emails)
                    }

                    // ATS Results
                    if (state.atsScore != null) {
                        binding.cardAtsScore.visibility = View.VISIBLE
                        binding.atsProgress.progress = state.atsScore
                        binding.tvAtsScore.text = "${state.atsScore}%"
                        binding.tvAtsFeedback.text = state.atsFeedback?.joinToString("\n• ", prefix = "• ")
                    }

                    state.analysisError?.let {
                        Toast.makeText(this@SendMailActivity, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun getLatestPdf(fileName: String): File? {
        val pdfDir = File(getExternalFilesDir(null), "pdfs")
        val file = File(pdfDir, fileName)
        return if (file.exists()) file else null
    }

    private fun sendPdfInGmail(pdfFile: File, profile: Profile, subject: String, body: String) {
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(binding.etEmail.text.toString()))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.google.android.gm")
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            intent.setPackage(null)
            startActivity(Intent.createChooser(intent, "Send Email"))
        }
    }
}

// Extension to avoid infinite loop
fun android.widget.EditText.etJdTextChanged(after: (android.text.Editable?) -> Unit) {
    this.addTextChangedListener(object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) { after(s) }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}
