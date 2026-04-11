package com.tarunguptaraja.coldemailer

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            viewModel.onScreenshotSelected(bitmap)
            binding.btnScreenshot.setImageBitmap(bitmap)
            Toast.makeText(this, "Screenshot selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySendEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.etJdText.doAfterTextChanged { viewModel.onJdTextChanged(it.toString()) }

        binding.sendEmail.setOnClickListener {
            val emailText = binding.etEmail.text.toString()
            if (emailText.isEmpty() || !emailText.contains("@")) {
                Toast.makeText(this, "Invalid Email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val state = viewModel.uiState.value
            state.profile?.let {
                val pdfFile = getLatestPdf(it)
                if (pdfFile != null) {
                    val bodyToSend = state.modifiedBody ?: it.body
                    sendPdfInGmail(pdfFile, it.copy(body = bodyToSend))
                    viewModel.saveSentHistory(emailText)
                } else {
                    Toast.makeText(this, "No PDF found", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "No Profile Data Found", Toast.LENGTH_SHORT).show()
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
            viewModel.analyzeJob()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.btnAnalyze.text =
                        if (state.isAnalyzing) "Analyzing..." else "Analyze with AI"
                    binding.btnAnalyze.isEnabled = !state.isAnalyzing
                    binding.progressAnalysis.visibility =
                        if (state.isAnalyzing) android.view.View.VISIBLE else android.view.View.INVISIBLE

                    if (state.emails.isNotEmpty() && binding.etEmail.text.isNullOrEmpty()) {
                        binding.etEmail.setText(state.emails)
                    }

                    state.analysisError?.let {
                        Toast.makeText(this@SendMailActivity, it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun getLatestPdf(profile: Profile): File? {
        val pdfDir = File(getExternalFilesDir(null), "pdfs")
        if (!pdfDir.exists()) return null

        return pdfDir.listFiles()?.filter {
            it.extension.equals("pdf", true) && it.name.contains(profile.resumeName)
        }?.maxByOrNull { it.lastModified() }
    }

    private fun sendPdfInGmail(file: File, profile: Profile) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            val emailText = binding.etEmail.text.toString()
            val emails = emailText.split(Regex("[,;]")).map { it.trim() }.filter { it.isNotEmpty() }
                .toTypedArray()
            putExtra(Intent.EXTRA_EMAIL, emails)
            putExtra(Intent.EXTRA_SUBJECT, profile.subject)
            putExtra(Intent.EXTRA_TEXT, profile.body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            val gmailIntent = Intent(intent)
            gmailIntent.setPackage("com.google.android.gm")
            startActivity(gmailIntent)
        } catch (e: android.content.ActivityNotFoundException) {
            startActivity(Intent.createChooser(intent, "Send Email"))
        }
    }
}
