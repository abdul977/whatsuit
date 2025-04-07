package com.example.whatsuit.fragment

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.example.whatsuit.adapter.HybridNotificationAdapter
import com.example.whatsuit.util.AutoReplyManager
import com.example.whatsuit.MainActivity
import com.example.whatsuit.NotificationDetailActivity
import com.example.whatsuit.R
import com.example.whatsuit.viewmodel.NotificationsViewModel
import com.example.whatsuit.util.SearchUtil
import com.example.whatsuit.data.NotificationEntity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import android.util.Log

class NotificationsFragment : Fragment() {
    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var chipGroup: ChipGroup
    private lateinit var notificationAdapter: HybridNotificationAdapter
    private lateinit var searchAdapter: HybridNotificationAdapter
    private lateinit var emptyView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)

        // Initialize views
        notificationsRecyclerView = view.findViewById(R.id.relatedNotificationsRecyclerView)
        searchResultsRecyclerView = view.findViewById(R.id.searchResultsRecyclerView)
        chipGroup = view.findViewById(R.id.appHeaderChipGroup)
        emptyView = view.findViewById<TextView>(R.id.emptyView)

        // Set up notifications RecyclerView
        notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        notificationAdapter = HybridNotificationAdapter(
            requireContext().packageManager,
            AutoReplyManager.getInstance(requireContext())
        )
        notificationsRecyclerView.adapter = notificationAdapter

        // Set up search results RecyclerView - use a separate instance of the same adapter type
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        // Make sure the RecyclerView has a fixed size for better performance
        searchResultsRecyclerView.setHasFixedSize(true)
        // Add item decoration for better spacing
        searchResultsRecyclerView.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(
                requireContext(),
                LinearLayoutManager.VERTICAL
            )
        )

        // Create a separate adapter instance for search results
        searchAdapter = HybridNotificationAdapter(
            requireContext().packageManager,
            AutoReplyManager.getInstance(requireContext())
        )
        searchResultsRecyclerView.adapter = searchAdapter

        // Force initial visibility to ensure proper layout
        searchResultsRecyclerView.visibility = View.GONE

        // Log for debugging
        Log.d("NotificationsFragment", "Search RecyclerView setup complete with separate HybridAdapter instance")
        Log.d("SearchDebug", "Search adapter: $searchAdapter, Notification adapter: $notificationAdapter")

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

        // Observe filtered notifications
        viewModel.filteredNotifications.observe(viewLifecycleOwner) { notifications ->
            val groupedNotifications = notifications.groupBy { it.appName ?: "Unknown" }.toSortedMap()
            notificationAdapter.updateNotifications(groupedNotifications)

            // Update app filter chips
            updateAppFilterChips(groupedNotifications, chipAllApps)

            // Update visibility
            updateViewVisibility()
        }

        // Observe search results
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            Log.d("NotificationsFragment", "Received search results update")

            // Count total results for better logging
            val totalResults = results.values.sumOf { it.size }
            Log.d("NotificationsFragment", "Total search results: $totalResults")

            // Log individual result counts
            results.forEach { (type, items) ->
                Log.d("NotificationsFragment", "Search results for $type: ${items.size}")
                if (items.isNotEmpty()) {
                    Log.d("NotificationsFragment", "First result: ${items.first().title}")
                }
            }

            // Update adapter with the search results
            searchAdapter.updateWithSearchResults(results)
            Log.d("SearchDebug", "Updated search adapter with results")

            // Force visibility update immediately
            if (results.any { it.value.isNotEmpty() }) {
                Log.d("SearchDebug", "Search results found, making search RecyclerView visible")

                // Make search results visible immediately
                searchResultsRecyclerView.visibility = View.VISIBLE
                notificationsRecyclerView.visibility = View.GONE
                emptyView.visibility = View.GONE

                // Log the current state
                Log.d("SearchDebug", "Current visibility states: " +
                    "searchResults=${searchResultsRecyclerView.visibility == View.VISIBLE}, " +
                    "notifications=${notificationsRecyclerView.visibility == View.VISIBLE}, " +
                    "empty=${emptyView.visibility == View.VISIBLE}")

                // Then update all visibility states
                updateViewVisibility()

                // Ensure we're scrolled to the top
                searchResultsRecyclerView.post {
                    searchResultsRecyclerView.scrollToPosition(0)
                    Log.d("NotificationsFragment", "Scrolled search results to top")

                    // Double-check visibility after scrolling
                    Log.d("SearchDebug", "After scrolling, visibility states: " +
                        "searchResults=${searchResultsRecyclerView.visibility == View.VISIBLE}, " +
                        "notifications=${notificationsRecyclerView.visibility == View.VISIBLE}, " +
                        "empty=${emptyView.visibility == View.VISIBLE}")
                }
            } else {
                Log.d("SearchDebug", "No search results found, updating visibility")
                // Update visibility for empty results
                updateViewVisibility()
            }
        }

        // Add touch feedback to search results
        searchResultsRecyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()

        return view
    }

    private fun updateAppFilterChips(
        groupedNotifications: Map<String, List<NotificationEntity>>,
        chipAllApps: Chip
    ) {
        // Remove existing dynamic chips (keep "All Apps" chip)
        for (i in chipGroup.childCount - 1 downTo 1) {
            chipGroup.removeViewAt(i)
        }

        // Add chip for each app
        groupedNotifications.forEach { (appName: String, notifications: List<NotificationEntity>) ->
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

                // Get app icon
                val notification = notifications.firstOrNull()
                if (notification?.packageName != null) {
                    try {
                        chipIcon = requireContext().packageManager
                            .getApplicationIcon(notification.packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        chipIcon = ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_app_placeholder
                        )
                    }
                }
                isChipIconVisible = true

                setOnClickListener {
                    viewModel.setSelectedApp(notification?.packageName)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun updateViewVisibility() {
        val hasSearchResults = viewModel.searchResults.value?.any { it.value.isNotEmpty() } == true
        val hasNotifications = viewModel.filteredNotifications.value?.isNotEmpty() == true
        val isSearching = viewModel.isSearching()
        val searchQuery = viewModel.getCurrentSearchQuery()

        // Count total search results for better logging
        val totalSearchResults = viewModel.searchResults.value?.values?.sumOf { it.size } ?: 0

        Log.d("ViewVisibility", """
            isSearching: $isSearching
            hasSearchResults: $hasSearchResults (total: $totalSearchResults)
            hasNotifications: $hasNotifications
            searchResults: ${viewModel.searchResults.value?.mapValues { it.value.size }}
            searchQuery: '$searchQuery'
        """.trimIndent())

        // Update empty view message
        emptyView.setText(when {
            isSearching -> getString(R.string.no_search_results, searchQuery)
            else -> getString(R.string.no_notifications)
        })

        // Force immediate visibility update instead of posting to message queue
        when {
            isSearching && hasSearchResults -> {
                Log.d("ViewVisibility", "Showing search results RecyclerView with $totalSearchResults results")
                Log.d("SearchDebug", "updateViewVisibility: Showing search results with $totalSearchResults results")

                // Make sure search results are visible
                searchResultsRecyclerView.visibility = View.VISIBLE
                notificationsRecyclerView.visibility = View.GONE
                emptyView.visibility = View.GONE

                // Ensure RecyclerView is properly laid out
                searchResultsRecyclerView.post {
                    // Force redraw
                    searchResultsRecyclerView.invalidate()

                    // Check if adapter has items
                    val adapterItemCount = searchResultsRecyclerView.adapter?.itemCount ?: 0
                    Log.d("ViewVisibility", "Search adapter has $adapterItemCount items")
                    Log.d("SearchDebug", "Search adapter has $adapterItemCount items, adapter=${searchResultsRecyclerView.adapter}")
                    Log.d("ViewVisibility", "Search RecyclerView dimensions: ${searchResultsRecyclerView.width}x${searchResultsRecyclerView.height}")

                    // Check if the adapter is the same as the notification adapter
                    val isSameAdapter = searchResultsRecyclerView.adapter === notificationAdapter
                    Log.d("SearchDebug", "Is search adapter same as notification adapter? $isSameAdapter")

                    // Scroll to top to ensure visibility
                    if (adapterItemCount > 0) {
                        searchResultsRecyclerView.scrollToPosition(0)
                        Log.d("SearchDebug", "Scrolled to top of search results")
                    }
                }
            }
            isSearching && !hasSearchResults -> {
                Log.d("ViewVisibility", "No search results, showing empty view")
                Log.d("SearchDebug", "updateViewVisibility: No search results, showing empty view")
                searchResultsRecyclerView.visibility = View.GONE
                notificationsRecyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            }
            else -> {
                Log.d("ViewVisibility", "Not searching, showing notifications")
                Log.d("SearchDebug", "updateViewVisibility: Not searching, showing notifications")
                searchResultsRecyclerView.visibility = View.GONE
                notificationsRecyclerView.visibility = if (hasNotifications) View.VISIBLE else View.GONE
                emptyView.visibility = if (hasNotifications) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        notificationAdapter.cleanup()
        searchAdapter.cleanup()
    }
}
