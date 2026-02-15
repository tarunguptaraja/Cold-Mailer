package com.tarunguptaraja.coldemailer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tarunguptaraja.coldemailer.databinding.ActivitySendEmailBinding
import java.io.File

class SendMailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySendEmailBinding
    private val profilePrefs: ProfilePreferenceManager by lazy { ProfilePreferenceManager(this) }
    private val profileData by lazy {
        profilePrefs.getProfile()
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
            if (binding.etEmail.text.toString().isEmpty() && !binding.etEmail.text.toString()
                    .contains("@")
            ) {
                Toast.makeText(this, "Invalid Email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            profileData?.let {
                val pdfFile = getLatestPdf(it)
                if (pdfFile != null) {
                    sendPdfInGmail(pdfFile, it)
                } else {
                    Toast.makeText(this, "No PDF found", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "No Profile Data Found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnProfile.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
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
            putExtra(Intent.EXTRA_EMAIL, binding.etEmail.text.toString().split(" ").toTypedArray())
            putExtra(Intent.EXTRA_TEXT, profileData.subject)
            putExtra(Intent.EXTRA_SUBJECT, profileData.body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.google.android.gm")
        }
        startActivity(Intent.createChooser(intent, "Send Email"))
    }
}
