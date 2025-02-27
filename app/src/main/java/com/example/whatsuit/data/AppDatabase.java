package com.example.whatsuit.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {
        NotificationEntity.class,
        GeminiConfig.class,
        ConversationHistory.class,
        PromptTemplate.class
    },
    version = 3,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    
    public abstract NotificationDao notificationDao();
    public abstract GeminiDao geminiDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notifications ADD COLUMN autoReplied INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE notifications ADD COLUMN autoReplyContent TEXT");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create gemini_config table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS gemini_config (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "api_key TEXT NOT NULL, " +
                "model_name TEXT NOT NULL DEFAULT 'gemini-1.5-flash', " +
                "max_history_per_thread INTEGER DEFAULT 10, " +
                "created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)" +
                ")"
            );

            // Create conversation_history table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS conversation_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "notification_id INTEGER, " +
                "message TEXT NOT NULL, " +
                "response TEXT NOT NULL, " +
                "timestamp INTEGER DEFAULT (strftime('%s', 'now') * 1000), " +
                "FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE" +
                ")"
            );

            // Create prompt_templates table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS prompt_templates (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "template TEXT NOT NULL, " +
                "is_active INTEGER NOT NULL DEFAULT 1, " +
                "created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)" +
                ")"
            );

            // Create index for conversation history
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_conversation_notification_id " +
                "ON conversation_history(notification_id)"
            );

            // Insert default prompt template
            database.execSQL(
                "INSERT INTO prompt_templates (name, template) VALUES (" +
                "'Default Concise Response', " +
                "'System: Generate a clear and concise response (maximum 50 words) that directly addresses the query.\n" +
                "Context: Previous conversation - {context}\n" +
                "User: {message}\n" +
                "Assistant: Provide a direct response under 50 words.')"
            );
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "notification_database"
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
