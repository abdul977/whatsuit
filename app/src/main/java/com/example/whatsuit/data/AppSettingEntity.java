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

    public AppSettingEntity(@NonNull String packageName, String appName, boolean autoReplyEnabled) {
        this.packageName = packageName;
        this.appName = appName;
        this.autoReplyEnabled = autoReplyEnabled;
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
}