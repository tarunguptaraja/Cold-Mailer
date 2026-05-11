package com.tarunguptaraja.coldemailer.presentation.interview.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tarunguptaraja.coldemailer.R
import com.tarunguptaraja.coldemailer.domain.model.QuestionAnalysis

class QuestionAnalysisAdapter : ListAdapter<QuestionAnalysis, QuestionAnalysisAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_question_analysis, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvQuestionNumber: TextView = itemView.findViewById(R.id.tv_question_number)
        private val tvScore: TextView = itemView.findViewById(R.id.tv_score)
        private val tvFeedback: TextView = itemView.findViewById(R.id.tv_feedback)
        private val tvSuggestedAnswer: TextView = itemView.findViewById(R.id.tv_suggested_answer)

        fun bind(analysis: QuestionAnalysis, questionNumber: Int) {
            tvQuestionNumber.text = "Question $questionNumber"
            tvScore.text = "${analysis.score}/100"
            
            // Set score color based on value
            val scoreColor = when {
                analysis.score >= 80 -> android.graphics.Color.parseColor("#4CAF50") // Green
                analysis.score >= 60 -> android.graphics.Color.parseColor("#FF9800") // Orange
                else -> android.graphics.Color.parseColor("#F44336") // Red
            }
            tvScore.setTextColor(scoreColor)
            
            tvFeedback.text = analysis.feedback
            tvSuggestedAnswer.text = "Suggested: ${analysis.suggestedAnswer}"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<QuestionAnalysis>() {
        override fun areItemsTheSame(oldItem: QuestionAnalysis, newItem: QuestionAnalysis): Boolean {
            return oldItem.questionId == newItem.questionId
        }

        override fun areContentsTheSame(oldItem: QuestionAnalysis, newItem: QuestionAnalysis): Boolean {
            return oldItem == newItem
        }
    }
}
