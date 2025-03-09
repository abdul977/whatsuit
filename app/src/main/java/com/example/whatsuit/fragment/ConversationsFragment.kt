package com.example.whatsuit.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.R
import com.example.whatsuit.adapter.ConversationHistoryAdapter
import com.example.whatsuit.data.ConversationHistory
import com.example.whatsuit.viewmodel.NotificationDetailViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class ConversationsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var viewModel: NotificationDetailViewModel
    private lateinit var adapter: ConversationHistoryAdapter
    private lateinit var fab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_conversations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.conversationsRecyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        fab = view.findViewById(R.id.fab)

        setupViewModel()
        setupRecyclerView()
        setupFab()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ConversationHistoryAdapter(
            onEdit = { conversation, newMessage, newResponse ->
                viewModel.editConversation(conversation, newMessage, newResponse)
            }
        )
        recyclerView.adapter = adapter
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(requireActivity())[NotificationDetailViewModel::class.java]
        
        viewModel.getConversations().observe(viewLifecycleOwner) { conversations ->
            if (conversations.isNullOrEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                adapter.updateConversations(conversations)
            }
        }
    }

    private fun setupFab() {
        fab.setOnClickListener {
            showAddConversationDialog()
        }
    }

    private fun showAddConversationDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_conversation, null)

        val messageInput = dialogView.findViewById<TextInputEditText>(R.id.messageEditText)
        val responseInput = dialogView.findViewById<TextInputEditText>(R.id.responseEditText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_conversation)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val newMessage = messageInput.text?.toString()
                val newResponse = responseInput.text?.toString()

                if (!newMessage.isNullOrBlank() && !newResponse.isNullOrBlank()) {
                    val newConversation = ConversationHistory(
                        message = newMessage,
                        response = newResponse,
                        notificationId = 0, // Replace with actual notification ID
                        conversationId = "", // Replace with actual conversation ID
                        timestamp = System.currentTimeMillis()
                    )
                    viewModel.addConversation(newConversation)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    companion object {
        fun newInstance() = ConversationsFragment()
    }
}
