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
    private val allNotifications = notificationDao.getAllNotifications()

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    val categorizedNotifications: LiveData<Map<String, List<NotificationEntity>>> = 
        searchQuery.switchMap { query: String ->
            allNotifications.map { notifications: List<NotificationEntity> ->
                notifications
                    .filter { notification ->
                        if (query.isEmpty()) true
                        else (notification.title?.contains(query, ignoreCase = true) == true ||
                              notification.content?.contains(query, ignoreCase = true) == true)
                    }
                    .groupBy { it.appName ?: "Unknown" }
                    .toSortedMap()
            }
        }
}
