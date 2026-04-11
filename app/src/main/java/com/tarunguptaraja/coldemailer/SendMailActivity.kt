package com.tarunguptaraja.coldemailer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tarunguptaraja.coldemailer.databinding.ActivitySendEmailBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

class SendMailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySendEmailBinding
    private val dbHelper by lazy { DatabaseHelper(this) }
    private val profilePrefs: ProfilePreferenceManager by lazy { ProfilePreferenceManager(this) }
    private val profileData by lazy {
        profilePrefs.getProfile()
    }
    
    // Store the modified bodies after AI analysis
    private var modifiedEmailBody: String? = null
    private var generatedFollowUp: String? = null

    // Replace with your actual API Key from Google AI Studio
    // https://aistudio.google.com/app/apikey
    private val geminiApiKey = "AIzaSyAqMCYh92GXt68XUrW8diLn4rI4MR-CP4c"
    private val geminiManager by lazy { GeminiManager(geminiApiKey) }

    private var selectedBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = contentResolver.openInputStream(it)
            selectedBitmap = BitmapFactory.decodeStream(inputStream)
            binding.btnScreenshot.setImageBitmap(selectedBitmap)
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

        binding.sendEmail.setOnClickListener {
            val emailText = binding.etEmail.text.toString()
            if (emailText.isEmpty() || !emailText.contains("@")) {
                Toast.makeText(this, "Invalid Email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            profileData?.let {
                val pdfFile = getLatestPdf(it)
                if (pdfFile != null) {
                    // Use modified body if available from AI
                    val dataToSend = if (modifiedEmailBody != null) {
                        it.copy(body = modifiedEmailBody!!)
                    } else {
                        it
                    }
                    sendPdfInGmail(pdfFile, dataToSend)
                } else {
                    Toast.makeText(this, "No PDF found", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "No Profile Data Found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        binding.btnProfile.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        binding.btnScreenshot.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnAnalyze.setOnClickListener {
            val jdText = binding.etJdText.text.toString().trim()
            if (jdText.isEmpty() && selectedBitmap == null) {
                Toast.makeText(this, "Please paste JD or select a screenshot", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

//            if (geminiApiKey == "AIzaSyAqMCYh92GXt68XUrW8diLn4rI4MR-CP4c") {
//                Toast.makeText(this, "Please set your Gemini API Key in the code first", Toast.LENGTH_LONG).show()
//                return@setOnClickListener
//            }

            analyzeWithGemini(jdText)
        }
    }

    private fun analyzeWithGemini(jdText: String) {
        val currentProfile = profileData ?: return

        binding.btnAnalyze.text = "Analyzing..."
        binding.btnAnalyze.isEnabled = false

        lifecycleScope.launch {
            val input = if (selectedBitmap != null) selectedBitmap!! else jdText
            val result = geminiManager.analyzeJD(input, currentProfile.resumeText, currentProfile)

            binding.btnAnalyze.text = "Analyze with AI"
            binding.btnAnalyze.isEnabled = true

            if (result != null) {
                binding.etEmail.setText(result.emails.joinToString(", "))
                modifiedEmailBody = result.initialBody
                generatedFollowUp = result.followUpBody
                Toast.makeText(this@SendMailActivity, "Analyzed! Ready to send.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@SendMailActivity, "Analysis failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getLatestPdf(profileData: ProfileData): File? {
        val pdfDir = File(getExternalFilesDir(null), "pdfs")
        if (!pdfDir.exists()) return null

        return pdfDir.listFiles()?.filter {
            it.extension.equals(
                "pdf", true
            ) && it.name.contains(profileData.resumeName)
        }?.maxByOrNull { it.lastModified() }
    }

    private fun sendPdfInGmail(file: File, profileData: ProfileData) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            val emailText = binding.etEmail.text.toString()
            val emails = emailText.split(Regex("[,;]")).map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
            putExtra(Intent.EXTRA_EMAIL, emails)
            putExtra(Intent.EXTRA_SUBJECT, profileData.subject)
            putExtra(Intent.EXTRA_TEXT, profileData.body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        // Save to DB
        val dateSent = System.currentTimeMillis()
        val emailText = binding.etEmail.text.toString()
        val emailsList = emailText.split(Regex("[,;]")).map { it.trim() }.filter { it.isNotEmpty() }
        emailsList.forEach {
            dbHelper.addHistory(it, profileData.subject, dateSent, profileData.body, generatedFollowUp ?: "")
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
