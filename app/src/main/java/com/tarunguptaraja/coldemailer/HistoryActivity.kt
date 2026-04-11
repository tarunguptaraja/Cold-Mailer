package com.tarunguptaraja.coldemailer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tarunguptaraja.coldemailer.databinding.ActivityHistoryBinding
import com.tarunguptaraja.coldemailer.domain.model.EmailHistory
import com.tarunguptaraja.coldemailer.presentation.history.HistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        observeState()
    }
    
    private fun setupRecyclerView() {
        adapter = HistoryAdapter(emptyList(), { historyItem ->
            showOptionsDialog(historyItem)
        }, { historyItem ->
            sendFollowUp(historyItem)
        })
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.updateData(state.historyList)
                    if (state.historyList.isEmpty()) {
                        binding.emptyState.visibility = android.view.View.VISIBLE
                        binding.recyclerHistory.visibility = android.view.View.GONE
                    } else {
                        binding.emptyState.visibility = android.view.View.GONE
                        binding.recyclerHistory.visibility = android.view.View.VISIBLE
                    }
                }
            }
        }
    }
    
    private fun showOptionsDialog(historyItem: EmailHistory) {
        val options = arrayOf("Send Follow-up Email", "Delete from History")
        AlertDialog.Builder(this)
            .setTitle(historyItem.email)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sendFollowUp(historyItem)
                    1 -> {
                        viewModel.deleteHistory(historyItem.id)
                    }
                }
            }
            .show()
    }
    
    private fun sendFollowUp(historyItem: EmailHistory) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") 
            putExtra(Intent.EXTRA_EMAIL, arrayOf(historyItem.email))
            putExtra(Intent.EXTRA_SUBJECT, "Re: ${historyItem.subject}")
            val followUpText = if (historyItem.followUp.isNotEmpty()) {
                historyItem.followUp
            } else {
                "Hi,\n\nJust following up on my previous email. I would love to connect soon!\n\nThanks."
            }
            putExtra(Intent.EXTRA_TEXT, followUpText)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
}
