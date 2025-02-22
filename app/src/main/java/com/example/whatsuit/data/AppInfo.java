package com.example.whatsuit.data;

import androidx.room.ColumnInfo;

public class AppInfo {
    @ColumnInfo(name = "packageName")
    private String packageName;

    @ColumnInfo(name = "appName")
    private String appName;

    // Required by Room
    public AppInfo(String packageName, String appName) {
        this.packageName = packageName;
        this.appName = appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }
}
