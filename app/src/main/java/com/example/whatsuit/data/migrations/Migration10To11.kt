package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

class Migration10To11 : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            Log.d(TAG, "Starting migration 10 -> 11")

            // Add indices for conversation tracking and improve query performance
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_conversation_history_notification_timestamp 
                ON conversation_history (notificationId, timestamp)
            """)

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_notifications_conversation_timestamp 
                ON notifications (conversationId, timestamp)
            """)

            // Ensure conversationId is not null by updating any null values with a generated ID
            database.execSQL("""
                UPDATE notifications 
                SET conversationId = packageName || '_' || COALESCE(title, 'unknown') 
                WHERE conversationId IS NULL
            """)

            Log.d(TAG, "Successfully completed migration 10 -> 11")
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration 10 -> 11", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "Migration10To11"
    }
}
