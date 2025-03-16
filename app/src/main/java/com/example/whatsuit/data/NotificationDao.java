package com.example.whatsuit.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Transaction;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface NotificationDao {
    @Query("SELECT COUNT(*) FROM notifications")
    int getCount();

    @Insert
    long insert(NotificationEntity notification);

    @Delete
    void delete(NotificationEntity notification);

    @Update
    void update(NotificationEntity notification);

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> getAllNotifications();

    @Query("SELECT * FROM notifications WHERE " +
           "LOWER(title) LIKE LOWER(:query) OR " +
           "LOWER(content) LIKE LOWER(:query) OR " +
           "LOWER(appName) LIKE LOWER(:query) " +
           "ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> searchNotifications(String query);

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> getNotificationsForApp(String packageName);

    @Query("SELECT DISTINCT packageName, appName FROM notifications ORDER BY appName ASC")
    LiveData<List<AppInfo>> getDistinctApps();

    @Query("SELECT * FROM notifications GROUP BY packageName, timestamp ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> getGroupedNotifications();

    @Query("SELECT * FROM notifications WHERE conversationId = :conversationId")
    LiveData<NotificationEntity> getNotificationById(String conversationId);

    // Check if auto-reply is disabled for a specific title/chat
    @Query("SELECT EXISTS(SELECT 1 FROM notifications " +
            "WHERE packageName = :packageName AND " +
            "CASE " +
            "  WHEN packageName LIKE '%whatsapp%' AND title LIKE '%[0-9+]%' " +
            "  THEN replace(replace(replace(title, '+', ''), '-', ''), ' ', '') = :phoneNumber " +
            "  ELSE title = :titlePrefix " +
            "END " +
            "AND autoReplyDisabled = 1 LIMIT 1)")
    boolean isAutoReplyDisabled(String packageName, String phoneNumber, String titlePrefix);

    @Query("SELECT autoReplyDisabled FROM notifications WHERE packageName = :packageName AND conversationId = :conversationId LIMIT 1")
    boolean isAutoReplyDisabled(String packageName, String conversationId);

    // Update auto-reply disabled status for all matching notifications
    @Query("UPDATE notifications SET autoReplyDisabled = :disabled " +
            "WHERE packageName = :packageName AND " +
            "CASE " +
            "  WHEN packageName LIKE '%whatsapp%' AND title LIKE '%[0-9+]%' " +
            "  THEN replace(replace(replace(title, '+', ''), '-', ''), ' ', '') = :phoneNumber " +
            "  ELSE title = :titlePrefix " +
            "END")
    void updateAutoReplyDisabled(String packageName, String phoneNumber, String titlePrefix, boolean disabled);

    // Get all related notifications with same exact phone number and same package
    @Query("SELECT * FROM notifications " +
            "WHERE packageName = (SELECT packageName FROM notifications WHERE conversationId = :conversationId) " +
            "AND CASE " +
            "  WHEN packageName LIKE '%whatsapp%' AND " +
            "       title LIKE '%[0-9+]%' AND " +
            "       (SELECT title FROM notifications WHERE conversationId = :conversationId) LIKE '%[0-9+]%' " +
            "  THEN " +
            "    replace(replace(replace(title, '+', ''), '-', ''), ' ', '') = " +
            "    replace(replace(replace((SELECT title FROM notifications WHERE conversationId = :conversationId), '+', ''), '-', ''), ' ', '') " +
            "  ELSE " +
            "    title = (SELECT title FROM notifications WHERE conversationId = :conversationId) " +
            "END " +
            "ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> getRelatedNotifications(String conversationId);

    // Get related notifications within a time range
    @Query("SELECT * FROM notifications " +
            "WHERE packageName = :packageName " +
            "AND CASE " +
            "  WHEN packageName LIKE '%whatsapp%' AND " +
            "       title LIKE '%[0-9+]%' " +
            "  THEN " +
            "    SUBSTR(replace(replace(replace(title, '+', ''), '-', ''), ' ', ''), 1, 11) = :phoneNumber " +
            "  ELSE " +
            "    SUBSTR(title, 1, 5) = :titlePrefix " +
            "END " +
            "AND timestamp >= :startTime " +
            "AND timestamp <= :endTime " +
            "ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> getRelatedNotificationsByTimeRange(
            String packageName,
            String phoneNumber,
            String titlePrefix,
            long startTime,
            long endTime
    );

    // Smart grouping for time range with exact phone number matching for WhatsApp
    @androidx.room.RewriteQueriesToDropUnusedColumns
    @Query("WITH GroupedNotifs AS (" +
            "  SELECT n1.*, " +
            "         MIN(n1.timestamp) as group_timestamp, " +
            "         COUNT(*) as group_count " +
            "  FROM notifications n1 " +
            "  LEFT JOIN notifications n2 ON " +
            "    n1.packageName = n2.packageName AND " +
            "    CASE " +
            "      WHEN n1.packageName LIKE '%whatsapp%' AND " +
            "           n1.title IS NOT NULL AND n2.title IS NOT NULL AND " +
            "           n1.title LIKE '%[0-9+]%' AND " +
            "           n2.title LIKE '%[0-9+]%' AND " +
            "           LENGTH(COALESCE(replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', ''), '')) >= 11 AND " +
            "           LENGTH(COALESCE(replace(replace(replace(n2.title, '+', ''), '-', ''), ' ', ''), '')) >= 11 " +
            "      THEN " +
            "        SUBSTR(COALESCE(replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', ''), ''), 1, 11) = " +
            "        SUBSTR(COALESCE(replace(replace(replace(n2.title, '+', ''), '-', ''), ' ', ''), ''), 1, 11) " +
            "      ELSE " +
            "        SUBSTR(COALESCE(n1.title, ''), 1, 5) = SUBSTR(COALESCE(n2.title, ''), 1, 5) " +
            "    END AND " +
            "    abs(n1.timestamp - n2.timestamp) < 86400000 " +
            "  WHERE n1.timestamp >= :startTime AND n1.timestamp <= :endTime " +
            "  GROUP BY n1.packageName, " +
            "           CASE " +
            "             WHEN n1.packageName LIKE '%whatsapp%' AND " +
            "                  n1.title LIKE '%[0-9+]%' AND " +
            "                  LENGTH(replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', '')) = 11 " +
            "             THEN replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', '') " +
            "             ELSE n1.title " +
            "           END " +
            ") " +
            "SELECT * FROM GroupedNotifs ORDER BY group_timestamp DESC")
    LiveData<List<NotificationEntity>> getSmartGroupedNotificationsInRange(long startTime, long endTime);

    @androidx.room.RewriteQueriesToDropUnusedColumns
    @Query("WITH GroupedNotifs AS (" +
            "  SELECT n1.*, " +
            "         MIN(n1.timestamp) as group_timestamp, " +
            "         COUNT(*) as group_count " +
            "  FROM notifications n1 " +
            "  LEFT JOIN notifications n2 ON " +
            "    n1.packageName = n2.packageName AND " +
            "    CASE " +
            "      WHEN n1.packageName LIKE '%whatsapp%' AND " +
            "           n1.title IS NOT NULL AND n2.title IS NOT NULL AND " +
            "           n1.title LIKE '%[0-9+]%' AND " +
            "           n2.title LIKE '%[0-9+]%' AND " +
            "           LENGTH(COALESCE(replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', ''), '')) >= 11 AND " +
            "           LENGTH(COALESCE(replace(replace(replace(n2.title, '+', ''), '-', ''), ' ', ''), '')) >= 11 " +
            "      THEN " +
            "        SUBSTR(COALESCE(replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', ''), ''), 1, 11) = " +
            "        SUBSTR(COALESCE(replace(replace(replace(n2.title, '+', ''), '-', ''), ' ', ''), ''), 1, 11) " +
            "      ELSE " +
            "        SUBSTR(COALESCE(n1.title, ''), 1, 5) = SUBSTR(COALESCE(n2.title, ''), 1, 5) " +
            "    END AND " +
            "    abs(n1.timestamp - n2.timestamp) < 86400000 " +
            "  WHERE n1.timestamp >= strftime('%s', datetime('now', :timeRange)) * 1000 " +
            "  GROUP BY n1.packageName, " +
            "           CASE " +
            "             WHEN n1.packageName LIKE '%whatsapp%' AND " +
            "                  n1.title LIKE '%[0-9+]%' AND " +
            "                  LENGTH(replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', '')) = 11 " +
            "             THEN replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', '') " +
            "             ELSE n1.title " +
            "           END " +
            ") " +
            "SELECT * FROM GroupedNotifs ORDER BY group_timestamp DESC")
    LiveData<List<NotificationEntity>> getSmartGroupedNotificationsByTimeRange(String timeRange);

    // Get yesterday's notifications with smart grouping
    default LiveData<List<NotificationEntity>> getYesterdayNotifications() {
        return getSmartGroupedNotificationsByTimeRange("-1 day");
    }


    // Get notification by thread ID
    @Query("SELECT * FROM notifications WHERE conversationId = :threadId ORDER BY timestamp DESC LIMIT 1")
    NotificationEntity getNotificationByThreadIdSync(String threadId);

    @Transaction
    default long upsertNotification(NotificationEntity notification) {
        NotificationEntity existing = getNotificationByThreadIdSync(notification.getConversationId());
        if (existing != null) {
            notification.setId(existing.getId());
            update(notification);
            return existing.getId();
        }
        return insert(notification);
    }

    // Atomic check and update operation
    @Transaction
    default NotificationEntity getAndUpdateNotification(String threadId, NotificationEntity notification) {
        return getNotificationByThreadIdSync(threadId);
    }

    @Query("SELECT * FROM notifications WHERE id = :id")
    NotificationEntity getNotificationByIdSync(long id);

    @Query("SELECT * FROM notifications WHERE id = :id")
    LiveData<NotificationEntity> getNotificationByIdNumeric(long id);

    @Query("SELECT * FROM notifications " +
           "WHERE conversationId = :conversationId " +
           "AND timestamp >= :startTime " +
           "AND timestamp <= :endTime " +
           "ORDER BY timestamp ASC")
    List<NotificationEntity> getNotificationsInTimeRange(String conversationId, long startTime, long endTime);

    // Get all notifications with smart grouping
    @androidx.room.RewriteQueriesToDropUnusedColumns
    @Query("WITH GroupedNotifs AS (" +
            "  SELECT n1.*, " +
            "         MIN(n1.timestamp) as group_timestamp, " +
            "         COUNT(*) as group_count " +
            "  FROM notifications n1 " +
            "  LEFT JOIN notifications n2 ON " +
            "    n1.packageName = n2.packageName AND " +
            "    CASE " +
            "      WHEN n1.packageName LIKE '%whatsapp%' AND " +
            "           n1.title IS NOT NULL AND n2.title IS NOT NULL AND " +
            "           n1.title LIKE '%[0-9+]%' AND " +
            "           n2.title LIKE '%[0-9+]%' AND " +
            "           LENGTH(COALESCE(replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', ''), '')) >= 11 AND " +
            "           LENGTH(COALESCE(replace(replace(replace(n2.title, '+', ''), '-', ''), ' ', ''), '')) >= 11 " +
            "      THEN " +
            "        SUBSTR(COALESCE(replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', ''), ''), 1, 11) = " +
            "        SUBSTR(COALESCE(replace(replace(replace(n2.title, '+', ''), '-', ''), ' ', ''), ''), 1, 11) " +
            "      ELSE " +
            "        SUBSTR(COALESCE(n1.title, ''), 1, 5) = SUBSTR(COALESCE(n2.title, ''), 1, 5) " +
            "    END AND " +
            "    abs(n1.timestamp - n2.timestamp) < 86400000 " +
            "  GROUP BY n1.packageName, " +
            "           CASE " +
            "             WHEN n1.packageName LIKE '%whatsapp%' AND " +
            "                  n1.title LIKE '%[0-9+]%' AND " +
            "                  LENGTH(replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', '')) = 11 " +
            "             THEN replace(replace(replace(n1.title, '+', ''), '-', ''), ' ', '') " +
            "             ELSE n1.title " +
            "           END " +
            ") " +
            "SELECT * FROM GroupedNotifs ORDER BY group_timestamp DESC")
    LiveData<List<NotificationEntity>> getSmartGroupedNotifications();
}
