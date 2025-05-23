package com.example.whatsuit.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AppSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AppSettingEntity setting);

    @Update
    void update(AppSettingEntity setting);

    @Query("SELECT * FROM app_settings")
    LiveData<List<AppSettingEntity>> getAllSettings();

    @Query("SELECT * FROM app_settings WHERE packageName = :packageName")
    AppSettingEntity getAppSetting(String packageName);

    @Query("SELECT autoReplyEnabled FROM app_settings WHERE packageName = :packageName")
    boolean isAutoReplyEnabled(String packageName);

    @Query("SELECT autoReplyGroupsEnabled FROM app_settings WHERE packageName = :packageName")
    boolean isAutoReplyGroupsEnabled(String packageName);

    @Query("DELETE FROM app_settings WHERE packageName = :packageName")
    void delete(String packageName);

    // Synchronous methods for backup/restore
    @Query("SELECT * FROM app_settings")
    List<AppSettingEntity> getAllSettingsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AppSettingEntity> settings);
}
