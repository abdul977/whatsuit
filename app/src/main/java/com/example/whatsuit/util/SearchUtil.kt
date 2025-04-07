package com.example.whatsuit.util

import android.database.Cursor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.whatsuit.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for performing global search across all database tables
 * Uses the v_global_search view for efficient querying
 */
object SearchUtil {
    private var appDatabase: AppDatabase? = null

    /**
     * Initialize the SearchUtil with database instance
     */
    fun initialize(database: AppDatabase) {
        appDatabase = database
    }

    /**
     * Search result types
     */
    enum class ResultType(val key: String) {
        NOTIFICATION("notification"),
        CONVERSATION("conversation"),
        TEMPLATE("template")
    }

    /**
     * Base class for search results
     */
    sealed class SearchResult {
        abstract val id: Long
        abstract val title: String
        abstract val content: String
        abstract val timestamp: Long
        abstract val type: ResultType
    }

    /**
     * Notification search result
     */
    data class NotificationResult(
        override val id: Long,
        override val title: String,
        override val content: String,
        override val timestamp: Long,
        val packageName: String,
        val appName: String
    ) : SearchResult() {
        override val type = ResultType.NOTIFICATION
    }

    /**
     * Conversation search result
     */
    data class ConversationResult(
        override val id: Long,
        override val title: String, // message
        override val content: String, // response
        override val timestamp: Long,
        val notificationId: Long
    ) : SearchResult() {
        override val type = ResultType.CONVERSATION
    }

    /**
     * Template search result
     */
    data class TemplateResult(
        override val id: Long,
        override val title: String, // name
        override val content: String, // template
        override val timestamp: Long,
        val isActive: Boolean
    ) : SearchResult() {
        override val type = ResultType.TEMPLATE
    }

    /**
     * Global search parameters
     */
    data class SearchParams(
        val query: String,
        val types: List<ResultType>? = null,
        val limit: Int = 20,
        val offset: Int = 0
    )

    /**
     * Perform a global search across all content
     * @param params Search parameters
     * @return LiveData containing search results grouped by type
     */
    suspend fun search(params: SearchParams): Map<ResultType, List<SearchResult>> = withContext(Dispatchers.IO) {
        val db = appDatabase ?: throw IllegalStateException("SearchUtil not initialized")
        val searchQuery = "%${params.query.trim()}%"

        // Build type filter
        val typeFilter = params.types?.let { types ->
            if (types.isNotEmpty()) {
                "AND result_type IN (${types.joinToString { "'${it.key}'" }})"
            } else null
        } ?: ""

        // Build and execute query
        val query = SimpleSQLiteQuery("""
            SELECT *
            FROM v_global_search
            WHERE (search_title LIKE ? OR search_content LIKE ?)
            $typeFilter
            ORDER BY search_timestamp DESC
            LIMIT ? OFFSET ?
        """.trimIndent(), arrayOf(searchQuery, searchQuery, params.limit, params.offset))

        val cursor = db.query(query)
        cursor.use { c ->
            val results = mutableListOf<SearchResult>()
            while (c.moveToNext()) {
                results.add(cursorToSearchResult(c))
            }
            results.groupBy { it.type }
        }
    }

    /**
     * Convert database cursor to SearchResult
     */
    private fun cursorToSearchResult(cursor: Cursor): SearchResult {
        return when (cursor.getString(cursor.getColumnIndexOrThrow("result_type"))) {
            ResultType.NOTIFICATION.key -> NotificationResult(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                title = cursor.getString(cursor.getColumnIndexOrThrow("search_title")),
                content = cursor.getString(cursor.getColumnIndexOrThrow("search_content")),
                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("search_timestamp")),
                packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                appName = cursor.getString(cursor.getColumnIndexOrThrow("app_name"))
            )
            ResultType.CONVERSATION.key -> ConversationResult(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                title = cursor.getString(cursor.getColumnIndexOrThrow("search_title")),
                content = cursor.getString(cursor.getColumnIndexOrThrow("search_content")),
                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("search_timestamp")),
                notificationId = cursor.getLong(cursor.getColumnIndexOrThrow("notification_id"))
            )
            ResultType.TEMPLATE.key -> TemplateResult(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                title = cursor.getString(cursor.getColumnIndexOrThrow("search_title")),
                content = cursor.getString(cursor.getColumnIndexOrThrow("search_content")),
                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("search_timestamp")),
                isActive = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1
            )
            else -> throw IllegalArgumentException("Unknown result type")
        }
    }

    /**
     * Get search suggestions based on previous searches
     * @param prefix The search prefix
     * @param limit Maximum number of suggestions
     */
    suspend fun getSuggestions(prefix: String, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        val db = appDatabase ?: throw IllegalStateException("SearchUtil not initialized")
        val escapedPrefix = escapeSearchQuery(prefix)

        val query = SimpleSQLiteQuery("""
            SELECT DISTINCT search_title 
            FROM v_global_search 
            WHERE search_title LIKE ? 
            LIMIT ?
        """.trimIndent(), arrayOf("$escapedPrefix%", limit))

        val cursor = db.query(query)
        cursor.use { c ->
            val suggestions = mutableListOf<String>()
            while (c.moveToNext()) {
                suggestions.add(c.getString(0))
            }
            suggestions
        }
    }

    /**
     * Escape special characters in search query
     */
    private fun escapeSearchQuery(query: String): String {
        return query.replace(Regex("[%_\\[\\]^]")) { matchResult ->
            "\\${matchResult.value}"
        }
    }
}