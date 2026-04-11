package com.tarunguptaraja.coldemailer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.tarunguptaraja.coldemailer.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val dbHelper by lazy { DatabaseHelper(this) }
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
        loadHistory()
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

    private fun loadHistory() {
        val historyList = dbHelper.getAllHistory()
        adapter.updateData(historyList)
    }
    
    private fun showOptionsDialog(historyItem: EmailHistory) {
        val options = arrayOf("Send Follow-up Email", "Delete from History")
        AlertDialog.Builder(this)
            .setTitle(historyItem.email)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sendFollowUp(historyItem)
                    1 -> {
                        dbHelper.deleteHistory(historyItem.id)
                        loadHistory()
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
            
            dbHelper.addHistory(historyItem.email, "Re: ${historyItem.subject}", System.currentTimeMillis(), historyItem.body, historyItem.followUp)
            loadHistory()
            
        } catch (e: Exception) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
}
