package com.example.whatsuit.adapter

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.R
import com.example.whatsuit.util.SearchUtil.SearchResult
import com.example.whatsuit.util.SearchUtil.ResultType
import com.example.whatsuit.util.SearchUtil.NotificationResult
import com.example.whatsuit.util.SearchUtil.ConversationResult
import com.example.whatsuit.util.SearchUtil.TemplateResult

/**
 * Adapter for displaying search results in a RecyclerView
 */
class SearchResultAdapter(
    private val context: Context,
    private val onItemClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, SearchResultAdapter.ViewHolder>(SearchResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = getItem(position)
        holder.bind(result)
    }

    inner class ViewHolder(
        itemView: View,
        private val onItemClick: (SearchResult) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val typeIcon: ImageView = itemView.findViewById(R.id.resultTypeIcon)
        private val title: TextView = itemView.findViewById(R.id.resultTitle)
        private val content: TextView = itemView.findViewById(R.id.resultContent)
        private val timestamp: TextView = itemView.findViewById(R.id.resultTimestamp)
        private val sourceLabel: TextView = itemView.findViewById(R.id.resultSourceLabel)

        fun bind(result: SearchResult) {
            // Set icon based on result type
            val iconRes = when (result.type) {
                ResultType.NOTIFICATION -> R.drawable.ic_notifications_settings
                ResultType.CONVERSATION -> R.drawable.ic_history
                ResultType.TEMPLATE -> R.drawable.ic_edit
            }
            typeIcon.setImageResource(iconRes)

            // Set title and content
            title.text = result.title
            content.text = result.content

            // Format timestamp
            timestamp.text = DateUtils.getRelativeTimeSpanString(
                result.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )

            // Set source label based on result type
            when (result) {
                is NotificationResult -> {
                    sourceLabel.text = result.appName
                    sourceLabel.visibility = View.VISIBLE
                }
                is ConversationResult -> {
                    sourceLabel.text = "Conversation"
                    sourceLabel.visibility = View.VISIBLE
                }
                is TemplateResult -> {
                    sourceLabel.text = "Template"
                    sourceLabel.visibility = View.VISIBLE
                }
            }

            // Set click listener
            itemView.setOnClickListener { onItemClick(result) }
        }
    }

    private class SearchResultDiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem.id == newItem.id && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem.title == newItem.title &&
                   oldItem.content == newItem.content &&
                   oldItem.timestamp == newItem.timestamp
        }
    }

    /**
     * Update the displayed search results
     */
    fun updateSearchResults(results: Map<ResultType, List<SearchResult>>) {
        try {
            // Log incoming results
            results.forEach { (type, items) ->
                Log.d("SearchAdapter", "Results for $type: ${items.size}")
            }

            // Flatten the map into a single list, sorted by timestamp
            val flattenedResults = results.values
                .flatten()
                .sortedByDescending { it.timestamp }

            Log.d("SearchAdapter", "Total flattened results: ${flattenedResults.size}")

            // Log first few results for debugging
            flattenedResults.take(3).forEachIndexed { index, result ->
                Log.d("SearchAdapter", "Result $index: type=${result.type}, title='${result.title}'")
            }

            // Use submitList with a callback to know when the update is complete
            submitList(flattenedResults) {
                Log.d("SearchAdapter", "List update completed")
                // Notify any observers that might need to know about the update
                if (flattenedResults.isNotEmpty()) {
                    Log.d("SearchAdapter", "Notifying item range inserted: 0 to ${flattenedResults.size}")
                    notifyItemRangeChanged(0, flattenedResults.size)
                }
            }
        } catch (e: Exception) {
            Log.e("SearchAdapter", "Error updating search results", e)
            e.printStackTrace()
        }
    }
}
