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
import com.example.whatsuit.adapter.RelatedNotificationsAdapter
import com.example.whatsuit.viewmodel.NotificationDetailViewModel
import androidx.fragment.app.viewModels

class NotificationsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var viewModel: NotificationDetailViewModel
    private lateinit var adapter: RelatedNotificationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.relatedNotificationsRecyclerView)
        emptyView = view.findViewById(R.id.emptyView)

        setupRecyclerView()
        setupViewModel()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = RelatedNotificationsAdapter()
        recyclerView.adapter = adapter
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(requireActivity())[NotificationDetailViewModel::class.java]
        
        viewModel.getRelatedNotifications().observe(viewLifecycleOwner) { notifications ->
            if (notifications.isNullOrEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                adapter.setNotifications(notifications)
            }
        }
    }

    companion object {
        fun newInstance() = NotificationsFragment()
    }
}
