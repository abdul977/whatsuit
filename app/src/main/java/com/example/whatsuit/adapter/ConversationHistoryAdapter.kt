package com.example.whatsuit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.R
import com.example.whatsuit.data.ConversationHistory
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationHistoryAdapter(
    private val onEdit: (ConversationHistory, String, String) -> Unit
) : RecyclerView.Adapter<ConversationHistoryAdapter.ViewHolder>() {

    private var conversations = listOf<ConversationHistory>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    fun updateConversations(newConversations: List<ConversationHistory>) {
        conversations = newConversations
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.bind(conversation)
    }

    override fun getItemCount() = conversations.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val responseText: TextView = itemView.findViewById(R.id.responseText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val editMessageButton: ImageButton = itemView.findViewById(R.id.editMessageButton)
        private val editResponseButton: ImageButton = itemView.findViewById(R.id.editResponseButton)

        fun bind(conversation: ConversationHistory) {
            messageText.text = conversation.message
            responseText.text = conversation.response
            timestampText.text = dateFormat.format(Date(conversation.timestamp))
            
            if (conversation.isModified) {
                timestampText.text = "${timestampText.text} (edited)"
            }

            editMessageButton.setOnClickListener {
                showEditDialog(conversation, true)
            }

            editResponseButton.setOnClickListener {
                showEditDialog(conversation, false)
            }
        }

        private fun showEditDialog(conversation: ConversationHistory, isMessage: Boolean) {
            val context = itemView.context
            val dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_edit_conversation, null)

            val messageInput = dialogView.findViewById<TextInputEditText>(R.id.messageEditText)
            val responseInput = dialogView.findViewById<TextInputEditText>(R.id.responseEditText)

            messageInput.setText(conversation.message)
            responseInput.setText(conversation.response)

            // If editing only message or response, disable the other field
            if (isMessage) {
                responseInput.isEnabled = false
            } else {
                messageInput.isEnabled = false
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle(R.string.edit_conversation)
                .setView(dialogView)
                .create()

            // Use buttons from the dialog layout
            dialogView.findViewById<View>(R.id.saveButton).setOnClickListener {
                val newMessage = messageInput.text?.toString()
                val newResponse = responseInput.text?.toString()

                val validationResult = conversation.validateEdit(
                    newMessage = if (isMessage) newMessage else conversation.message,
                    newResponse = if (isMessage) conversation.response else newResponse
                )

                if (validationResult.first) {
                    onEdit(
                        conversation,
                        if (isMessage) newMessage!! else conversation.message,
                        if (isMessage) conversation.response else newResponse!!
                    )
                    dialog.dismiss()
                } else {
                    dialog.dismiss()
                    // Show error message
                    AlertDialog.Builder(context)
                        .setMessage(validationResult.second)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }

            dialogView.findViewById<View>(R.id.cancelButton).setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }
}
