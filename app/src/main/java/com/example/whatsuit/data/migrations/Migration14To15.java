package com.example.whatsuit.data.migrations;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class Migration14To15 extends Migration {
    public Migration14To15() {
        super(14, 15);
    }

    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        // Create auto_reply_settings table
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS auto_reply_settings (" +
            "conversationId TEXT PRIMARY KEY NOT NULL, " +
            "isDisabled INTEGER NOT NULL DEFAULT 0, " +
            "lastUpdated INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)" +
            ")"
        );

        // Create index for faster lookups
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_auto_reply_settings_conversationId " +
            "ON auto_reply_settings(conversationId)"
        );
    }
}