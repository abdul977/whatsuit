# Android Compatibility Changes

This document outlines the changes made to support Android 7.0 (API 24) through Android 15 (API 35).

## Build Configuration Changes

1. Updated `minSdk` from 34 to 24 in `app/build.gradle.kts`
2. Added MultiDex support for older Android versions
3. Added compatibility libraries:
   - androidx.multidex:multidex:2.0.1
   - androidx.legacy:legacy-support-v4:1.0.0
   - androidx.core:core:1.12.0
   - com.karumi:dexter:6.2.3 (for permission handling)

## Permission Handling

1. Created `PermissionManager.java` to handle runtime permissions
2. Updated `AndroidManifest.xml` to properly declare permissions for different Android versions:
   - Basic permissions for all versions
   - Storage permissions with appropriate maxSdkVersion
   - Notification permissions for Android 13+ (API 33+)
   - Foreground service permissions for Android 11+ (API 30+)
   - Foreground service data sync permissions for Android 14+ (API 34+)

3. Added permission checks in `MainActivity.java`:
   - Storage permissions
   - Notification permissions (Android 13+)
   - Notification listener service

## Application Class

1. Created `WhatSuitApplication.java` extending `MultiDexApplication` to:
   - Initialize MultiDex for Android versions below 5.0
   - Handle application-wide initialization

## UI Compatibility

1. Updated splash screen handling in `MainActivity.java`:
   - Use SplashScreen API for Android 12+ (API 31+)
   - Use custom implementation for older versions

2. Updated EdgeToEdge handling:
   - Only enable on Android 10+ (API 29+)

3. Added styles for older Android versions:
   - Created `values-v24/styles.xml` for Android 7.0+ styling

## Notification Handling

1. Updated `NotificationService.java` to handle notifications differently based on Android version:
   - Added version checks for RemoteInput handling
   - Added compatibility code for Android 7+ (API 24+)

## Testing Considerations

When testing the app, verify:

1. App installs and runs on Android 7.0 (API 24) devices
2. Permissions are properly requested on first run
3. Notification listener works correctly
4. UI elements display properly
5. Auto-reply functionality works on supported messaging apps
6. Storage access works correctly

## Known Limitations

1. Some newer Android features may have limited functionality on older devices
2. UI may look slightly different on older Android versions
3. Some animations and transitions may be simplified on older versions
