package com.example.whatsuit.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_settings")
public class AppSettingEntity {
    @PrimaryKey
    @NonNull
    private String packageName;
    
    private String appName;
    private boolean autoReplyEnabled;
    private boolean autoReplyGroupsEnabled;

    public AppSettingEntity(@NonNull String packageName, String appName, boolean autoReplyEnabled, boolean autoReplyGroupsEnabled) {
        this.packageName = packageName;
        this.appName = appName;
        this.autoReplyEnabled = autoReplyEnabled;
<<<<<<< Updated upstream
        this.autoReplyGroupsEnabled = autoReplyGroupsEnabled;
=======
        this.autoReplyGroupsEnabled = true; // Enable by default
>>>>>>> Stashed changes
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(@NonNull String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public boolean isAutoReplyEnabled() {
        return autoReplyEnabled;
    }

    public void setAutoReplyEnabled(boolean autoReplyEnabled) {
        this.autoReplyEnabled = autoReplyEnabled;
    }

    public boolean isAutoReplyGroupsEnabled() {
        return autoReplyGroupsEnabled;
    }

    public void setAutoReplyGroupsEnabled(boolean autoReplyGroupsEnabled) {
        this.autoReplyGroupsEnabled = autoReplyGroupsEnabled;
    }
}
