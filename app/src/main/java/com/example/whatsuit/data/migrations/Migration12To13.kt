package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration12To13 : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Drop the old index if it exists
        database.execSQL("DROP INDEX IF EXISTS `index_notifications_conversationId`")
        
        // Create new unique index
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notifications_conversationId` ON notifications(`conversationId`)")
    }
}
