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

    @Query("SELECT DISTINCT packageName, appName, COUNT(*) as notificationCount " +
           "FROM notifications " +
           "GROUP BY packageName, appName " +
           "ORDER BY appName ASC")
    LiveData<List<AppInfo>> getDistinctAppsWithCount();

    @Query("SELECT * FROM notifications WHERE id = :id")
    LiveData<NotificationEntity> getNotificationById(long id);

    // Check if auto-reply is disabled for a specific title/chat
    @Query("SELECT EXISTS(SELECT 1 FROM notifications " +
           "WHERE packageName = :packageName AND " +
           "conversationId = CASE " +
           "  WHEN packageName LIKE '%whatsapp%' THEN :phoneNumber " +
           "  ELSE :titlePrefix " +
           "END " +
           "AND autoReplyDisabled = 1 LIMIT 1)")
    boolean isAutoReplyDisabled(String packageName, String phoneNumber, String titlePrefix);

    @Query("SELECT autoReplyDisabled FROM notifications WHERE packageName = :packageName AND conversationId = :conversationId LIMIT 1")
    boolean isAutoReplyDisabled(String packageName, String conversationId);

    // Update auto-reply disabled status for all matching notifications
    @Query("UPDATE notifications SET autoReplyDisabled = :disabled " +
           "WHERE packageName = :packageName AND " +
           "conversationId = CASE " +
           "  WHEN packageName LIKE '%whatsapp%' THEN :phoneNumber " +
           "  ELSE :titlePrefix " +
           "END")
    void updateAutoReplyDisabled(String packageName, String phoneNumber, String titlePrefix, boolean disabled);

    // Smart grouping for time range with app-level primary grouping
    @Query("WITH AppGroups AS (" +
           "  SELECT n1.*, " +
           "         n1.packageName as app_package, " +
           "         n1.appName as app_name, " +
           "         COUNT(*) as group_count, " +
           "         MIN(n1.timestamp) as group_timestamp " +
           "  FROM notifications n1 " +
           "  WHERE n1.timestamp >= :startTime AND n1.timestamp <= :endTime " +
           "  GROUP BY n1.packageName, n1.appName " +
           "  ORDER BY n1.appName ASC " +
           "), " +
           "ConversationGroups AS ( " +
           "  SELECT n2.*, " +
           "         cg.app_package, " +
           "         cg.app_name, " +
           "         cg.group_count, " +
           "         cg.group_timestamp, " +
           "         COUNT(*) as conversation_count " +
           "  FROM notifications n2 " +
           "  JOIN AppGroups cg ON n2.packageName = cg.app_package " +
           "  GROUP BY " +
           "    n2.packageName, " +
           "    CASE " +
           "      WHEN n2.packageName LIKE '%whatsapp%' " +
           "      THEN n2.conversationId " +
           "      ELSE CASE " +
           "        WHEN n2.title IS NOT NULL " +
           "        THEN n2.title " +
           "        ELSE 'Unknown' " +
           "      END " +
           "    END " +
           "  ORDER BY n2.timestamp DESC" +
           ") " +
           "SELECT * FROM ConversationGroups")
    LiveData<List<NotificationEntity>> getSmartGroupedNotificationsInRange(long startTime, long endTime);

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    NotificationEntity getNotificationByIdSync(long id);

    @Query("SELECT * FROM notifications WHERE conversationId = :threadId ORDER BY timestamp DESC LIMIT 1")
    NotificationEntity getNotificationByThreadIdSync(String threadId);

    @Query("SELECT n.* FROM notifications n " +
           "JOIN notifications source ON (source.id = :notificationId) " +
           "WHERE n.packageName = source.packageName AND " +
           "(n.conversationId = source.conversationId OR " +
           "(n.title LIKE '%' || source.title || '%' OR source.title LIKE '%' || n.title || '%')) " +
           "AND n.id != :notificationId " +
           "ORDER BY n.timestamp DESC")
    LiveData<List<NotificationEntity>> getRelatedNotifications(long notificationId);

    @Query("SELECT * FROM notifications " +
           "WHERE packageName = :packageName AND " +
           "timestamp BETWEEN :startTime AND :endTime AND " +
           "(:phoneNumber = '' OR conversationId LIKE '%' || :phoneNumber || '%' OR title LIKE '%' || :phoneNumber || '%') AND " +
           "(:titlePrefix = '' OR title LIKE '%' || :titlePrefix || '%') " +
           "ORDER BY timestamp DESC")
    LiveData<List<NotificationEntity>> getRelatedNotificationsByTimeRange(
            String packageName, 
            String phoneNumber, 
            String titlePrefix, 
            long startTime, 
            long endTime);

    @Transaction
    default long upsertNotification(NotificationEntity notification) {
        NotificationEntity existing = getNotificationByThreadIdSync(notification.getConversationId());
        if (existing != null) {
            notification.setId(existing.getId());
            update(notification);
            return existing.getId();
        } else {
            return insert(notification);
        }
    }

    @Transaction
    default NotificationEntity getAndUpdateNotification(String threadId, NotificationEntity notification) {
        return getNotificationByThreadIdSync(threadId);
    }
}
