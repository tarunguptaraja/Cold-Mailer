package com.tarunguptaraja.coldemailer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tarunguptaraja.coldemailer.databinding.ItemHistoryBinding
import com.tarunguptaraja.coldemailer.domain.model.EmailHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HistoryAdapter(
    private val onItemClick: (EmailHistory) -> Unit,
    private val onFollowUpClick: (EmailHistory) -> Unit
) : ListAdapter<EmailHistory, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    inner class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val emailHistory = getItem(position)
        holder.binding.tvEmail.text = emailHistory.email
        holder.binding.tvSubject.text = emailHistory.subject
        
        val company = emailHistory.companyName.ifEmpty { "Unknown Company" }
        val role = emailHistory.roleName.ifEmpty { "Unknown Role" }
        holder.binding.tvCompanyRole.text = "$company - $role"
        holder.binding.tvStatus.text = emailHistory.status

        val date = Date(emailHistory.dateSent)
        val format = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
        holder.binding.tvDate.text = format.format(date)

        val diffInMillis = System.currentTimeMillis() - emailHistory.dateSent
        val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)

        if (diffInDays >= 7) {
            holder.binding.tvFollowUp.visibility = View.VISIBLE
        } else {
            holder.binding.tvFollowUp.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(emailHistory)
        }
        
        holder.binding.tvFollowUp.setOnClickListener {
            onFollowUpClick(emailHistory)
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<EmailHistory>() {
        override fun areItemsTheSame(oldItem: EmailHistory, newItem: EmailHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: EmailHistory, newItem: EmailHistory): Boolean {
            return oldItem == newItem
        }
    }
}
