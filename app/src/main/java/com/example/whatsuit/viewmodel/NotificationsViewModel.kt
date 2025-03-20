package com.example.whatsuit.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.NotificationEntity
import android.util.Log

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application.applicationContext)
    private val notificationDao = database.notificationDao()
    
    // Selected package name filter
    private val selectedPackage = MutableLiveData<String?>(null)
    
    // Search query filter 
    private val searchQuery = MutableLiveData<String>("")
    
    // Get notifications filtered by package and search query
    val filteredNotifications: LiveData<List<NotificationEntity>> = 
        selectedPackage.switchMap { packageName ->
            searchQuery.switchMap { query ->
                when {
                    // No filters - show all
                    packageName == null && query.isEmpty() -> 
                        notificationDao.getSmartGroupedNotifications()
                    
                    // Only package filter
                    query.isEmpty() -> 
                        notificationDao.getNotificationsForApp(packageName)
                    
                    // Only search filter
                    packageName == null -> {
                        val searchTerm = if (!query.startsWith("%")) "%$query%" else query
                        notificationDao.searchNotifications(searchTerm)
                    }
                    
                    // Both filters
                    else -> {
                        val searchTerm = if (!query.startsWith("%")) "%$query%" else query
                        notificationDao.searchNotifications(searchTerm).map { notifications ->
                            notifications.filter { it.packageName == packageName }
                        }
                    }
                }
            }
        }

    // Get notifications categorized by app name, including package name mapping
    val categorizedNotifications: LiveData<Map<String, List<NotificationEntity>>> = 
        filteredNotifications.map { notifications ->
            notifications.groupBy { it.appName ?: "Unknown" }
                       .toSortedMap()
        }
        
    // Get total notification count
    val totalNotificationCount: LiveData<Int> = filteredNotifications.map { it.size }
    
    // Get notification count per app
    val notificationCountPerApp: LiveData<Map<String, Int>> = categorizedNotifications.map { grouped ->
        grouped.mapValues { it.value.size }
    }
    
    fun setSelectedApp(packageName: String?) {
        selectedPackage.value = packageName
    }
    
    fun setSearchQuery(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery != searchQuery.value?.trim('%')) {
            searchQuery.value = trimmedQuery
        }
    }

    // Add observer in init block
    init {
        filteredNotifications.observeForever { notifications ->
            Log.d("NotificationsViewModel", "Filtered notifications count: ${notifications.size}")
        }
    }
}
