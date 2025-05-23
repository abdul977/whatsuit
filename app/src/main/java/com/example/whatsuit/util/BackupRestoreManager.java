package com.example.whatsuit.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import androidx.room.Room;
import com.example.whatsuit.data.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Manager class for handling backup and restore operations.
 * Backs up all application data including database, SharedPreferences, and media files.
 */
public class BackupRestoreManager {
    private static final String TAG = "BackupRestoreManager";
    private static final String BACKUP_VERSION = "1.0";
    private static final String BACKUP_METADATA_FILE = "backup_metadata.json";
    private static final String DATABASE_BACKUP_FILE = "database_backup.json";
    private static final String PREFERENCES_BACKUP_FILE = "preferences_backup.json";
    private static final String MEDIA_FOLDER = "media/";

    private final Context context;
    private final AppDatabase database;
    private final Gson gson;

    public interface BackupRestoreCallback {
        void onProgress(String message, int progress);
        void onSuccess(String message);
        void onError(String error);
    }

    public BackupRestoreManager(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getDatabase(context);
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
    }

    /**
     * Creates a backup of all application data and saves it to the specified URI
     */
    public void createBackup(Uri destinationUri, BackupRestoreCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("Starting backup...", 0);

                // Create temporary directory for backup files
                File tempDir = new File(context.getCacheDir(), "backup_temp");
                if (tempDir.exists()) {
                    deleteDirectory(tempDir);
                }
                tempDir.mkdirs();

                // Step 1: Create backup metadata
                callback.onProgress("Creating backup metadata...", 10);
                BackupMetadata metadata = createBackupMetadata();
                saveJsonToFile(metadata, new File(tempDir, BACKUP_METADATA_FILE));

                // Step 2: Backup database
                callback.onProgress("Backing up database...", 20);
                DatabaseBackup databaseBackup = createDatabaseBackup();
                saveJsonToFile(databaseBackup, new File(tempDir, DATABASE_BACKUP_FILE));

                // Step 3: Backup SharedPreferences
                callback.onProgress("Backing up preferences...", 40);
                PreferencesBackup preferencesBackup = createPreferencesBackup();
                saveJsonToFile(preferencesBackup, new File(tempDir, PREFERENCES_BACKUP_FILE));

                // Step 4: Copy media files
                callback.onProgress("Backing up media files...", 60);
                copyMediaFiles(tempDir);

                // Step 5: Create ZIP file
                callback.onProgress("Creating backup archive...", 80);
                createZipFile(tempDir, destinationUri);

                // Cleanup
                deleteDirectory(tempDir);

                callback.onProgress("Backup completed successfully!", 100);
                callback.onSuccess("Backup created successfully at " + destinationUri.getLastPathSegment());

            } catch (Exception e) {
                Log.e(TAG, "Backup failed", e);
                callback.onError("Backup failed: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Restores application data from the specified backup ZIP file
     */
    public void restoreBackup(Uri backupUri, BackupRestoreCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("Starting restore...", 0);

                // Create temporary directory for extracted files
                File tempDir = new File(context.getCacheDir(), "restore_temp");
                if (tempDir.exists()) {
                    deleteDirectory(tempDir);
                }
                tempDir.mkdirs();

                // Step 1: Extract ZIP file
                callback.onProgress("Extracting backup archive...", 10);
                extractZipFile(backupUri, tempDir);

                // Step 2: Validate backup
                callback.onProgress("Validating backup...", 20);
                validateBackup(tempDir);

                // Step 3: Restore database
                callback.onProgress("Restoring database...", 40);
                restoreDatabase(tempDir);

                // Step 4: Restore SharedPreferences
                callback.onProgress("Restoring preferences...", 60);
                restorePreferences(tempDir);

                // Step 5: Restore media files
                callback.onProgress("Restoring media files...", 80);
                restoreMediaFiles(tempDir);

                // Cleanup
                deleteDirectory(tempDir);

                callback.onProgress("Restore completed successfully!", 100);
                callback.onSuccess("Data restored successfully from backup");

            } catch (Exception e) {
                Log.e(TAG, "Restore failed", e);
                callback.onError("Restore failed: " + e.getMessage());
            }
        }).start();
    }

    private BackupMetadata createBackupMetadata() {
        BackupMetadata metadata = new BackupMetadata();
        metadata.version = BACKUP_VERSION;
        metadata.timestamp = System.currentTimeMillis();
        metadata.appVersion = getAppVersion();
        metadata.deviceInfo = getDeviceInfo();
        return metadata;
    }

    private DatabaseBackup createDatabaseBackup() {
        DatabaseBackup backup = new DatabaseBackup();

        // Export all entities
        backup.notifications = database.notificationDao().getAllNotificationsSync();
        backup.geminiConfigs = database.geminiDao().getAllConfigsSync();
        backup.conversationHistory = database.conversationHistoryDao().getAllConversationsSync();
        backup.promptTemplates = database.geminiDao().getAllPromptTemplatesSync();
        backup.appSettings = database.appSettingDao().getAllSettingsSync();
        backup.keywordActions = database.keywordActionDao().getAllKeywordActionsSync();
        backup.conversationReplyCounts = database.conversationReplyCountDao().getAllReplyCountsSync();

        return backup;
    }

    private PreferencesBackup createPreferencesBackup() {
        PreferencesBackup backup = new PreferencesBackup();

        // Backup whatsuit_settings
        SharedPreferences whatsuitPrefs = context.getSharedPreferences("whatsuit_settings", Context.MODE_PRIVATE);
        backup.whatsuitSettings = new HashMap<>(whatsuitPrefs.getAll());

        // Backup processed_notifications
        SharedPreferences processedPrefs = context.getSharedPreferences("processed_notifications", Context.MODE_PRIVATE);
        backup.processedNotifications = new HashMap<>(processedPrefs.getAll());

        return backup;
    }

    private void copyMediaFiles(File tempDir) throws IOException {
        File mediaDir = new File(context.getFilesDir(), "media");
        if (!mediaDir.exists()) {
            return; // No media files to backup
        }

        File backupMediaDir = new File(tempDir, MEDIA_FOLDER);
        backupMediaDir.mkdirs();

        copyDirectory(mediaDir, backupMediaDir);
    }

    private void saveJsonToFile(Object object, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(object, writer);
        }
    }

    private <T> T loadJsonFromFile(File file, Class<T> clazz) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, clazz);
        }
    }

    private <T> T loadJsonFromFile(File file, Type type) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, type);
        }
    }

    private void createZipFile(File sourceDir, Uri destinationUri) throws IOException {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(destinationUri);
             ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

            zipDirectory(sourceDir, sourceDir.getName(), zipOut);
        }
    }

    private void zipDirectory(File dir, String baseName, ZipOutputStream zipOut) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                zipDirectory(file, baseName + "/" + file.getName(), zipOut);
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(baseName + "/" + file.getName());
                    zipOut.putNextEntry(zipEntry);

                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zipOut.write(buffer, 0, length);
                    }
                    zipOut.closeEntry();
                }
            }
        }
    }

    private void extractZipFile(Uri zipUri, File destDir) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(zipUri);
             ZipInputStream zipIn = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = zipIn.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
                zipIn.closeEntry();
            }
        }
    }

    private void validateBackup(File tempDir) throws Exception {
        // Check if required files exist
        File metadataFile = new File(tempDir, "backup_temp/" + BACKUP_METADATA_FILE);
        File databaseFile = new File(tempDir, "backup_temp/" + DATABASE_BACKUP_FILE);
        File preferencesFile = new File(tempDir, "backup_temp/" + PREFERENCES_BACKUP_FILE);

        if (!metadataFile.exists()) {
            throw new Exception("Invalid backup: Missing metadata file");
        }
        if (!databaseFile.exists()) {
            throw new Exception("Invalid backup: Missing database backup");
        }
        if (!preferencesFile.exists()) {
            throw new Exception("Invalid backup: Missing preferences backup");
        }

        // Validate metadata
        BackupMetadata metadata = loadJsonFromFile(metadataFile, BackupMetadata.class);
        if (!BACKUP_VERSION.equals(metadata.version)) {
            throw new Exception("Incompatible backup version: " + metadata.version);
        }
    }

    private void restoreDatabase(File tempDir) throws Exception {
        File databaseFile = new File(tempDir, "backup_temp/" + DATABASE_BACKUP_FILE);
        DatabaseBackup backup = loadJsonFromFile(databaseFile, DatabaseBackup.class);

        // Clear existing data
        database.clearAllTables();

        // Restore data
        if (backup.notifications != null && !backup.notifications.isEmpty()) {
            database.notificationDao().insertAll(backup.notifications);
        }
        if (backup.geminiConfigs != null && !backup.geminiConfigs.isEmpty()) {
            database.geminiDao().insertConfigs(backup.geminiConfigs);
        }
        if (backup.conversationHistory != null && !backup.conversationHistory.isEmpty()) {
            database.conversationHistoryDao().insertAll(backup.conversationHistory);
        }
        if (backup.promptTemplates != null && !backup.promptTemplates.isEmpty()) {
            database.geminiDao().insertPromptTemplates(backup.promptTemplates);
        }
        if (backup.appSettings != null && !backup.appSettings.isEmpty()) {
            database.appSettingDao().insertAll(backup.appSettings);
        }
        if (backup.keywordActions != null && !backup.keywordActions.isEmpty()) {
            database.keywordActionDao().insertAll(backup.keywordActions);
        }
        if (backup.conversationReplyCounts != null && !backup.conversationReplyCounts.isEmpty()) {
            database.conversationReplyCountDao().insertAll(backup.conversationReplyCounts);
        }
    }

    private void restorePreferences(File tempDir) throws Exception {
        File preferencesFile = new File(tempDir, "backup_temp/" + PREFERENCES_BACKUP_FILE);
        PreferencesBackup backup = loadJsonFromFile(preferencesFile, PreferencesBackup.class);

        // Restore whatsuit_settings
        if (backup.whatsuitSettings != null) {
            SharedPreferences.Editor editor = context.getSharedPreferences("whatsuit_settings", Context.MODE_PRIVATE).edit();
            editor.clear();
            for (Map.Entry<String, ?> entry : backup.whatsuitSettings.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    editor.putBoolean(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(entry.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(entry.getKey(), (Float) value);
                } else if (value instanceof String) {
                    editor.putString(entry.getKey(), (String) value);
                }
            }
            editor.apply();
        }

        // Restore processed_notifications
        if (backup.processedNotifications != null) {
            SharedPreferences.Editor editor = context.getSharedPreferences("processed_notifications", Context.MODE_PRIVATE).edit();
            editor.clear();
            for (Map.Entry<String, ?> entry : backup.processedNotifications.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    editor.putBoolean(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(entry.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(entry.getKey(), (Float) value);
                } else if (value instanceof String) {
                    editor.putString(entry.getKey(), (String) value);
                }
            }
            editor.apply();
        }
    }

    private void restoreMediaFiles(File tempDir) throws IOException {
        File backupMediaDir = new File(tempDir, "backup_temp/" + MEDIA_FOLDER);
        if (!backupMediaDir.exists()) {
            return; // No media files to restore
        }

        File mediaDir = new File(context.getFilesDir(), "media");
        if (mediaDir.exists()) {
            deleteDirectory(mediaDir);
        }
        mediaDir.mkdirs();

        copyDirectory(backupMediaDir, mediaDir);
    }

    private void copyDirectory(File sourceDir, File destDir) throws IOException {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        File[] files = sourceDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            File destFile = new File(destDir, file.getName());
            if (file.isDirectory()) {
                copyDirectory(file, destFile);
            } else {
                copyFile(file, destFile);
            }
        }
    }

    private void copyFile(File source, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }

    private String getAppVersion() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String getDeviceInfo() {
        return android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
               " (Android " + android.os.Build.VERSION.RELEASE + ")";
    }

    // Data classes for backup structure
    public static class BackupMetadata {
        public String version;
        public long timestamp;
        public String appVersion;
        public String deviceInfo;
    }

    public static class DatabaseBackup {
        public List<NotificationEntity> notifications;
        public List<GeminiConfig> geminiConfigs;
        public List<ConversationHistory> conversationHistory;
        public List<PromptTemplate> promptTemplates;
        public List<AppSettingEntity> appSettings;
        public List<KeywordActionEntity> keywordActions;
        public List<ConversationReplyCount> conversationReplyCounts;
    }

    public static class PreferencesBackup {
        public Map<String, Object> whatsuitSettings;
        public Map<String, Object> processedNotifications;
    }
}
