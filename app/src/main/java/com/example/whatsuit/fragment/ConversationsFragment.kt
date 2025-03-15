package com.example.whatsuit.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.R
import com.example.whatsuit.adapter.ConversationHistoryAdapter
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.ConversationHistory
import com.example.whatsuit.service.GeminiService
import com.google.android.material.bottomappbar.BottomAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class ConversationsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConversationHistoryAdapter
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var bottomAppBar: BottomAppBar
    private var conversationId: String? = null
    private var geminiService: GeminiService? = null
    private var isSending = false

    companion object {
        private const val ARG_CONVERSATION_ID = "conversation_id"

        fun newInstance(conversationId: String): ConversationsFragment {
            return ConversationsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONVERSATION_ID, conversationId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            conversationId = it.getString(ARG_CONVERSATION_ID)
        }
        geminiService = context?.let { GeminiService(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_conversations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        recyclerView = view.findViewById(R.id.conversationsRecyclerView)
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        bottomAppBar = view.findViewById(R.id.bottomAppBar)
        adapter = ConversationHistoryAdapter()

        // Setup RecyclerView
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = this@ConversationsFragment.adapter
        }

        // Setup window insets
        setupWindowInsets(view)

        // Setup message input
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isSending) {
                    sendButton.isEnabled = !s.isNullOrBlank()
                }
            }
        })

        // Setup send button
        sendButton.isEnabled = false
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty() && !isSending) {
                setSendingState(true)
                sendMessage(message)
                messageInput.text.clear()
            }
        }

        // Load conversation history
        conversationId?.let { loadConversationHistory(it) }
    }

    private fun setupWindowInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val bottomInset = maxOf(imeInsets.bottom, navigationInsets.bottom)
            
            // Apply insets to bottom bar
            bottomAppBar.updatePadding(bottom = bottomInset)
            
            // Update recycler view padding
            recyclerView.updatePadding(
                bottom = bottomAppBar.height + bottomInset
            )
            
            // Scroll to bottom when keyboard appears
            if (imeInsets.bottom > 0) {
                adapter.currentList?.size?.let { size ->
                    if (size > 0) {
                        recyclerView.smoothScrollToPosition(size - 1)
                    }
                }
            }
            
            insets
        }
    }

    private fun setSendingState(sending: Boolean) {
        isSending = sending
        messageInput.isEnabled = !sending
        sendButton.isEnabled = !sending
        if (sending) {
            sendButton.setImageResource(R.drawable.sending_progress)
        } else {
            sendButton.setImageResource(R.drawable.ic_send)
        }
    }

    private fun sendMessage(message: String) {
        val conversationId = this.conversationId ?: return
        val currentTime = Date().time

        // Create and save user message
        val userMessage = ConversationHistory.create(
            notificationId = 0, // This will be updated when we have the actual notification
            message = message,
            response = "" // Empty response initially
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                db.conversationHistoryDao().insert(userMessage)

                // Get response from Gemini
                geminiService?.generateReply(
                    notificationId = 0,
                    initialMessage = message,
                    callback = object : GeminiService.ResponseCallback {
                        override fun onPartialResponse(text: String) {
                            // Could implement typing animation here
                        }

                        override fun onComplete(response: String) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val assistantMessage = ConversationHistory.create(
                                        notificationId = 0,
                                        message = message,
                                        response = response
                                    )
                                    db.conversationHistoryDao().update(assistantMessage)
                                    
                                    withContext(Dispatchers.Main) {
                                        setSendingState(false)
                                        loadConversationHistory(conversationId)
                                    }
                                } catch (e: Exception) {
                                    handleError(e)
                                }
                            }
                        }

                        override fun onError(error: Throwable) {
                            handleError(error)
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    loadConversationHistory(conversationId)
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private fun handleError(error: Throwable) {
        activity?.runOnUiThread {
            setSendingState(false)
            Toast.makeText(context,
                "Error: ${error.message}",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadConversationHistory(conversationId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val history = AppDatabase.getDatabase(requireContext())
                    .conversationHistoryDao()
                    .getHistoryForConversationSync(conversationId)

                withContext(Dispatchers.Main) {
                    adapter.submitList(history) {
                        if (history.isNotEmpty()) {
                            recyclerView.smoothScrollToPosition(history.size - 1)
                        }
                    }
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        conversationId?.let { loadConversationHistory(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        geminiService = null
    }
}
