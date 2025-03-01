package com.example.whatsuit;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.data.ConversationHistory;
import com.example.whatsuit.service.GeminiService;

import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.GlobalScope;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

import java.util.HashSet;
import java.util.Set;

public class NotificationService extends NotificationListenerService {
    private static final String TAG = "AutoReplySystem";
    private volatile GeminiService geminiService;
    private volatile boolean geminiInitialized = false;
    private volatile boolean geminiInitializing = false;
    private AppDatabase database;
    private static final long NOTIFICATION_COOLDOWN = 5000; // 5 seconds cooldown
    private SharedPreferences processedNotifications;
    private final CoroutineScope serviceScope;

    public NotificationService() {
        // Use GlobalScope with IO dispatcher for background operations
        serviceScope = new CoroutineScope() {
            @Override
            public CoroutineContext getCoroutineContext() {
                return GlobalScope.INSTANCE.getCoroutineContext().plus(Dispatchers.getIO());
            }
        };
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NotificationService created");
        database = AppDatabase.getDatabase(this);
        initializeGeminiService();
        processedNotifications = getSharedPreferences("processed_notifications", Context.MODE_PRIVATE);
        
        createNotificationChannel();
        
        // Request rebind
        requestRebind();
        
    }
    
    private synchronized void initializeGeminiService() {
        if (geminiInitializing) {
            Log.d(TAG, "Gemini initialization already in progress");
            return;
        }

        geminiInitializing = true;
        geminiService = new GeminiService(this);
        
        try {
            BuildersKt.launch(
                serviceScope,
                EmptyCoroutineContext.INSTANCE,
                CoroutineStart.DEFAULT,
                (scope, continuation) -> {
                    try {
                        Log.d(TAG, "Starting Gemini initialization");
                        // Use proper Kotlin coroutine pattern for suspend function
                        Boolean result = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (coroutineScope, cont) -> {
                            // Call initialize without passing the continuation
                            return geminiService.initialize(cont);
                        });
                        
                        geminiInitialized = result != null && result;
                        Log.d(TAG, "Gemini initialization completed successfully: " + geminiInitialized);
                    } catch (Exception e) {
                        Log.w(TAG, "Gemini initialization failed (will continue without auto-reply): " + e.getMessage());
                        geminiInitialized = false;
                    } finally {
                        geminiInitializing = false;
                    }
                    return Unit.INSTANCE;
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch Gemini initialization", e);
            geminiInitializing = false;
            geminiInitialized = false;
        }
    }

    private boolean isRecentNotification(StatusBarNotification sbn, long currentTime) {
        String notificationKey = sbn.getKey();
        long lastProcessed = processedNotifications.getLong(notificationKey, 0);
        return (currentTime - lastProcessed < NOTIFICATION_COOLDOWN);
    }

    private void markNotificationProcessed(StatusBarNotification sbn, long currentTime) {
        String notificationKey = sbn.getKey();
        processedNotifications.edit()
            .putLong(notificationKey, currentTime)
            .apply();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        long currentTime = System.currentTimeMillis();

        // Only check for very recent duplicates (5 seconds)
        if (isRecentNotification(sbn, currentTime)) {
            Log.d(TAG, "Skipping very recent notification: " + sbn.getKey());
            return;
        }

        // Mark this notification time
        markNotificationProcessed(sbn, currentTime);

        BuildersKt.launch(
            serviceScope,
            EmptyCoroutineContext.INSTANCE,
            CoroutineStart.DEFAULT,
            (scope, continuation) -> {
                try {
                    handleNotification(sbn);
                } catch (Exception e) {
                    Log.e(TAG, "Error handling notification", e);
                }
                return Unit.INSTANCE;
            }
        );
    }

    private void handleNotification(StatusBarNotification sbn) throws PackageManager.NameNotFoundException {
        Notification notification = sbn.getNotification();
        String packageName = sbn.getPackageName();
        
        Log.d(TAG, "Handling notification from package: " + packageName);
        
        // Get app name
        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
        String appName = packageManager.getApplicationLabel(applicationInfo).toString();

        // Extract notification details
        String title = "";
        String content = "";
        
        if (notification.extras != null) {
            CharSequence titleSequence = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence textSequence = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            
            if (titleSequence != null) title = titleSequence.toString();
            if (textSequence != null) content = textSequence.toString();
            
            if (packageName.contains("whatsapp") && title != null && title.matches(".*[0-9+].*")) {
                String extracted = title.replaceAll("[^0-9+\\-]", "");
                String phoneNumber = extracted.replaceAll("[^0-9]", "");
                if (phoneNumber.length() == 11) {
                    title = phoneNumber;
                }
            }
        }

        NotificationEntity notificationEntity = new NotificationEntity(
            packageName,
            appName,
            title,
            content,
            sbn.getPostTime(),
            String.valueOf(sbn.getId())
        );

        // Check auto-reply settings
        SharedPreferences prefs = getSharedPreferences("whatsuit_settings", Context.MODE_PRIVATE);
        boolean globalAutoReplyEnabled = prefs.getBoolean("auto_reply_enabled", false);
        Log.d(TAG, "Global auto-reply enabled: " + globalAutoReplyEnabled);

        boolean appSpecificEnabled = false;
        if (isMessagingApp(packageName)) {
            appSpecificEnabled = database.appSettingDao().isAutoReplyEnabled(packageName);
            Log.d(TAG, "App-specific auto-reply enabled for " + packageName + ": " + appSpecificEnabled);
        }
        
        boolean shouldAutoReply = isMessagingApp(packageName) && 
            globalAutoReplyEnabled && 
            appSpecificEnabled &&
            geminiInitialized;
            
        Log.d(TAG, "Should auto-reply: " + shouldAutoReply + 
            " (isMessagingApp=" + isMessagingApp(packageName) + 
            ", globalEnabled=" + globalAutoReplyEnabled + 
            ", appEnabled=" + appSpecificEnabled + ")");

        // Save notification first
        long id = database.notificationDao().insert(notificationEntity);
        notificationEntity.setId(id);
        Log.d(TAG, "Saved notification to database with ID: " + id + ", Title: " + title + ", Content: " + content);

        if (shouldAutoReply) {
            String phoneNumber = "";
            String titlePrefix = "";
            
            if (packageName.contains("whatsapp") && content != null && content.matches(".*[0-9+].*")) {
                String extracted = content.replaceAll("[^0-9+\\-]", "");
                phoneNumber = extracted.replaceAll("[^0-9]", "");
            } else if (title != null && !title.isEmpty()) {
                titlePrefix = title.substring(0, Math.min(5, title.length()));
            }
            
            boolean autoReplyEnabled = !database.notificationDao().isAutoReplyDisabled(packageName, phoneNumber, titlePrefix);
            
            Log.d(TAG, String.format(
                "Auto-reply conditions met for notification:\n" +
                "App: %s\n" +
                "Content: %s\n" +
                "Phone: %s\n" +
                "Title Prefix: %s\n" +
                "Auto-reply enabled: %s",
                appName, content, phoneNumber, titlePrefix, autoReplyEnabled));
            
            if (autoReplyEnabled) {
                handleAutoReply(sbn, notificationEntity);
            }
        }

        updateNotificationWithDeepLink(sbn, id);
    }

    private void updateNotificationWithDeepLink(StatusBarNotification sbn, long id) {
        try {
            Intent deepLinkIntent = new Intent(Intent.ACTION_VIEW, 
                Uri.parse("whatsuit://notification/" + id));
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                deepLinkIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification notification = sbn.getNotification();
            if (notification.extras != null) {
                Notification.Builder builder;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    String channelId = notification.getChannelId();
                    builder = new Notification.Builder(this, channelId);
                } else {
                    builder = new Notification.Builder(this);
                }

                CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence content = notification.extras.getCharSequence(Notification.EXTRA_TEXT);

                builder.setContentTitle(title)
                       .setContentText(content)
                       .setSmallIcon(notification.getSmallIcon())
                       .setAutoCancel(true)
                       .setWhen(sbn.getPostTime())
                       .setContentIntent(pendingIntent);

                notification = builder.build();
                android.app.NotificationManager notificationManager = 
                    (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(sbn.getId(), notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification with deep link", e);
        }
    }

    private boolean isMessagingApp(String packageName) {
        return packageName.contains("whatsapp") || 
               packageName.contains("messenger") || 
               packageName.contains("telegram") ||
               packageName.contains("signal") ||
               packageName.contains("com.android.mms") ||
               packageName.contains("com.google.android.apps.messaging");
    }

    private void handleAutoReply(StatusBarNotification sbn, NotificationEntity notificationEntity) {
        Notification.Action[] actions = sbn.getNotification().actions;
        if (actions != null) {
            for (Notification.Action action : actions) {
                Log.d(TAG, "Checking notification action: " + action.title);
                boolean hasRemoteInput = action.getRemoteInputs() != null && action.getRemoteInputs().length > 0;
                Log.d(TAG, "Action has remote inputs: " + hasRemoteInput);
                if (hasRemoteInput) {
                    BuildersKt.launch(
                        serviceScope,
                        EmptyCoroutineContext.INSTANCE,
                        CoroutineStart.DEFAULT,
                        (scope, continuation) -> {
                            try {
                                // Initialize if needed
                                Boolean initResult = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (coroutineScope, cont) -> {
                                    return geminiService.initialize(cont);
                                });
                                Log.d(TAG, "Gemini initialized for auto-reply");
                                // Generate and send reply
                                generateAndSendReply(notificationEntity, action);
                            } catch (Exception e) {
                                Log.e(TAG, "Error during auto-reply", e);
                            }
                            return Unit.INSTANCE;
                        }
                    );
                    break;
                }
            }
        }
    }

    private void generateAndSendReply(NotificationEntity notification, Notification.Action replyAction) {
        geminiService.generateReply(notification.getId(), notification.getContent(), new GeminiService.ResponseCallback() {
            @Override
            public void onPartialResponse(String text) {
                Log.d(TAG, "Partial response from Gemini: " + text);
            }

            @Override
            public void onComplete(String fullResponse) {
                BuildersKt.launch(
                    serviceScope,
                    EmptyCoroutineContext.INSTANCE,
                    CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            saveConversationHistory(notification, fullResponse);
                            Log.d(TAG, "Initiated saving conversation history for notification " + notification.getId());
                            
                            notification.setAutoReplied(true);
                            notification.setAutoReplyContent(fullResponse);
                            database.notificationDao().update(notification);
                            
                            sendReply(replyAction, fullResponse);
                            Log.d(TAG, "Generated and sent full response: " + fullResponse);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing auto-reply", e);
                        }
                        return Unit.INSTANCE;
                    }
                );
            }

            @Override
            public void onError(Throwable error) {
                Log.e(TAG, "Error generating reply from Gemini", error);
            }
        });
    }

