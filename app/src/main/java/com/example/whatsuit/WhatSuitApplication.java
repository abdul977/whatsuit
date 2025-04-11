package com.example.whatsuit;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

/**
 * Custom Application class to handle MultiDex initialization for Android versions below 5.0
 * and other application-wide initializations.
 */
public class WhatSuitApplication extends MultiDexApplication {
    private static final String TAG = "WhatSuitApplication";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // Initialize MultiDex for Android versions below 5.0 (API 21)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "Initializing MultiDex for pre-Lollipop device");
            MultiDex.install(this);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application created, Android SDK version: " + Build.VERSION.SDK_INT);
        
        // Initialize any application-wide components here
    }
}
