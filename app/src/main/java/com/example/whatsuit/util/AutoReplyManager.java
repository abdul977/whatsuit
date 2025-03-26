package com.example.whatsuit.util;

import android.content.Context;
import android.util.Log;
import android.view.MenuItem;
import com.example.whatsuit.data.AutoReplySettings;
import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.data.AutoReplySettings;
import com.example.whatsuit.data.AutoReplySettingsDao;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.lang.ref.WeakReference;

public class AutoReplyManager {
    private static final String TAG = "AutoReplyManager";
    private static volatile AutoReplyManager instance;
    private WeakReference<Context> contextRef;
    private volatile ExecutorService executor;
    private volatile NotificationEntity currentNotification;
    private volatile MenuItem autoReplyMenuItem;

    public static AutoReplyManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AutoReplyManager.class) {
                if (instance == null) {
                    instance = new AutoReplyManager(context);
                }
            }
        }
        return instance;
    }

    public interface AutoReplyCallback {
        void onStatusChanged(boolean isDisabled);
    }

    public AutoReplyManager(Context context) {
        this.contextRef = new WeakReference<>(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void setCurrentNotification(NotificationEntity notification) {
        this.currentNotification = notification;
        Log.d(TAG, "Set current notification: " + 
              "\nPackage: " + notification.getPackageName() + 
              "\nTitle: " + notification.getTitle() + 
              "\nContent: " + notification.getContent());
    }

    public void setMenuItem(MenuItem menuItem) {
        this.autoReplyMenuItem = menuItem;
        updateMenuTitle();
    }

    private void updateMenuTitle() {
        if (autoReplyMenuItem != null && currentNotification != null) {
            ExtractedInfo info = extractIdentifierInfo(currentNotification);
            
            Log.d(TAG, "Updating menu title for notification: " + 
                      "\nPackage: " + currentNotification.getPackageName() +
                      "\nTitle: " + currentNotification.getTitle() +
                      "\nExtracted Phone: " + info.phoneNumber +
                      "\nTitle Prefix: " + info.titlePrefix);
            
            executor.execute(() -> {
                Context context = contextRef.get();
                if (context == null) {
                    Log.e(TAG, "Context is null when updating menu title");
                    return;
                }
                AutoReplySettingsDao settingsDao = AppDatabase.getDatabase(context).autoReplySettingsDao();
                AutoReplySettings settings = settingsDao.getByConversationIdBlocking(currentNotification.getConversationId());
                
                boolean isDisabled = settings != null && settings.isDisabled();
                
                Log.d(TAG, "Auto-reply is currently " + (isDisabled ? "disabled" : "enabled") + 
                          " for this chat");
                
                autoReplyMenuItem.setTitle(isDisabled ? "Enable Auto-Reply" : "Disable Auto-Reply");
            });
        }
    }

    public void toggleAutoReply(AutoReplyCallback callback) {
        if (currentNotification == null) {
            Log.e(TAG, "Cannot toggle auto-reply: current notification is null");
            return;
        }
        ExtractedInfo info = extractIdentifierInfo(currentNotification);
        Log.d(TAG, "Toggling auto-reply for: " +
                  "\nPackage: " + currentNotification.getPackageName() +
                  "\nTitle: " + currentNotification.getTitle() +
                  "\nPhone Number: " + info.phoneNumber +
                  "\nTitle Prefix: " + info.titlePrefix);
        
        // Generate conversationId before async operation to avoid race condition
        String conversationId = ConversationIdGenerator.generate(currentNotification);
        Log.d(TAG, "Conversation ID comparison:" +
                   "\nStored ID: " + currentNotification.getConversationId() +
                   "\nGenerated ID: " + conversationId +
                   "\nTitle: " + currentNotification.getTitle() +
                   "\nPrefix: " + info.titlePrefix);
        
        toggleAutoReply(currentNotification.getPackageName(), info.phoneNumber, info.titlePrefix, conversationId, callback);
    }

    public void toggleAutoReply(String packageName, String phoneNumber, String titlePrefix, String conversationId, AutoReplyCallback callback) {
        executor.execute(() -> {
            if (conversationId == null) {
                Log.e(TAG, "Cannot toggle auto-reply: conversation ID is null");
                return;
            }
            Context context = contextRef.get();
            if (context == null) {
                Log.e(TAG, "Context is null when toggling auto-reply state");
                return;
            }
            AppDatabase db = AppDatabase.getDatabase(context);
            AutoReplySettingsDao settingsDao = db.autoReplySettingsDao();
            AutoReplySettings settings = settingsDao.getByConversationIdBlocking(conversationId);
            
            boolean currentState = settings != null && settings.isDisabled();
            
            Log.d(TAG, "Toggling auto-reply state from " + (currentState ? "disabled" : "enabled") + 
                      " to " + (!currentState ? "disabled" : "enabled") +
                      "\nConversation ID: " + conversationId);
            
            settingsDao.upsertBlocking(new AutoReplySettings(
                conversationId, 
                !currentState,
                System.currentTimeMillis()));
            
            callback.onStatusChanged(!currentState);
        });
    }

    public void isAutoReplyDisabled(String packageName, String phoneNumber, String titlePrefix, AutoReplyCallback callback) {
        executor.execute(() -> {
            Context context = contextRef.get();
            // Add validation logging
            Log.d(TAG, "isAutoReplyDisabled called with:" +
                      "\nPackage: " + packageName +
                      "\nPhone: " + phoneNumber +
                      "\nTitle Prefix: " + titlePrefix);

            if (context == null) {
                Log.e(TAG, "Context is null when checking auto-reply state");
                return;
            }
            
            String conversationId = ConversationIdGenerator.generate(packageName, phoneNumber, titlePrefix);
            
            Log.d(TAG, "Checking auto-reply state for conversation:" +
                      "\nID: " + conversationId +
                      "\nOriginal Title: " + titlePrefix);
            
            AutoReplySettingsDao settingsDao = AppDatabase.getDatabase(context).autoReplySettingsDao();
            AutoReplySettings settings = settingsDao.getByConversationIdBlocking(conversationId);
            
            boolean isDisabled = settings != null && settings.isDisabled();
            
            Log.d(TAG, "Checking auto-reply state: " + (isDisabled ? "disabled" : "enabled") +
                      "\nPackage: " + packageName +
                      "\nPhone: " + phoneNumber +
                      "\nTitle Prefix: " + titlePrefix +
                      "\nConversation ID: " + conversationId);
            
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
        Log.d(TAG, "Extracting identifier info for package: " + notification.getPackageName());
        String title = notification.getTitle();
        
        Log.d(TAG, "Original title: " + title);

        if (NotificationUtils.isWhatsAppPackage(notification.getPackageName()) && 
            NotificationUtils.hasPhoneNumber(title)) {
            String phoneNumber = NotificationUtils.normalizePhoneNumber(title);
            Log.d(TAG, "WhatsApp notification with number in title, normalized phone: " + phoneNumber);
            return new ExtractedInfo(phoneNumber, "");
        } else {
            String titlePrefix = NotificationUtils.getTitlePrefix(title);
            Log.d(TAG, "Using title prefix for matching: " + titlePrefix);
            return new ExtractedInfo("", titlePrefix);
        }
    }

    public void shutdown() {
        Log.d(TAG, "Shutting down AutoReplyManager");
        ExecutorService executorToShutdown = executor;
        if (executorToShutdown != null) {
            try {
                // First attempt to finish any pending operations
                executorToShutdown.submit(() -> {}).get();
                executorToShutdown.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error during shutdown", e);
                e.printStackTrace();
            }
            executor = null;
        }
        // Clear references
        contextRef.clear();
        currentNotification = null;
        autoReplyMenuItem = null;
    }
}
