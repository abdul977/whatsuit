package com.example.whatsuit.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsuit.AppHeaderAdapter
import com.example.whatsuit.NotificationAdapter
import com.example.whatsuit.R
import com.example.whatsuit.util.FallDownItemAnimator
import com.example.whatsuit.viewmodel.NotificationsViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class NotificationsFragment : Fragment() {
    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var appHeaderAdapter: AppHeaderAdapter
    private lateinit var emptyView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyView = view.findViewById(R.id.emptyView)
        setupRecyclerViews()
        setupFilterChips()
        setupSortButton()
        observeData()
    }

    private fun setupRecyclerViews() {
        // Set up notifications recycler view
        notificationAdapter = NotificationAdapter()
        view?.findViewById<RecyclerView>(R.id.notificationsRecyclerView)?.apply {
            adapter = notificationAdapter
            layoutManager = LinearLayoutManager(context)
            itemAnimator = FallDownItemAnimator()
            FallDownItemAnimator.setAnimation(this)
        }

        // Set up app header recycler view
        appHeaderAdapter = AppHeaderAdapter(requireContext().packageManager)
        view?.findViewById<RecyclerView>(R.id.appHeaderRecyclerView)?.apply {
            adapter = appHeaderAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            itemAnimator = FallDownItemAnimator()
            FallDownItemAnimator.setAnimation(this)
        }

        // Handle app header clicks
        appHeaderAdapter.setOnAppHeaderClickListener { header ->
            viewModel.setSelectedApp(if (header.packageName.isEmpty()) null else header.packageName)
        }
    }

    private fun setupFilterChips() {
        val timeFilterChipGroup = view?.findViewById<ChipGroup>(R.id.timeFilterChipGroup)
        val sortOrderGroup = view?.findViewById<ChipGroup>(R.id.sortOrderGroup)

        // Handle time filter clicks
        timeFilterChipGroup?.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val filter = when (checkedIds[0]) {
                R.id.todayChip -> "today"
                R.id.yesterdayChip -> "yesterday"
                else -> "all"
            }
            viewModel.setTimeFilter(filter)
        }

        // Handle sort order changes
        sortOrderGroup?.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId != -1) {
                val sortOrder = if (checkedId == R.id.sort_latest) "latest" else "most_conversations"
                viewModel.setSortOrder(sortOrder)
            }
        }
    }

    private fun setupSortButton() {
        val sortButton = view?.findViewById<android.widget.ImageButton>(R.id.sortButton)
        sortButton?.setOnClickListener { button ->
            showSortingMenu(button)
        }
    }

    private fun showSortingMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.sort_menu, menu)
            
            // Update the checked state based on current sort order
            when (viewModel.selectedSortOrder.value) {
                "latest" -> menu.findItem(R.id.sort_latest).isChecked = true
                "most_conversations" -> menu.findItem(R.id.sort_most_conversations).isChecked = true
            }

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.sort_latest -> {
                        viewModel.setSortOrder("latest")
                        true
                    }
                    R.id.sort_most_conversations -> {
                        viewModel.setSortOrder("most_conversations")
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun observeData() {
        viewModel.categorizedNotifications.observe(viewLifecycleOwner) { categorizedNotifications ->
            if (categorizedNotifications != null && categorizedNotifications.isNotEmpty()) {
                // Update app headers
                val headers = mutableListOf(AppHeaderAdapter.AppHeader("", "All Apps", 0))
                var totalNotifications = 0
                
                categorizedNotifications.forEach { (appName, notifications) ->
                    if (notifications.isNotEmpty()) {
                        val firstNotification = notifications.first()
                        headers.add(AppHeaderAdapter.AppHeader(
                            firstNotification.packageName,
                            appName,
                            notifications.size
                        ))
                        totalNotifications += notifications.size
                    }
                }
                
                // Update "All Apps" count with positional arguments (packageName, appName, count)
                headers[0] = headers[0].copy(headers[0].packageName, headers[0].appName, totalNotifications)
                appHeaderAdapter.setHeaders(headers)

                // Update notifications list
                val allNotifications = categorizedNotifications.values.flatten()
                notificationAdapter.setAllNotifications(allNotifications)
                
                emptyView.visibility = View.GONE
                view?.findViewById<RecyclerView>(R.id.notificationsRecyclerView)?.visibility = View.VISIBLE
            } else {
                appHeaderAdapter.setHeaders(listOf(AppHeaderAdapter.AppHeader("", "All Apps", 0)))
                notificationAdapter.setAllNotifications(emptyList())
                emptyView.visibility = View.VISIBLE
                view?.findViewById<RecyclerView>(R.id.notificationsRecyclerView)?.visibility = View.GONE
            }
        }
    }
}
