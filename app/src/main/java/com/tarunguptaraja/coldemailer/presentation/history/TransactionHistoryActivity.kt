package com.tarunguptaraja.coldemailer.presentation.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.R
import com.tarunguptaraja.coldemailer.databinding.ActivityTransactionHistoryBinding
import com.tarunguptaraja.coldemailer.databinding.ItemTokenTransactionBinding
import com.tarunguptaraja.coldemailer.domain.model.TokenTransaction
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionHistoryBinding

    @Inject
    lateinit var profilePreferenceManager: ProfilePreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val transactions = profilePreferenceManager.getTransactions()
        if (transactions.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvTransactions.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvTransactions.visibility = View.VISIBLE
            binding.rvTransactions.layoutManager = LinearLayoutManager(this)
            binding.rvTransactions.adapter = TransactionAdapter(transactions)
        }
    }

    class TransactionAdapter(private val transactions: List<TokenTransaction>) :
        RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemTokenTransactionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTokenTransactionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tx = transactions[position]
            val binding = holder.binding

            binding.tvDescription.text = tx.description
            binding.tvDate.text = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(Date(tx.timestamp))

            if (tx.type == "AWARD") {
                binding.tvAmount.text = "+%,d".format(tx.amount)
                binding.tvAmount.setTextColor(binding.root.context.getColor(android.R.color.holo_green_dark))
            } else {
                binding.tvAmount.text = "-%,d".format(tx.amount)
                binding.tvAmount.setTextColor(binding.root.context.getColor(R.color.colorError))
            }
        }

        override fun getItemCount() = transactions.size
    }
}
