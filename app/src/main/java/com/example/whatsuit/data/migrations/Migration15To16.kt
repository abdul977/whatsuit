package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 
 * Migration 15 to 16: Adds global search functionality with views and indexes
 * - Creates search indexes on conversation_history and prompt_templates
 * - Creates views for searchable conversations, templates, and global search
 * - Adds validation to ensure views are created correctly
 */
class Migration15To16 : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val migrationName = "Migration 15 to 16"
        Log.d(TAG, "Starting $migrationName")
        
        try {
            database.beginTransaction()

            
// Step 1: Drop existing views to avoid conflicts
            dropExistingViews(database)
            
            // Step 2: Create search indices
            createSearchIndices(database)
            
            // Step 3: Create intermediate views
            createSearchableConversationsView(database)
            createSearchableTemplatesView(database)
            
            // Step 4: Create global search view
            createGlobalSearchView(database)
            
            // Step 5: Validate views and indices
            validateMigration(database)
            
            database.setTransactionSuccessful()
            Log.d(TAG, "$migrationName completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during $migrationName", e)
            Log.e(TAG, "Stack trace:", e)
            throw MigrationException("Failed to execute $migrationName", e)
        } finally {
            database.endTransaction()
        }
    }
    
    private fun dropExistingViews(database: SupportSQLiteDatabase) {
        Log.d(TAG, "Dropping existing views if present")
        try {
            database.execSQL("DROP VIEW IF EXISTS v_global_search")
            database.execSQL("DROP VIEW IF EXISTS v_searchable_conversations")
            database.execSQL("DROP VIEW IF EXISTS v_searchable_templates")
        } catch (e: Exception) {
            throw MigrationException("Failed to drop existing views", e)
        }
    }
    
    private fun createSearchIndices(database: SupportSQLiteDatabase) {
        Log.d(TAG, "Creating search indices")
        try {
            // Conversation history search index
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_conversation_history_search 
                ON conversation_history
 (message, response)
            """.trimIndent())

            // Prompt templates search index
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_prompt_templates_search 
                ON prompt_templates(name, template)
            """.trimIndent())
            
            // Global search indices
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_global_search_title 
                ON notifications(title)
            """.trimIndent())
            
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_global_search_content 
                ON notifications(content)
            """.trimIndent())
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_global_search_timestamp 
                ON notifications(timestamp DESC)
            """.trimIndent())
            
        } catch (e: Exception) {
            throw MigrationException("Failed to create search indices", e)
        }
    }
    
    private fun createSearchableConversationsView(database: SupportSQLiteDatabase) {
        Log.d(TAG, "Creating searchable conversations view")
        try {
            database.execSQL("""
                CREATE VIEW v_searchable_conversations AS
                SELECT
                    ch.id,
                    'conversation' as result_type,
                    ch.message as search_title,
                    ch.response as search_content,
                    ch.timestamp as search_timestamp,
                    n.id as notification_id
                FROM conversation_history ch
                LEFT JOIN notifications n ON ch.notification_id = n.id
            """.trimIndent())
        } catch (e: Exception) {
            throw MigrationException("Failed to create searchable conversations view", e)
        }
    }
    
    private fun createSearchableTemplatesView(database: SupportSQLiteDatabase) {
        Log.d(TAG, "Creating searchable templates view")
        try {
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
        } catch (e: Exception) {
            throw MigrationException("Failed to create searchable templates view", e)
        }
    }
    
    private fun createGlobalSearchView(database: SupportSQLiteDatabase) {
        Log.d(TAG, "Creating global search view")
        try {
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
                        id,
                        'notification' as result_type,
                        title as search_title,
                        content as search_content,
                        timestamp as search_timestamp,
                        packageName as package_name,
                        appName as app_name,
                        id as notification_id,
                        0 as is_active
                    FROM notifications
                    
                UNION ALL
                        conv.id,
                        conv.result_type,
                        conv.search_title,
                        conv.search_content,
                        conv.search_timestamp,
                        n.packageName as package_name,
                        n.appName as app_name,
                        conv.notification_id,
                        0 as is_active
                    FROM v_searchable_conversations conv
                    LEFT JOIN notifications n ON conv.notification_id = n.id
                    
                    UNION ALL
                    
                    SELECT 
                        tmpl.id,
                        tmpl.result_type,
                        tmpl.search_title,
                        tmpl.search_content,
                        tmpl.search_timestamp,
                        NULL as package_name,
                        NULL as app_name,
                        NULL as notification_id,
                        tmpl.is_active
                    FROM v_searchable_templates
 tmpl
                )
                ORDER BY search_timestamp DESC
            """.trimIndent())
        } catch (e: Exception) {
            throw MigrationException("Failed to create global search view", e)
        }
    }
    
    private fun validateMigration(database: SupportSQLiteDatabase) {
        Log.d(TAG, "Validating migration")
        try {
            // Validate views exist and have correct structure
            validateView(database, "v_searchable_conversations", listOf(
                "id", "result_type", "search_title", "search_content",
                "search_timestamp", "notification_id"
            ))
            
            validateView(database, "v_searchable_templates", listOf(
                "id", "result_type", "search_title", "search_content",
                "search_timestamp", "is_active"
            ))
            
            validateView(database, "v_global_search", listOf(
                "id", "result_type", "search_title", "search_content",
                "search_timestamp", "package_name", "app_name",
                "notification_id", "is_active"
            ))
            
            // Test query execution
            database.query("SELECT COUNT(*) FROM v_global_search LIMIT 1")
            

            Log.d(TAG, "Migration validation successful")
        } catch (e: Exception) {
            throw MigrationException("Migration validation failed", e)
        }
    }
    
    private fun validateView(database: SupportSQLiteDatabase, viewName: String, expectedColumns: List<String>) {
        val cursor = database.query("PRAGMA table_info($viewName)")
        val columns = mutableListOf<String>()
        
        cursor.use {
            while (it.moveToNext()) {
                columns.add(it.getString(it.getColumnIndexOrThrow("name")))
            }
        }
        
        val missingColumns = expectedColumns.filter { !columns.contains(it) }
        if (missingColumns.isNotEmpty()) {
            throw MigrationException("View $viewName missing columns: $missingColumns")
        }
    }
    
    class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        private const val TAG = "Migration15To16"
    }
}