package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 8 to 9
 * Adds isModified column to conversation_history table
 */
class Migration8To9 : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isModified column with default value false
        database.execSQL("""
            ALTER TABLE conversation_history 
            ADD COLUMN isModified INTEGER NOT NULL DEFAULT 0
        """)
    }
}
