package com.example.whatsuit.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.NotificationAdapter
import com.example.whatsuit.R
import com.example.whatsuit.viewmodel.NotificationsViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class NotificationsFragment : Fragment() {
    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var chipGroup: ChipGroup
    private lateinit var notificationAdapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)

        // Initialize views
        notificationsRecyclerView = view.findViewById(R.id.relatedNotificationsRecyclerView)
        chipGroup = view.findViewById(R.id.appHeaderChipGroup)
        
        // Set up RecyclerView
        notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        notificationAdapter = NotificationAdapter()
        notificationsRecyclerView.adapter = notificationAdapter

        // Set up All Apps chip
        val chipAllApps = view.findViewById<Chip>(R.id.chipAllApps)
        chipAllApps.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.filteredNotifications.observe(viewLifecycleOwner) { notifications ->
                    notificationAdapter.setNotifications(notifications)
                }
            }
        }

        // Observe categorized notifications and create app filter chips
        viewModel.categorizedNotifications.observe(viewLifecycleOwner) { categorizedNotifications ->
            // Remove existing dynamic chips (keep "All Apps" chip)
            for (i in chipGroup.childCount - 1 downTo 1) {
                chipGroup.removeViewAt(i)
            }

            // Add chip for each app
            categorizedNotifications.forEach { (appName, notifications) ->
                val chip = Chip(requireContext()).apply {
                    text = appName
                    isCheckable = true
                    chipBackgroundColor = chipAllApps.chipBackgroundColor
                    chipStrokeWidth = chipAllApps.chipStrokeWidth
                    chipStrokeColor = chipAllApps.chipStrokeColor
                    isCheckedIconVisible = true
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Chip)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            notificationAdapter.setNotifications(notifications)
                        }
                    }
                }
                chipGroup.addView(chip)
            }
        }

        return view
    }
}
