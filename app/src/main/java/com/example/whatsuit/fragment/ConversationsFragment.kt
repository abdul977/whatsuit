package com.example.whatsuit.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.R
import com.example.whatsuit.adapter.GroupedNotificationAdapter
import com.example.whatsuit.util.FallDownItemAnimator

class ConversationsFragment : Fragment() {
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var adapter: GroupedNotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_conversations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView(view)
    }

    private fun setupRecyclerView(view: View) {
        conversationsRecyclerView = view.findViewById(R.id.conversationsRecyclerView)
        
        // Use existing GroupedNotificationAdapter for consistency
        adapter = GroupedNotificationAdapter(requireActivity().packageManager, null)
        
        conversationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = FallDownItemAnimator()
            FallDownItemAnimator.setAnimation(this)
            adapter = this@ConversationsFragment.adapter
        }
    }

    companion object {
        fun newInstance() = ConversationsFragment()
    }
}
