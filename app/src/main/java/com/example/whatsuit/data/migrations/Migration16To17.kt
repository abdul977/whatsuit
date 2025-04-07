package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Migration 16 to 17:
 * - No schema changes needed as views are now handled by DatabaseCallback
 */
class Migration16To17 : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            Log.d(TAG, "Starting migration 16 -> 17")
            // No schema changes needed
            Log.d(TAG, "Successfully completed migration 16 -> 17")
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration 16 -> 17", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "Migration16To17"
    }
}
