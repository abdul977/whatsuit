package com.example.whatsuit.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to handle runtime permissions across different Android versions
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";

    /**
     * Check and request storage permissions based on Android version
     * @param activity The activity requesting permissions
     * @param callback Callback to be invoked after permission check
     */
    public static void checkStoragePermissions(Activity activity, PermissionCallback callback) {
        List<String> permissions = new ArrayList<>();

        // For Android 10 (API 29) and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } 
        // For Android 11-12 (API 30-32)
        else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        // For Android 13+ (API 33+)
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        }

        if (permissions.isEmpty()) {
            // No permissions needed for this Android version
            callback.onPermissionResult(true);
            return;
        }

        requestPermissions(activity, permissions.toArray(new String[0]), callback);
    }

    /**
     * Check and request notification permissions for Android 13+
     * @param activity The activity requesting permissions
     * @param callback Callback to be invoked after permission check
     */
    public static void checkNotificationPermission(Activity activity, PermissionCallback callback) {
        // Only needed for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, callback);
        } else {
            // Permission not needed for older versions
            callback.onPermissionResult(true);
        }
    }

    /**
     * Request multiple permissions using Dexter library
     * @param activity The activity requesting permissions
     * @param permissions Array of permissions to request
     * @param callback Callback to be invoked after permission check
     */
    private static void requestPermissions(Activity activity, String[] permissions, PermissionCallback callback) {
        Dexter.withContext(activity)
                .withPermissions(permissions)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            Log.d(TAG, "All permissions granted");
                            callback.onPermissionResult(true);
                        } else {
                            Log.d(TAG, "Some permissions denied");
                            if (report.isAnyPermissionPermanentlyDenied()) {
                                showSettingsDialog(activity, callback);
                            } else {
                                callback.onPermissionResult(false);
                            }
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                })
                .check();
    }

    /**
     * Show dialog to direct user to app settings when permissions are permanently denied
     * @param activity The activity context
     * @param callback Callback to be invoked after dialog interaction
     */
    private static void showSettingsDialog(Activity activity, PermissionCallback callback) {
        new AlertDialog.Builder(activity)
                .setTitle("Permission Required")
                .setMessage("Some permissions are needed for this app to function properly. Please grant them in app settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    dialog.dismiss();
                    openAppSettings(activity);
                    callback.onPermissionResult(false);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    callback.onPermissionResult(false);
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Open app settings page
     * @param activity The activity context
     */
    private static void openAppSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    /**
     * Callback interface for permission results
     */
    public interface PermissionCallback {
        void onPermissionResult(boolean granted);
    }
}
