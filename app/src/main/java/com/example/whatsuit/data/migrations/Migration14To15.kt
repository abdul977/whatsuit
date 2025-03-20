package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration14To15 : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create auto_reply_rules table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS auto_reply_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                packageName TEXT NOT NULL,
                identifier TEXT NOT NULL,
                identifierType TEXT NOT NULL,
                disabled INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
            )
        """)

        // Create index for faster lookups
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_auto_reply_rules_unique 
            ON auto_reply_rules(packageName, identifier, identifierType)
        """)

        // Migrate existing auto-reply disabled settings
        database.execSQL("""
            INSERT INTO auto_reply_rules (packageName, identifier, identifierType, disabled)
            SELECT DISTINCT 
                packageName,
                CASE 
                    WHEN packageName LIKE '%whatsapp%' AND title LIKE '%[0-9+]%'
                    THEN replace(replace(replace(title, '+', ''), '-', ''), ' ', '')
                    ELSE title
                END as identifier,
                CASE 
                    WHEN packageName LIKE '%whatsapp%' AND title LIKE '%[0-9+]%'
                    THEN 'PHONE_NUMBER'
                    ELSE 'TITLE'
                END as identifierType,
                1 as disabled
            FROM notifications
            WHERE autoReplyDisabled = 1
        """)
    }
}
