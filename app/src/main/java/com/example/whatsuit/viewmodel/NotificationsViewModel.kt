package com.example.whatsuit.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.NotificationEntity
import com.example.whatsuit.util.SearchUtil
import kotlinx.coroutines.launch
import android.util.Log

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application.applicationContext)
    private val notificationDao = database.notificationDao()
    
    // Selected package name filter
    private val selectedPackage = MutableLiveData<String?>(null)
    
    // Search query filter 
    private val searchQuery = MutableLiveData<String>("")

    // Search results by type
    private val _searchResults = MutableLiveData<Map<SearchUtil.ResultType, List<SearchUtil.SearchResult>>>()
    val searchResults: LiveData<Map<SearchUtil.ResultType, List<SearchUtil.SearchResult>>> = _searchResults
    
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
        
    init {
        // Initialize SearchUtil with database
        SearchUtil.initialize(database)
        
Log.d("SearchFlow", "[ViewModel] Initialized with database")

        filteredNotifications.observeForever { notifications ->
            Log.d("NotificationsViewModel", "Filtered notifications count: ${notifications.size}")
        }
    }

    // Get notifications categorized by app name
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
            Log.d("SearchFlow", "[ViewModel] Processing search query: '$trimmedQuery'")
            performGlobalSearch(trimmedQuery)
  // Call search first
            searchQuery.value = trimmedQuery
        }
    }

    fun isSearching(): Boolean {
        return !searchQuery.value.isNullOrBlank()
    }

    fun getCurrentSearchQuery(): String {
        return searchQuery.value?.trim() ?: ""
    }

    fun getNotificationById(id: Long): NotificationEntity? {
        return notificationDao.getNotificationByIdSync(id)
    }

    /**
     * Perform a global search across all content types
     */
    private fun performGlobalSearch(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                Log.d("SearchFlow", "[ViewModel] Clear search results for empty query")
                _searchResults.value = emptyMap()
                return@launch
            }

            try {
                val params = SearchUtil.SearchParams(
                    query = query,
                    limit = 20
                )
                Log.d("SearchFlow", "[ViewModel] Executing search with params: $params")
                val results = SearchUtil.search(params)
                Log.d("SearchFlow", "[ViewModel] Got results: ${results.map { "${it.key}: ${it.value.size}" }}")
                Log.d("SearchFlow", "[ViewModel] Setting search results in LiveData")
                _searchResults.value = results
            } catch (e: Exception) {
                Log.e("NotificationsViewModel", "Error performing global search", e)
                Log.e("NotificationsViewModel", "Stack trace:", e)
                _searchResults.value = emptyMap()
            }
        }
    }

    /**
     * Get search suggestions based on previous searches
     */
    suspend fun getSearchSuggestions(prefix: String, limit: Int = 5): List<String> {
        return try {
            SearchUtil.getSuggestions(prefix, limit)
        } catch (e: Exception) {
            Log.e("NotificationsViewModel", "Error getting search suggestions", e)
            emptyList()
        }
    }
}
