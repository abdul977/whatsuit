package com.example.whatsuit.fragment

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.MenuItem
import android.widget.PopupMenu
import com.example.whatsuit.adapter.HybridNotificationAdapter
import com.example.whatsuit.util.AutoReplyManager
import com.example.whatsuit.MainActivity
import com.example.whatsuit.NotificationDetailActivity
import com.example.whatsuit.R
import com.example.whatsuit.viewmodel.NotificationsViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class NotificationsFragment : Fragment() {
    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var chipGroup: ChipGroup
    private lateinit var notificationAdapter: HybridNotificationAdapter
    private lateinit var emptyView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)

        // Initialize views
        notificationsRecyclerView = view.findViewById(R.id.relatedNotificationsRecyclerView)
        chipGroup = view.findViewById(R.id.appHeaderChipGroup)
        emptyView = view.findViewById(R.id.emptyView)
        
        // Set up RecyclerView with HybridNotificationAdapter
        notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        notificationAdapter = HybridNotificationAdapter(
            requireContext().packageManager,
            AutoReplyManager.getInstance(requireContext())
        )
        notificationsRecyclerView.adapter = notificationAdapter

        // Set up All Apps chip
        val chipAllApps = view.findViewById<Chip>(R.id.chipAllApps)
        var lastSelectedChip: Chip = chipAllApps
        
        viewModel.totalNotificationCount.observe(viewLifecycleOwner) { count ->
            chipAllApps.text = getString(R.string.all_apps_count, count)
        }
        
        chipAllApps.setOnClickListener {
            lastSelectedChip.isChecked = false
            chipAllApps.isChecked = true
            lastSelectedChip = chipAllApps
            viewModel.setSelectedApp(null)
        }

        // Initialize AllApps as selected
        chipAllApps.isChecked = true

        // Observe categorized notifications for RecyclerView updates
        viewModel.categorizedNotifications.observe(viewLifecycleOwner) { notifications ->
            notificationAdapter.updateNotifications(notifications)
            val isEmpty = notifications.isEmpty()
            notificationsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
            emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
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
                    text = getString(R.string.app_with_count, appName, notifications.size)
                    isCheckable = true
                    chipBackgroundColor = chipAllApps.chipBackgroundColor
                    chipStrokeWidth = chipAllApps.chipStrokeWidth
                    chipStrokeColor = chipAllApps.chipStrokeColor
                    isCheckedIconVisible = true
                    chipStartPadding = resources.getDimension(R.dimen.material_chip_spacing)
                    chipEndPadding = resources.getDimension(R.dimen.material_chip_spacing)
                    chipMinHeight = resources.getDimension(R.dimen.chip_min_height)
                    chipCornerRadius = resources.getDimension(R.dimen.chip_corner_radius)
                    
                    // Get app icon from notifications
                    val notification = notifications.firstOrNull()
                    if (notification != null && notification.packageName != null) {
                        try {
                            val packageManager = requireContext().packageManager
                            val icon = packageManager.getApplicationIcon(notification.packageName)
                            chipIcon = icon
                            isChipIconVisible = true
                        } catch (e: PackageManager.NameNotFoundException) {
                            chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_app_placeholder)
                            isChipIconVisible = true
                        }
                    } else {
                        chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_app_placeholder)
                        isChipIconVisible = true
                    }
                    
                    setOnClickListener {
                        lastSelectedChip.isChecked = false
                        isChecked = true
                        lastSelectedChip = this
                        // Get package name from the first notification in this group
                        val notification = notifications.firstOrNull()
                        if (notification != null && notification.packageName != null) {
                            viewModel.setSelectedApp(notification.packageName)
                            // Scroll to show the selected chip
                            post { parent.requestChildFocus(this, this) }
                        }
                    }
                }
                chipGroup.addView(chip)
            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        notificationAdapter.cleanup()
    }
}
