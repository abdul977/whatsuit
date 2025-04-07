package com.example.whatsuit.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

class DatabaseCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        recreateViews(db)
    }

    private fun recreateViews(database: SupportSQLiteDatabase) {
        Log.d(TAG, "Recreating database views")
        try {
            database.beginTransaction()

            // Drop existing views
            dropViews(database)
            
            // Create intermediate views
            createSearchableConversationsView(database)
            createSearchableTemplatesView(database)
            
            // Create global search view
            createGlobalSearchView(database)
            
            database.setTransactionSuccessful()
            Log.d(TAG, "Successfully recreated database views")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error recreating database views", e)
        } finally {
            database.endTransaction()
        }
    }

    private fun dropViews(database: SupportSQLiteDatabase) {
        database.execSQL("DROP VIEW IF EXISTS v_global_search")
        database.execSQL("DROP VIEW IF EXISTS v_searchable_conversations")
        database.execSQL("DROP VIEW IF EXISTS v_searchable_templates")
    }

    private fun createSearchableConversationsView(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE VIEW v_searchable_conversations AS
            SELECT
                ch.id,
                'conversation' as result_type,
                ch.message as search_title,
                ch.response as search_content,
                ch.timestamp as search_timestamp,
                ch.notificationId as notification_id
            FROM conversation_history ch
        """.trimIndent())
    }

    private fun createSearchableTemplatesView(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE VIEW v_searchable_templates AS
            SELECT 
                id,
                'template' as result_type,
                name as search_title,
                template as search_content,
                created_at as search_timestamp,
                is_active
            FROM prompt_templates
        """.trimIndent())
    }

    private fun createGlobalSearchView(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE VIEW v_global_search AS
            SELECT
                id,
                result_type,
                search_title,
                search_content,
                search_timestamp,
                package_name,
                app_name,
                notification_id,
                COALESCE(is_active, 0) as is_active
            FROM (
                SELECT 
                    n.id,
                    'notification' as result_type,
                    n.title as search_title,
                    n.content as search_content,
                    n.timestamp as search_timestamp,
                    n.packageName as package_name,
                    COALESCE(a.appName, n.packageName) as app_name,
                    n.id as notification_id,
                    0 as is_active
                FROM notifications n
                LEFT JOIN app_settings a ON n.packageName = a.packageName
                
                UNION ALL
                
                SELECT
                    conv.id,
                    conv.result_type,
                    conv.search_title,
                    conv.search_content,
                    conv.search_timestamp,
                    n.packageName as package_name,
                    COALESCE(a.appName, n.packageName) as app_name,
                    conv.notification_id,
                    0 as is_active
                FROM v_searchable_conversations conv
                LEFT JOIN notifications n ON conv.notification_id = n.id
                LEFT JOIN app_settings a ON n.packageName = a.packageName
                
                UNION ALL
                
                SELECT 
                    id,
                    result_type,
                    search_title,
                    search_content,
                    search_timestamp,
                    NULL as package_name,
                    NULL as app_name,
                    NULL as notification_id,
                    is_active
                FROM v_searchable_templates
            )
            ORDER BY search_timestamp DESC
        """.trimIndent())
    }

    companion object {
        private const val TAG = "DatabaseCallback"
    }
}
