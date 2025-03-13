package com.example.whatsuit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.R
import com.example.whatsuit.data.ConversationHistory
import java.text.SimpleDateFormat
import java.util.Locale

class ConversationHistoryAdapter : ListAdapter<ConversationHistory, RecyclerView.ViewHolder>(ConversationDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_conversation, parent, false)
                UserMessageViewHolder(view)
            }
            VIEW_TYPE_ASSISTANT -> {
                val view = inflater.inflate(R.layout.item_assistant_conversation, parent, false)
                AssistantMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> {
                holder.bind(item.message)
                holder.bindTimestamp(item.timestamp)
            }
            is AssistantMessageViewHolder -> {
                holder.bind(item.response)
                holder.bindTimestamp(item.timestamp)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        // If position is even, it's a user message, otherwise it's an assistant response
        return if (position % 2 == 0) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContent: TextView = itemView.findViewById(R.id.messageContent)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(message: String?) {
            messageContent.text = message ?: ""
        }

        fun bindTimestamp(time: Long?) {
            timestamp.text = time?.let { dateFormat.format(it) } ?: ""
        }
    }

    class AssistantMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContent: TextView = itemView.findViewById(R.id.messageContent)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(message: String?) {
            messageContent.text = message ?: ""
        }

        fun bindTimestamp(time: Long?) {
            timestamp.text = time?.let { dateFormat.format(it) } ?: ""
        }
    }
}

private class ConversationDiffCallback : DiffUtil.ItemCallback<ConversationHistory>() {
    override fun areItemsTheSame(oldItem: ConversationHistory, newItem: ConversationHistory): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ConversationHistory, newItem: ConversationHistory): Boolean {
        return oldItem == newItem
    }
}
