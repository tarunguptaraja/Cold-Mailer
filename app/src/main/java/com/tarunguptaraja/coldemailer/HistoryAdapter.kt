package com.tarunguptaraja.coldemailer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tarunguptaraja.coldemailer.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HistoryAdapter(
    private var historyList: List<EmailHistory>,
    private val onItemClick: (EmailHistory) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val emailHistory = historyList[position]
        holder.binding.tvEmail.text = emailHistory.email
        holder.binding.tvSubject.text = emailHistory.subject

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
    }

    override fun getItemCount(): Int = historyList.size
    
    fun updateData(newList: List<EmailHistory>) {
        historyList = newList
        notifyDataSetChanged()
    }
}
