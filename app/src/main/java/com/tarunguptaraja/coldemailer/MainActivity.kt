package com.tarunguptaraja.coldemailer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
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
import com.tarunguptaraja.coldemailer.databinding.ItemJobRoleBinding
import com.tarunguptaraja.coldemailer.domain.model.JobRole
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.etName.doAfterTextChanged { viewModel.onNameChanged(it.toString()) }
        
        // Role Editor listeners
        binding.etRoleName.doAfterTextChanged { viewModel.onRoleNameChanged(it.toString()) }
        binding.etSubject.doAfterTextChanged { viewModel.onSubjectChanged(it.toString()) }
        binding.etBody.doAfterTextChanged { viewModel.onBodyChanged(it.toString()) }

        binding.btnAddRole.setOnClickListener {
            viewModel.addNewRole()
        }

        binding.uploadResume.setOnClickListener {
            openPdfPicker()
        }

        binding.btnSaveRole.setOnClickListener {
            viewModel.saveCurrentRole()
        }

        binding.btnCancelRole.setOnClickListener {
            viewModel.cancelEdit()
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.tvSave.setOnClickListener {
            if (viewModel.uiState.value.roles.isEmpty()) {
                Toast.makeText(this, "Please add at least one job role profile", Toast.LENGTH_SHORT).show()
            } else {
                goToSendScreen()
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Global info
                    if (binding.etName.text.toString() != state.name) binding.etName.setText(state.name)
                    
                    // Role List
                    renderRoles(state.roles)
                    
                    // Editor Visibility
                    val isEditing = state.currentRoleId != null
                    binding.roleEditor.visibility = if (isEditing) View.VISIBLE else View.GONE
                    binding.editorBottomPanel.visibility = if (isEditing) View.VISIBLE else View.GONE
                    binding.roleListContainer.visibility = if (isEditing) View.GONE else View.VISIBLE
                    binding.bottomPanel.visibility = if (isEditing) View.GONE else View.VISIBLE
                    
                    // Role Editor Data
                    if (isEditing) {
                        binding.tvEditorTitle.text = if (state.roles.any { it.id == state.currentRoleId }) "Edit Role" else "Create New Role"
                        if (binding.etRoleName.text.toString() != state.currentRoleName) binding.etRoleName.setText(state.currentRoleName)
                        if (binding.etSubject.text.toString() != state.currentSubject) binding.etSubject.setText(state.currentSubject)
                        if (binding.etBody.text.toString() != state.currentBody) binding.etBody.setText(state.currentBody)
                        binding.tvResume.text = if (state.currentResumeName.isEmpty()) "No Resume Selected" else state.currentResumeName
                    }

                    binding.progressLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    
                    state.message?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessage()
                    }
                }
            }
        }
    }

    private fun renderRoles(roles: List<JobRole>) {
        binding.llRolesList.removeAllViews()
        roles.forEach { role ->
            val roleBinding = ItemJobRoleBinding.inflate(layoutInflater, binding.llRolesList, false)
            roleBinding.tvRoleName.text = role.roleName
            roleBinding.tvResumeInfo.text = "Resume: ${role.resumeFileName}"
            
            roleBinding.btnEdit.setOnClickListener { viewModel.editRole(role) }
            roleBinding.btnDelete.setOnClickListener { viewModel.deleteRole(role.id) }
            
            binding.llRolesList.addView(roleBinding.root)
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
            val originalFileName = getFileName(uri) ?: "Resume_${System.currentTimeMillis()}.pdf"
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val pdfDir = File(getExternalFilesDir(null), "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val pdfFile = File(pdfDir, originalFileName)
            val outputStream = FileOutputStream(pdfFile)
            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()
            originalFileName
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
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

    private fun goToSendScreen() {
        val intent = Intent(this, SendMailActivity::class.java)
        startActivity(intent)
    }
}