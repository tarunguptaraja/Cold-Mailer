package com.tarunguptaraja.coldemailer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import com.tarunguptaraja.coldemailer.databinding.ActivityMainBinding
import com.tarunguptaraja.coldemailer.presentation.profile.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.etName.doAfterTextChanged { viewModel.onNameChanged(it.toString()) }
        binding.etSubject.doAfterTextChanged { viewModel.onSubjectChanged(it.toString()) }
        binding.etBody.doAfterTextChanged { viewModel.onBodyChanged(it.toString()) }

        binding.uploadResume.setOnClickListener {
            if (binding.etName.text.isNullOrEmpty()) {
                Toast.makeText(this, getString(R.string.err_enter_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openPdfPicker()
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.tvSave.setOnClickListener {
            viewModel.saveProfile()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (binding.etName.text.toString() != state.name) binding.etName.setText(state.name)
                    if (binding.etSubject.text.toString() != state.subject) binding.etSubject.setText(state.subject)
                    if (binding.etBody.text.toString() != state.body) binding.etBody.setText(state.body)
                    
                    binding.tvResume.text = state.resumeName
                    
                    state.message?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessage()
                    }
                    
                    if (state.isProfileSaved) {
                        goToSendScreen()
                    }
                }
            }
        }
    }

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = savePdfToAppStorage(it)
            if (fileName != null) {
                viewModel.onResumeSelected(it, fileName)
            }
        }
    }

    private fun openPdfPicker() {
        pickPdfLauncher.launch(arrayOf("application/pdf"))
    }

    private fun savePdfToAppStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val pdfDir = File(getExternalFilesDir(null), "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val fileName = "${binding.etName.text.toString().trim().replace(" ", "_")}_Resume.pdf"
            val pdfFile = File(pdfDir, fileName)

            val outputStream = FileOutputStream(pdfFile)
            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()
            fileName
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.err_save_pdf), Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun goToSendScreen() {
        val intent = Intent(this, SendMailActivity::class.java)
        startActivity(intent)
    }
}