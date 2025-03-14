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
import com.example.whatsuit.adapter.NotificationAdapter
import com.example.whatsuit.databinding.FragmentNotificationsBinding
import com.example.whatsuit.viewmodel.NotificationsViewModel

class NotificationsFragment : Fragment() {
    private lateinit var binding: FragmentNotificationsBinding
    private lateinit var viewModel: NotificationsViewModel
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[NotificationsViewModel::class.java]
        
        setupRecyclerView()
        setupSearchView()
        observeNotifications()
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@NotificationsFragment.adapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { viewModel.setSearchQuery(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { viewModel.setSearchQuery(it) }
                return true
            }
        })
    }

    private fun observeNotifications() {
        viewModel.filteredNotifications.observe(viewLifecycleOwner) { notifications ->
            adapter.submitList(notifications)
            binding.emptyView.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        fun newInstance() = NotificationsFragment()
    }
}
