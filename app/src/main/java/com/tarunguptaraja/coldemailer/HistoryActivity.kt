package com.tarunguptaraja.coldemailer

import android.content.Intent
import com.tarunguptaraja.coldemailer.R

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
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        observeState()
    }
    
    private fun setupRecyclerView() {
        adapter = HistoryAdapter({ historyItem ->
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
                    adapter.submitList(state.historyList)
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
        val options = arrayOf("Update Status", getString(R.string.opt_send_followup), getString(R.string.opt_delete))
        AlertDialog.Builder(this)
            .setTitle(historyItem.email)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showStatusDialog(historyItem)
                    1 -> sendFollowUp(historyItem)
                    2 -> viewModel.deleteHistory(historyItem.id)
                }
            }
            .show()
    }

    private fun showStatusDialog(historyItem: EmailHistory) {
        val statuses = arrayOf("Applied", "Followed_Up", "Interviewing", "Rejected", "Offer")
        AlertDialog.Builder(this)
            .setTitle("Update Status")
            .setItems(statuses) { _, which ->
                viewModel.updateStatus(historyItem.id, statuses[which])
            }
            .show()
    }
    
    private fun sendFollowUp(historyItem: EmailHistory) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") 
            putExtra(Intent.EXTRA_EMAIL, arrayOf(historyItem.email))
            putExtra(Intent.EXTRA_SUBJECT, "Re: ${historyItem.subject}")
            val followUpText = historyItem.followUp.ifEmpty {
                getString(R.string.default_followup)
            }
            putExtra(Intent.EXTRA_TEXT, followUpText)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.err_no_email_app), Toast.LENGTH_SHORT).show()
        }
    }
}
