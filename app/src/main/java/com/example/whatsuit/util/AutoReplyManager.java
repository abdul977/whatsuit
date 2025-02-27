package com.example.whatsuit.util;

import android.content.Context;
import android.view.MenuItem;
import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.NotificationEntity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoReplyManager {
    private final Context context;
    private final ExecutorService executor;
    private NotificationEntity currentNotification;
    private MenuItem autoReplyMenuItem;

    public interface AutoReplyCallback {
        void onStatusChanged(boolean isDisabled);
    }

    public AutoReplyManager(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void setCurrentNotification(NotificationEntity notification) {
        this.currentNotification = notification;
    }

    public void setMenuItem(MenuItem menuItem) {
        this.autoReplyMenuItem = menuItem;
        updateMenuTitle();
    }

    private void updateMenuTitle() {
        if (autoReplyMenuItem != null && currentNotification != null) {
            ExtractedInfo info = extractIdentifierInfo(currentNotification);
            
            executor.execute(() -> {
                boolean isDisabled = AppDatabase.getDatabase(context).notificationDao()
                    .isAutoReplyDisabled(currentNotification.getPackageName(), 
                                     info.phoneNumber, 
                                     info.titlePrefix);
                
                autoReplyMenuItem.setTitle(isDisabled ? "Enable Auto-Reply" : "Disable Auto-Reply");
            });
        }
    }

    public void toggleAutoReply(AutoReplyCallback callback) {
        if (currentNotification == null) return;
        ExtractedInfo info = extractIdentifierInfo(currentNotification);
        toggleAutoReply(currentNotification.getPackageName(), info.phoneNumber, info.titlePrefix, callback);
    }

    public void toggleAutoReply(String packageName, String phoneNumber, String titlePrefix, AutoReplyCallback callback) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            boolean currentState = db.notificationDao()
                .isAutoReplyDisabled(packageName, phoneNumber, titlePrefix);
            
            db.notificationDao()
                .updateAutoReplyDisabled(packageName, phoneNumber, titlePrefix, !currentState);
            
            callback.onStatusChanged(!currentState);
        });
    }

    public void isAutoReplyDisabled(String packageName, String phoneNumber, String titlePrefix, AutoReplyCallback callback) {
        executor.execute(() -> {
            boolean isDisabled = AppDatabase.getDatabase(context).notificationDao()
                .isAutoReplyDisabled(packageName, phoneNumber, titlePrefix);
            
            if (callback != null) {
                callback.onStatusChanged(isDisabled);
            }
        });
    }

    private static class ExtractedInfo {
        final String phoneNumber;
        final String titlePrefix;

        ExtractedInfo(String phoneNumber, String titlePrefix) {
            this.phoneNumber = phoneNumber;
            this.titlePrefix = titlePrefix;
        }
    }

    private ExtractedInfo extractIdentifierInfo(NotificationEntity notification) {
        String phoneNumber = "";
        String titlePrefix = "";
        
        if (notification.getPackageName().contains("whatsapp") && 
            notification.getContent() != null && 
            notification.getContent().matches(".*[0-9+].*")) {
            
            String content = notification.getContent();
            // Extract numbers, plus signs, and hyphens, then clean up
            String extracted = content.replaceAll("[^0-9+\\-]", "");
            // Remove all non-digits for final comparison
            phoneNumber = extracted.replaceAll("[^0-9]", "");
        } else {
            String title = notification.getTitle();
            if (title != null && !title.isEmpty()) {
                titlePrefix = title.substring(0, Math.min(5, title.length()));
            }
        }
        
        return new ExtractedInfo(phoneNumber, titlePrefix);
    }

    public void shutdown() {
        executor.shutdown();
    }
}