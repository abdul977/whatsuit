package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

class Migration11To12 : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            Log.d(TAG, "Starting migration 11 -> 12")

            // Add autoReplyGroupsEnabled column to app_settings table with default value true
            database.execSQL("""
                ALTER TABLE app_settings 
                ADD COLUMN autoReplyGroupsEnabled INTEGER NOT NULL DEFAULT 1
            """)

            Log.d(TAG, "Successfully completed migration 11 -> 12")
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration 11 -> 12", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "Migration11To12"
    }
}
