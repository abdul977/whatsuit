package com.example.whatsuit.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NotificationDao {
    @Insert
    void insert(NotificationEntity notification);

    @Delete
    void delete(NotificationEntity notification);

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
}
