package com.example.whatsuit.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.NotificationEntity

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application.applicationContext)
    private val notificationDao = database.notificationDao()
    private val searchQuery = MutableLiveData<String>("")
    // Get notifications filtered by search query
    val filteredNotifications: LiveData<List<NotificationEntity>> = 
        searchQuery.switchMap { query ->
            if (query.isEmpty()) {
                notificationDao.getAllNotifications()
            } else {
                notificationDao.searchNotifications("%$query%")
            }
        }

    // Get notifications categorized by app name
    val categorizedNotifications: LiveData<Map<String, List<NotificationEntity>>> = 
        filteredNotifications.map { notifications ->
            notifications.groupBy { it.appName ?: "Unknown" }
                       .toSortedMap()
        }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }
}
