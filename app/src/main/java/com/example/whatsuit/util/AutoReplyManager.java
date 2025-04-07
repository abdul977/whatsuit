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
    private Context applicationContext; // Strong reference to application context
    private volatile ExecutorService executor;
    private volatile NotificationEntity currentNotification;
    private volatile MenuItem autoReplyMenuItem;

    public static AutoReplyManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AutoReplyManager.class) {
                if (instance == null) {
                    // Always use application context to prevent memory leaks
                    Context appContext = context.getApplicationContext();
                    instance = new AutoReplyManager(appContext);
                }
            }
        }
        return instance;
    }

    public interface AutoReplyCallback {
        void onStatusChanged(boolean isDisabled);
    }

    public AutoReplyManager(Context context) {
        // Store both a weak reference and a strong reference to the application context
        this.contextRef = new WeakReference<>(context);
        // Ensure we're using application context to prevent memory leaks
        if (context != null) {
            this.applicationContext = context.getApplicationContext();
        }
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

            // Check if executor is null and recreate if necessary
            if (executor == null) {
                Log.w(TAG, "Executor was null in updateMenuTitle, recreating it");
                executor = Executors.newSingleThreadExecutor();
            }

            // Use final reference to avoid potential race conditions
            final ExecutorService currentExecutor = executor;

            // Check again to be safe
            if (currentExecutor == null) {
                Log.e(TAG, "Executor is still null after recreation attempt in updateMenuTitle");
                return;
            }

            currentExecutor.execute(() -> {
                // Try to get context from weak reference first
                Context context = contextRef.get();

                // If weak reference is null, fall back to application context
                if (context == null) {
                    context = applicationContext;
                    Log.d(TAG, "Using application context as fallback for menu update");
                }

                // If both references are null, we can't proceed
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
        // Check if executor is null and recreate if necessary
        if (executor == null) {
            Log.w(TAG, "Executor was null in toggleAutoReply, recreating it");
            executor = Executors.newSingleThreadExecutor();
        }

        // Use final reference to avoid potential race conditions
        final ExecutorService currentExecutor = executor;

        // Check again to be safe
        if (currentExecutor == null) {
            Log.e(TAG, "Executor is still null after recreation attempt in toggleAutoReply");
            if (callback != null) {
                callback.onStatusChanged(false);
            }
            return;
        }

        currentExecutor.execute(() -> {
            if (conversationId == null) {
                Log.e(TAG, "Cannot toggle auto-reply: conversation ID is null");
                if (callback != null) {
                    callback.onStatusChanged(false);
                }
                return;
            }

            // Try to get context from weak reference first
            Context context = contextRef.get();

            // If weak reference is null, fall back to application context
            if (context == null) {
                context = applicationContext;
                Log.d(TAG, "Using application context as fallback for toggle");
            }

            // If both references are null, we can't proceed
            if (context == null) {
                Log.e(TAG, "Context is null when toggling auto-reply state");
                if (callback != null) {
                    callback.onStatusChanged(false);
                }
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
        // Check if executor is null and recreate if necessary
        if (executor == null) {
            Log.w(TAG, "Executor was null in isAutoReplyDisabled, recreating it");
            executor = Executors.newSingleThreadExecutor();
        }

        // Use final reference to avoid potential race conditions
        final ExecutorService currentExecutor = executor;

        // Check again to be safe
        if (currentExecutor == null) {
            Log.e(TAG, "Executor is still null after recreation attempt");
            if (callback != null) {
                // Default to enabled state when executor is null
                callback.onStatusChanged(false);
            }
            return;
        }

        currentExecutor.execute(() -> {
            // Try to get context from weak reference first
            Context context = contextRef.get();

            // Add validation logging
            Log.d(TAG, "isAutoReplyDisabled called with:" +
                      "\nPackage: " + packageName +
                      "\nPhone: " + phoneNumber +
                      "\nTitle Prefix: " + titlePrefix);

            // If weak reference is null, fall back to application context
            if (context == null) {
                context = applicationContext;
                Log.d(TAG, "Using application context as fallback");
            }

            // If both references are null, we can't proceed
            if (context == null) {
                Log.e(TAG, "Context is null when checking auto-reply state");
                if (callback != null) {
                    // Default to enabled state when context is null
                    callback.onStatusChanged(false);
                }
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

                // Force shutdown if there was an error
                try {
                    executorToShutdown.shutdownNow();
                } catch (Exception e2) {
                    Log.e(TAG, "Error during forced shutdown", e2);
                }
            }
            executor = null;
        }
        // Clear references
        contextRef.clear();
        // Don't clear application context as it's needed for the singleton
        // applicationContext = null;
        currentNotification = null;
        autoReplyMenuItem = null;
    }
}
