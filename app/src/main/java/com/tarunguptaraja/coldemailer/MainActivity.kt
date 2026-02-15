package com.tarunguptaraja.coldemailer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tarunguptaraja.coldemailer.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var profilePrefs: ProfilePreferenceManager
    var fileName: String = "resume.pdf"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        profilePrefs = ProfilePreferenceManager(this)
        setContentView(binding.root)

        loadSavedProfile()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.uploadResume.setOnClickListener {
            if (binding.etName.text.isNullOrEmpty()) {
                Toast.makeText(this, "Please enter your name first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openPdfPicker()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvSave.setOnClickListener {
            val data = ProfileData(
                binding.etName.text.toString().trim(),
                binding.etSubject.text.toString().trim(),
                binding.etBody.text.toString().trim(),
                fileName
            )
            profilePrefs.saveProfile(data)
            Toast.makeText(this, "Profile saved successfully", Toast.LENGTH_SHORT).show()
            goToSendScreen()
        }
    }

    private fun loadSavedProfile() {
        binding.etName.setText(profilePrefs.getName())
        binding.etSubject.setText(profilePrefs.getSubject())
        binding.etBody.setText(profilePrefs.getBody())
        val savedResumeName = profilePrefs.getResumeName()
        if (savedResumeName.isNotEmpty()) {
            fileName = savedResumeName
            binding.tvResume.text = savedResumeName
        }
    }

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            savePdfToAppStorage(it)
        }
    }

    private fun openPdfPicker() {
        pickPdfLauncher.launch(arrayOf("application/pdf"))
    }

    private fun savePdfToAppStorage(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            val pdfDir = File(getExternalFilesDir(null), "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            fileName = "${binding.etName.text.toString().trim().replace(" ", "_")}_Resume.pdf"

            // Also save the filename to preferences
            profilePrefs.setResumeName(fileName)

            val pdfFile = File(pdfDir, fileName)

            val outputStream = FileOutputStream(pdfFile)
            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()
            binding.tvResume.text = fileName
            Toast.makeText(this, "Saved:\n${pdfFile.name}", Toast.LENGTH_SHORT).show()
            pdfFile
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun goToSendScreen() {
        val intent = Intent(this, SendMailActivity::class.java)
        startActivity(intent)
    }
}