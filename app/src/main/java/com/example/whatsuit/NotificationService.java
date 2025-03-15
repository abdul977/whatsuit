package com.example.whatsuit;

import android.app.Notification.Action;
import android.app.RemoteInput;
import android.content.Intent;
import android.os.Build;
import android.content.SharedPreferences;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.AppSettingDao;
import com.example.whatsuit.data.AppSettingEntity;
import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.service.GeminiService;
import com.example.whatsuit.util.SentMessageTracker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationService extends NotificationListenerService {
    private static final String TAG = "NotificationService";
    private AppDatabase database;
    private ExecutorService executorService;
    private GeminiService geminiService;
    private SharedPreferences prefs;
    private final Set<String> recentlyProcessedConversations = Collections.synchronizedSet(new HashSet<>());
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Add this constant
    private static final String PREF_THROTTLE_DELAY = "auto_reply_throttle_delay_ms";
    private static final int DEFAULT_THROTTLE_DELAY_MS = 500; // 0.5 seconds instead of default

    @Override
    public void onCreate() {
        super.onCreate();
        database = AppDatabase.getDatabase(this);
        executorService = Executors.newSingleThreadExecutor();
        geminiService = new GeminiService(this);
        prefs = getSharedPreferences("whatsuit_settings", MODE_PRIVATE);
        
        // Initialize Gemini service
        executorService.execute(() -> {
            try {
                geminiService.initializeFromJava();  // Use the non-suspend version
                Log.d(TAG, "GeminiService initialization triggered");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing GeminiService", e);
            }
        });
        
        Log.d(TAG, "NotificationService created");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;
            
            String title = extras.getString(Notification.EXTRA_TITLE, "");
            String text = extras.getString(Notification.EXTRA_TEXT, "");
            
            // Skip if this is our own sent message
            if (SentMessageTracker.getInstance().isLikelyOwnMessage(text)) {
                Log.d(TAG, "Skipping processing of our own message: " + text);
                return;
            }
            
            String packageName = sbn.getPackageName();
            
            // Get app name from package name
            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            String appName = packageManager.getApplicationLabel(applicationInfo).toString();
            
            // Create notification entity
            NotificationEntity entity = new NotificationEntity();
            entity.setTitle(title);
            entity.setContent(text);
            entity.setPackageName(packageName);
            entity.setAppName(appName);
            entity.setTimestamp(sbn.getPostTime());
            
            // Generate conversation ID
            String conversationId = generateConversationId(entity);
            entity.setConversationId(conversationId);

            // Log notification details
            Log.i(TAG, "\n====== NEW NOTIFICATION RECEIVED ======" +
                "\nApp: " + appName +
                "\nPackage: " + packageName +
                "\nTitle: " + title +
                "\nContent: " + text +
                "\nConversation ID: " + conversationId +
                "\nTimestamp: " + new java.util.Date(sbn.getPostTime()) +
                "\n===================================");

            // Insert or update notification in database using background thread
            executorService.execute(() -> {
                try {
                    // Use upsertNotification instead of insert to handle duplicates
                    long notificationId = database.notificationDao().upsertNotification(entity);
                    entity.setId(notificationId);
                    
                    // Check if auto-reply is enabled
                    if (shouldAutoReply(packageName)) {
                        handleAutoReply(sbn, entity, notification);
                    }
                    
                    Log.d(TAG, "Notification processed: " + title);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing notification", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error processing notification", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Handle notification removal if needed
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "NotificationListener connected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "NotificationListener disconnected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
        if (geminiService != null) {
            geminiService.shutdown();
        }
    }

    private String generateConversationId(NotificationEntity entity) {
        if (entity.getPackageName().contains("whatsapp")) {
            String title = entity.getTitle();
            if (title.matches(".*[0-9+].*")) {
                // For phone number based titles, normalize the number
                String normalizedNumber = title.replaceAll("[^0-9+]", "");
                // Keep only last 11 digits if longer
                if (normalizedNumber.length() > 11) {
                    normalizedNumber = normalizedNumber.substring(normalizedNumber.length() - 11);
                }
                return "whatsapp_" + normalizedNumber;
            } else {
                // For name-based titles (e.g., "John Emialin"), use normalized name
                String normalizedName = title.trim().toLowerCase().replaceAll("\\s+", "_");
                return "whatsapp_contact_" + normalizedName;
            }
        }
        
        // For other apps, use package and normalized title
        return entity.getPackageName() + "_" + entity.getTitle().trim().toLowerCase().replaceAll("\\s+", "_");
    }

    private boolean shouldAutoReply(String packageName) {
        // Check global auto-reply setting
        if (!prefs.getBoolean("auto_reply_enabled", true)) {
            return false;
        }

        try {
            // Check app-specific settings
            AppSettingEntity appSetting = database.appSettingDao().getAppSetting(packageName);
            return appSetting != null && appSetting.isAutoReplyEnabled();
        } catch (Exception e) {
            Log.e(TAG, "Error checking auto-reply settings", e);
            return false;
        }
    }

    private void handleAutoReply(StatusBarNotification sbn, NotificationEntity entity, Notification notification) {
        try {
            // Check if we've already replied to this notification
            if (entity.isAutoReplied()) {
                Log.d(TAG, "Notification already auto-replied, skipping duplicate reply");
                return;
            }
            
            // Get conversation ID for better tracking
            String conversationId = entity.getConversationId();
            
            // Add additional check for recently processed conversations
            if (recentlyProcessedConversations.contains(conversationId)) {
                Log.d(TAG, "Conversation ID " + conversationId + " was just processed, skipping to prevent duplicate replies");
                return;
            }
            
            // Add this conversation to the recently processed list
            recentlyProcessedConversations.add(conversationId);

            // Get throttle delay from preferences with default value
            int throttleDelay = prefs.getInt(PREF_THROTTLE_DELAY, DEFAULT_THROTTLE_DELAY_MS);

            // Schedule removal after the configurable delay
            handler.postDelayed(() -> recentlyProcessedConversations.remove(conversationId), throttleDelay);

            // Find actions that can be used to reply
            Action[] actions = notification.actions;
            if (actions == null) {
                Log.d(TAG, "No actions found for auto-reply");
                return;
            }

            Action replyAction = null;
            RemoteInput remoteInput = null;

            // Find action with remote input
            for (Action action : actions) {
                RemoteInput[] remoteInputs = action.getRemoteInputs();
                if (remoteInputs != null && remoteInputs.length > 0) {
                    replyAction = action;
                    remoteInput = remoteInputs[0];
                    break;
                }
            }

            if (replyAction == null || remoteInput == null) {
                Log.d(TAG, "No reply action or remote input found");
                return;
            }

            final Action finalReplyAction = replyAction;
            final RemoteInput finalRemoteInput = remoteInput;

            // Generate reply using Gemini
            geminiService.generateReply(entity.getId(), entity.getContent(), new GeminiService.ResponseCallback() {
                @Override
                public void onPartialResponse(String text) {
                    // Not needed for auto-reply
                }

                @Override
                public void onComplete(String response) {
                    try {
                        // Create the remote input bundle
                        Bundle resultsBundle = new Bundle();
                        resultsBundle.putString(finalRemoteInput.getResultKey(), response);

                        // Create intent to send the reply
                        Intent intent = new Intent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        
                        // Send the reply using the action
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            RemoteInput.addResultsToIntent(new RemoteInput[]{finalRemoteInput}, intent, resultsBundle);
                        } else {
                            Bundle wrapper = new Bundle();
                            wrapper.putBundle(RemoteInput.RESULTS_CLIP_LABEL, resultsBundle);
                            intent.putExtra(RemoteInput.EXTRA_RESULTS_DATA, wrapper);
                        }
                        
                        finalReplyAction.actionIntent.send(NotificationService.this, 0, intent);
                        
                        // Record the sent message to prevent feedback loops
                        SentMessageTracker.getInstance().recordSentMessage(response);

                        // Update notification entity with auto-reply info
                        executorService.execute(() -> {
                            try {
                                entity.setAutoReplied(true);
                                entity.setAutoReplyContent(response);
                                database.notificationDao().upsertNotification(entity);
                    Log.i(TAG, "\n====== AUTO-REPLY SENT ======" +
                        "\nNotification ID: " + entity.getId() +
                        "\nConversation ID: " + entity.getConversationId() +
                        "\nOriginal Message: " + entity.getContent() +
                        "\nAuto-Reply: " + response +
                        "\nTimestamp: " + new java.util.Date() +
                        "\n===========================");
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating notification after auto-reply", e);
                            }
                        });

                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "Error sending auto-reply", e);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    Log.e(TAG, "Error generating auto-reply", error);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error handling auto-reply", e);
        }
    }
}
