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

class ConversationsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var viewModel: NotificationDetailViewModel
    private lateinit var adapter: ConversationHistoryAdapter

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

        setupViewModel()
        setupRecyclerView()
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

    companion object {
        fun newInstance() = ConversationsFragment()
    }
}