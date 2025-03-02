package com.example.whatsuit.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Transaction;

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

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> getNotificationsForApp(String packageName);

    @Query("SELECT DISTINCT packageName, appName FROM notifications ORDER BY appName ASC")
    LiveData<List<AppInfo>> getDistinctApps();

    @Query("SELECT * FROM notifications GROUP BY packageName, timestamp ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> getGroupedNotifications();

    @Query("SELECT * FROM notifications WHERE id = :id")
    LiveData<NotificationEntity> getNotificationById(long id);

    // Check if auto-reply is disabled for a specific title/chat
    @Query("SELECT EXISTS(SELECT 1 FROM notifications " +
           "WHERE packageName = :packageName AND " +
           "CASE " +
           "  WHEN packageName LIKE '%whatsapp%' AND content LIKE '%[0-9+]%' " +
           "  THEN substr(replace(replace(replace(content, '+', ''), '-', ''), ' ', ''), 1, 11) = :phoneNumber " +
           "  ELSE substr(title, 1, 10) = :titlePrefix " +
           "END " +
           "AND autoReplyDisabled = 1 LIMIT 1)")
    boolean isAutoReplyDisabled(String packageName, String phoneNumber, String titlePrefix);

    @Query("SELECT autoReplyDisabled FROM notifications WHERE packageName = :packageName AND conversationId = :conversationId LIMIT 1")
    boolean isAutoReplyDisabled(String packageName, String conversationId);

    // Update auto-reply disabled status for all matching notifications
    @Query("UPDATE notifications SET autoReplyDisabled = :disabled " +
           "WHERE packageName = :packageName AND " +
           "CASE " +
           "  WHEN packageName LIKE '%whatsapp%' AND content LIKE '%[0-9+]%' " +
           "  THEN substr(replace(replace(replace(content, '+', ''), '-', ''), ' ', ''), 1, 11) = :phoneNumber " +
           "  ELSE substr(title, 1, 10) = :titlePrefix " +
           "END")
    void updateAutoReplyDisabled(String packageName, String phoneNumber, String titlePrefix, boolean disabled);

    // Get all related notifications with similar content or title and same package
    @Query("SELECT * FROM notifications " +
           "WHERE packageName = (SELECT packageName FROM notifications WHERE id = :id) " +
           "AND CASE " +
           "  WHEN packageName LIKE '%whatsapp%' AND " +
           "       content LIKE '%[0-9+]%' AND " +
           "       (SELECT content FROM notifications WHERE id = :id) LIKE '%[0-9+]%' " +
           "  THEN " +
           "    substr(replace(replace(replace(content, '+', ''), '-', ''), ' ', ''), 1, 11) = " +
           "    substr(replace(replace(replace((SELECT content FROM notifications WHERE id = :id), '+', ''), '-', ''), ' ', ''), 1, 11) " +
           "  ELSE " +
           "    substr(title, 1, 10) = (SELECT substr(title, 1, 10) FROM notifications WHERE id = :id) " +
           "END " +
           "ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> getRelatedNotifications(long id);

    // Get related notifications within a time range
    @Query("SELECT * FROM notifications " +
           "WHERE packageName = :packageName " +
           "AND CASE " +
           "  WHEN packageName LIKE '%whatsapp%' AND " +
           "       content LIKE '%[0-9+]%' " +
           "  THEN " +
           "    substr(replace(replace(replace(content, '+', ''), '-', ''), ' ', ''), 1, 11) = :phoneNumber " +
           "  ELSE " +
           "    substr(title, 1, 10) = :titlePrefix " +
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

    // Smart grouping for time range with phone number matching for WhatsApp
    @Query("WITH GroupedNotifs AS (" +
           "  SELECT n1.*, " +
           "         MIN(n1.timestamp) as group_timestamp, " +
           "         COUNT(*) as group_count " +
           "  FROM notifications n1 " +
           "  LEFT JOIN notifications n2 ON " +
           "    n1.packageName = n2.packageName AND " +
           "    CASE " +
           "      WHEN n1.packageName LIKE '%whatsapp%' AND " +
           "           n1.content LIKE '%[0-9+]%' AND " +
           "           n2.content LIKE '%[0-9+]%' " +
           "      THEN " +
           "        substr(replace(replace(replace(n1.content, '+', ''), '-', ''), ' ', ''), 1, 11) = " +
           "        substr(replace(replace(replace(n2.content, '+', ''), '-', ''), ' ', ''), 1, 11) " +
           "      ELSE " +
           "        substr(n1.title, 1, 10) = substr(n2.title, 1, 10) " +
           "    END AND " +
           "    abs(n1.timestamp - n2.timestamp) < 86400000 " +
           "  WHERE n1.timestamp >= :startTime AND n1.timestamp <= :endTime " +
           "  GROUP BY n1.packageName, " +
           "           CASE " +
           "             WHEN n1.packageName LIKE '%whatsapp%' AND n1.content LIKE '%[0-9+]%' " +
           "             THEN substr(replace(replace(replace(n1.content, '+', ''), '-', ''), ' ', ''), 1, 11) " +
           "             ELSE substr(n1.title, 1, 10) " +
           "           END " +
           ") " +
           "SELECT * FROM GroupedNotifs ORDER BY group_timestamp DESC")
    LiveData<List<NotificationEntity>> getSmartGroupedNotificationsInRange(long startTime, long endTime);

    @Query("WITH GroupedNotifs AS (" +
           "  SELECT n1.*, " +
           "         MIN(n1.timestamp) as group_timestamp, " +
           "         COUNT(*) as group_count " +
           "  FROM notifications n1 " +
           "  LEFT JOIN notifications n2 ON " +
           "    n1.packageName = n2.packageName AND " +
           "    CASE " +
           "      WHEN n1.packageName LIKE '%whatsapp%' AND " +
           "           n1.content LIKE '%[0-9+]%' AND " +
           "           n2.content LIKE '%[0-9+]%' " +
           "      THEN " +
           "        substr(replace(replace(replace(n1.content, '+', ''), '-', ''), ' ', ''), 1, 11) = " +
           "        substr(replace(replace(replace(n2.content, '+', ''), '-', ''), ' ', ''), 1, 11) " +
           "      ELSE " +
           "        substr(n1.title, 1, 10) = substr(n2.title, 1, 10) " +
           "    END AND " +
           "    abs(n1.timestamp - n2.timestamp) < 86400000 " +
           "  WHERE n1.timestamp >= strftime('%s', datetime('now', :timeRange)) * 1000 " +
           "  GROUP BY n1.packageName, " +
           "           CASE " +
           "             WHEN n1.packageName LIKE '%whatsapp%' AND n1.content LIKE '%[0-9+]%' " +
           "             THEN substr(replace(replace(replace(n1.content, '+', ''), '-', ''), ' ', ''), 1, 11) " +
           "             ELSE substr(n1.title, 1, 10) " +
           "           END " +
           ") " +
           "SELECT * FROM GroupedNotifs ORDER BY group_timestamp DESC")
    LiveData<List<NotificationEntity>> getSmartGroupedNotificationsByTimeRange(String timeRange);

    // Get yesterday's notifications with smart grouping
    default LiveData<List<NotificationEntity>> getYesterdayNotifications() {
        return getSmartGroupedNotificationsByTimeRange("-1 day");
    }

    // Get notification by ID (non-LiveData version for internal checks)
    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    NotificationEntity getNotificationByIdSync(long id);

    // Get notification by thread ID
    @Query("SELECT * FROM notifications WHERE conversationId = :threadId ORDER BY timestamp DESC LIMIT 1")
    NotificationEntity getNotificationByThreadIdSync(String threadId);
    
    // Atomic upsert operation for notifications
    @Transaction
    default long upsertNotification(NotificationEntity notification) {
        NotificationEntity existing = getNotificationByThreadIdSync(notification.getConversationId());
        if (existing != null) {
            // Update existing notification
            notification.setId(existing.getId());
            update(notification);
            return existing.getId();
        } else {
            // Insert new notification
            // Don't set the ID - let Room handle it
            return insert(notification);
        }
    }
    
    // Atomic check and update operation
    @Transaction
    default NotificationEntity getAndUpdateNotification(String threadId, NotificationEntity notification) {
        return getNotificationByThreadIdSync(threadId);
    }

    // Get all notifications with smart grouping
    @Query("WITH GroupedNotifs AS (" +
           "  SELECT n1.*, " +
           "         MIN(n1.timestamp) as group_timestamp, " +
           "         COUNT(*) as group_count " +
           "  FROM notifications n1 " +
           "  LEFT JOIN notifications n2 ON " +
           "    n1.packageName = n2.packageName AND " +
           "    CASE " +
           "      WHEN n1.packageName LIKE '%whatsapp%' AND " +
           "           n1.content LIKE '%[0-9+]%' AND " +
           "           n2.content LIKE '%[0-9+]%' " +
           "      THEN " +
           "        substr(replace(replace(replace(n1.content, '+', ''), '-', ''), ' ', ''), 1, 11) = " +
           "        substr(replace(replace(replace(n2.content, '+', ''), '-', ''), ' ', ''), 1, 11) " +
           "      ELSE " +
           "        substr(n1.title, 1, 10) = substr(n2.title, 1, 10) " +
           "    END AND " +
           "    abs(n1.timestamp - n2.timestamp) < 86400000 " +
           "  GROUP BY n1.packageName, " +
           "           CASE " +
           "             WHEN n1.packageName LIKE '%whatsapp%' AND n1.content LIKE '%[0-9+]%' " +
           "             THEN substr(replace(replace(replace(n1.content, '+', ''), '-', ''), ' ', ''), 1, 11) " +
           "             ELSE substr(n1.title, 1, 10) " +
           "           END " +
           ") " +
           "SELECT * FROM GroupedNotifs ORDER BY group_timestamp DESC")
    LiveData<List<NotificationEntity>> getSmartGroupedNotifications();
}
