package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration9To10 : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add analysis and analysisTimestamp columns
        database.execSQL(
            "ALTER TABLE conversation_history ADD COLUMN analysis TEXT"
        )
        database.execSQL(
            "ALTER TABLE conversation_history ADD COLUMN analysisTimestamp INTEGER"
        )
    }
}
