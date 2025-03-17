package com.example.whatsuit.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.NotificationEntity
import com.example.whatsuit.util.TimeFilterHelper

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application.applicationContext)
    private val notificationDao = database.notificationDao()
    private val searchQuery = MutableLiveData<String>("")
    private val selectedTimeFilter = MutableLiveData("all") // "today", "yesterday", "all"
    val selectedSortOrder = MutableLiveData("latest") // "latest", "most_conversations"
    private val selectedAppPackage = MutableLiveData<String?>(null) // null means "All Apps"

    // Get notifications filtered by search query and time
    private val timeFilteredNotifications: LiveData<List<NotificationEntity>> = 
        selectedTimeFilter.switchMap { timeFilter ->
            when (timeFilter) {
                "today" -> notificationDao.getSmartGroupedNotificationsByTimeRange("start of day")
                "yesterday" -> notificationDao.getYesterdayNotifications()
                else -> notificationDao.getSmartGroupedNotifications()
            }
        }

    // Apply app filter
    private val appFilteredNotifications: LiveData<List<NotificationEntity>> =
        selectedAppPackage.switchMap { packageName ->
            if (packageName == null) {
                timeFilteredNotifications
            } else {
                timeFilteredNotifications.map { notifications ->
                    notifications.filter { it.packageName == packageName }
                }
            }
        }

    // Apply search filter
    val filteredAndSortedNotifications: LiveData<List<NotificationEntity>> =
        searchQuery.switchMap { query ->
            if (query.isEmpty()) {
                applySorting(appFilteredNotifications)
            } else {
                applySorting(appFilteredNotifications.map { notifications ->
                    notifications.filter { notification ->
                        notification.title?.contains(query, true) == true ||
                        notification.content?.contains(query, true) == true ||
                        notification.appName?.contains(query, true) == true
                    }
                })
            }
        }

    // Get notifications categorized by app name
    public val categorizedNotifications: LiveData<Map<String, List<NotificationEntity>>> = 
        filteredAndSortedNotifications.map { notifications ->
            notifications.groupBy { it.appName ?: "Unknown" }
                       .toSortedMap()
        }

    private fun applySorting(source: LiveData<List<NotificationEntity>>): LiveData<List<NotificationEntity>> {
        return source.switchMap { notifications ->
            selectedSortOrder.map { sortOrder ->
                when (sortOrder) {
                    "most_conversations" -> notifications.sortedByDescending { it.groupCount ?: 0 }
                    else -> notifications.sortedByDescending { it.timestamp }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setTimeFilter(filter: String) {
        selectedTimeFilter.value = filter
    }

    fun setSortOrder(order: String) {
        selectedSortOrder.value = order
    }

    fun setSelectedApp(packageName: String?) {
        selectedAppPackage.value = packageName
    }
}
