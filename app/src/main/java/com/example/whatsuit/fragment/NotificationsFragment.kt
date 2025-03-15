package com.example.whatsuit.fragment

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.databinding.FragmentNotificationsBinding
import com.example.whatsuit.AutoReplyProvider
import com.example.whatsuit.R
import com.example.whatsuit.adapter.GroupedNotificationAdapter
import com.example.whatsuit.data.NotificationEntity
import com.example.whatsuit.viewmodel.NotificationsViewModel

class NotificationsFragment : Fragment() {
    private lateinit var binding: FragmentNotificationsBinding
    private lateinit var viewModel: NotificationsViewModel
    private lateinit var adapter: GroupedNotificationAdapter

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
        
        val activity = requireActivity() as? AutoReplyProvider
        activity?.let { setupRecyclerView(it) }
        if (!isNotificationServiceEnabled()) {
            showNotificationAccessDialog()
        }
        setupSearchView()
        observeNotifications()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = requireActivity().packageName
        val flat = android.provider.Settings.Secure.getString(
            requireActivity().contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(packageName) ?: false
    }

    private fun showNotificationAccessDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Notification Access Required")
            .setMessage("Please grant notification access to enable notification monitoring")
            .setPositiveButton("Grant Access") { _, _ ->
                startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            .setNegativeButton("Cancel") { _, _ ->
                requireActivity().finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupRecyclerView(provider: AutoReplyProvider) {
        adapter = GroupedNotificationAdapter(requireActivity().packageManager, provider.getAutoReplyManager())
        with(binding.relatedNotificationsRecyclerView) {
            this.layoutManager = LinearLayoutManager(context)
            this.adapter = this@NotificationsFragment.adapter
            this.setHasFixedSize(true)
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
        viewModel.categorizedNotifications.observe(viewLifecycleOwner) { categoryMap ->
            val notifications = categoryMap.values.flatten()
            adapter.updateNotifications(notifications)
            binding.emptyView.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
            binding.relatedNotificationsRecyclerView.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        fun newInstance() = NotificationsFragment()
    }
}