    private void sendReply(Notification.Action action, String replyText) {
        RemoteInput[] remoteInputs = action.getRemoteInputs();
        if (remoteInputs == null || remoteInputs.length == 0) return;

        try {
            RemoteInput remoteInput = remoteInputs[0];
            Intent intent = new Intent();
            Bundle results = new Bundle();
            results.putCharSequence(remoteInput.getResultKey(), replyText);
            RemoteInput.addResultsToIntent(remoteInputs, intent, results);

            action.actionIntent.send(this, 0, intent);
            Log.d(TAG, "Successfully sent auto-reply: " + replyText);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Failed to send auto-reply", e);
        }
    }

    private void saveConversationHistory(NotificationEntity notification, String fullResponse) {
        BuildersKt.launch(
            serviceScope,
            EmptyCoroutineContext.INSTANCE,
            CoroutineStart.DEFAULT,
            (scope, continuation) -> {
                try {
                    ConversationHistory history = ConversationHistory.Companion.create(
                        notification.getId(),
                        notification.getContent(),
                        fullResponse
                    );
                    database.conversationHistoryDao().insert(history, continuation);
                    Log.d(TAG, "Successfully saved conversation history for notification " + notification.getId());
                } catch (Exception e) {
                    Log.e(TAG, "Error saving conversation history", e);
                }
                return Unit.INSTANCE;
            }
        );
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String channelId = "whatsuit_notification_channel";
            CharSequence channelName = "WhatSuit Notifications";
            String channelDescription = "Channel for WhatSuit notifications";
            int importance = android.app.NotificationManager.IMPORTANCE_DEFAULT;
            
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                channelId, channelName, importance);
            channel.setDescription(channelDescription);
            
            android.app.NotificationManager notificationManager = getSystemService(
                android.app.NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created");
            }
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "Notification listener disconnected - requesting rebind");
        requestRebind();
    }

    private void requestRebind() {
        ComponentName componentName = new ComponentName(this, NotificationService.class);
        NotificationListenerService.requestRebind(componentName);
        Log.d(TAG, "Requested rebind for notification service");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "NotificationService being destroyed");
        if (geminiService != null) {
            geminiService.shutdown();
        }
        if (serviceScope != null) {
            Job job = serviceScope.getCoroutineContext().get(Job.Key);
            if (job != null) {
                job.cancel(null);
            }
            Log.d(TAG, "Service scope cancelled");
        }
    }
}
