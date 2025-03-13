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
import com.example.whatsuit.data.KeywordActionEntity;
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

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService extends NotificationListenerService {
    private static final String TAG = "AutoReplySystem";
    private volatile GeminiService geminiService;
    private volatile boolean geminiInitialized = false;
    private volatile boolean geminiInitializing = false;
    private AppDatabase database;
    private static final long NOTIFICATION_COOLDOWN = 5000; // 5 seconds cooldown
    private SharedPreferences processedNotifications;
    private final CoroutineScope serviceScope;
    private final ConcurrentHashMap<String, Long> processingNotifications = new ConcurrentHashMap<>();

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
            // Use runBlocking to make initialization synchronous
            Boolean result = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (coroutineScope, cont) -> {
                return geminiService.initialize(cont);
            });
            
            geminiInitialized = result != null && result;
            Log.d(TAG, "Gemini initialization completed with result: " + geminiInitialized);
        } catch (Exception e) {
            Log.e(TAG, "Gemini initialization failed", e);
            geminiInitialized = false;
        } finally {
            geminiInitializing = false;
        }
    }

    private boolean canProcessNotification(StatusBarNotification sbn) {
        String key = sbn.getKey();
        long now = System.currentTimeMillis();
        Long lastProcessTime = processingNotifications.putIfAbsent(key, now);
        
        if (lastProcessTime != null && (now - lastProcessTime) < NOTIFICATION_COOLDOWN) {
            Log.d(TAG, "Skipping duplicate notification processing: " + key);
            return false;
        }
        return true;
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

        if (!canProcessNotification(sbn)) {
            return;
        }

        BuildersKt.launch(
            serviceScope,
            EmptyCoroutineContext.INSTANCE,
            CoroutineStart.DEFAULT,
            (scope, continuation) -> {
                try {
                    handleNotification(sbn);
                } catch (Exception e) {
                    Log.e(TAG, "Error handling notification", e);
                } finally {
                    processingNotifications.remove(sbn.getKey());
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
        
        // Initialize threadId
        String threadId;
        
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
        
        // Generate consistent thread ID for the conversation
        threadId = generateThreadId(packageName, title);

        
long id;
        NotificationEntity notificationEntity;
        try {
            notificationEntity = new NotificationEntity(
                packageName,
                appName,
                title,
                content,
                threadId,
                sbn.getPostTime(),
                String.valueOf(sbn.getId())
             );
            
            // Use atomic upsert operation
            id = database.notificationDao().upsertNotification(notificationEntity);
            Log.d(TAG, "Successfully processed notification with ID: " + id);
        } catch (Exception e) {
            Log.e(TAG, "Error handling notification (Ask Gemini)", e);
            return;
        }
        // Check auto-reply settings
        SharedPreferences prefs = getSharedPreferences("whatsuit_settings", Context.MODE_PRIVATE);
        boolean globalAutoReplyEnabled = prefs.getBoolean("auto_reply_enabled", false);
        Log.d(TAG, "Global auto-reply enabled: " + globalAutoReplyEnabled);

        boolean appSpecificEnabled = false;
        boolean appSpecificGroupsEnabled = false;
        if (isMessagingApp(packageName)) {
            appSpecificEnabled = database.appSettingDao().isAutoReplyEnabled(packageName);
            appSpecificGroupsEnabled = database.appSettingDao().isAutoReplyGroupsEnabled(packageName);
            Log.d(TAG, "App-specific auto-reply enabled for " + packageName + ": " + appSpecificEnabled);
            Log.d(TAG, "App-specific auto-reply for groups enabled for " + packageName + ": " + appSpecificGroupsEnabled);
        }
        
        // Check basic auto-reply conditions without requiring Gemini to be initialized
        boolean shouldAutoReply = isMessagingApp(packageName) && 
            globalAutoReplyEnabled && 
            appSpecificEnabled;
            
        Log.d(TAG, "Should auto-reply: " + shouldAutoReply + 
            " (isMessagingApp=" + isMessagingApp(packageName) + 
            ", globalEnabled=" + globalAutoReplyEnabled + 
            ", appEnabled=" + appSpecificEnabled + 
            ", geminiStatus=" + (geminiInitialized ? "initialized" : "not initialized") + ")");

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
                boolean isGroupMessage = isGroupMessage(sbn);
                if (isGroupMessage && appSpecificGroupsEnabled) {
                    handleAutoReply(sbn, notificationEntity);
                } else if (!isGroupMessage) {
                    handleAutoReply(sbn, notificationEntity);
                }
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

    private String generateThreadId(String packageName, String title) {
        // For WhatsApp, use exact 11-digit phone number as thread ID
        if (packageName.contains("whatsapp") && title != null) {
            String phoneNumber = title.replaceAll("[^0-9]", "");
            if (phoneNumber.length() == 11) {
                return packageName + "_" + phoneNumber;
            }
        }
        
        // For other apps or when no valid phone number, use package name + sanitized title
        return packageName + "_" + (title != null ? title.replaceAll("[^a-zA-Z0-9]", "") : "unknown");
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
                                // Check for keyword match first
                                KeywordActionEntity keywordAction = database.keywordActionDao()
                                    .findMatchingKeyword(notificationEntity.getContent());
                                    
                                if (keywordAction != null && keywordAction.isEnabled()) {
                                    Log.d(TAG, "Found matching keyword action: " + keywordAction.getKeyword());
                                    handleKeywordAction(action, keywordAction);
                                } else {
                                    // Queue for Gemini response if not initialized
                                    if (!geminiInitialized) {
                                        Log.d(TAG, "Queueing notification for later processing");
                                        geminiService.queueNotification(notificationEntity.getId(), 
                                            createResponseCallback(action, notificationEntity));
                                    } else {
                                        generateAndSendReply(notificationEntity, action);
                                    }
                                }
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

    private void handleKeywordAction(Notification.Action action, KeywordActionEntity keywordAction) {
        try {
            if (keywordAction.getActionType().equals("TEXT")) {
                // Handle text replies directly
                sendReply(action, keywordAction.getActionContent());
                return;
            }

            File mediaFile = new File(keywordAction.getActionContent());
            if (!mediaFile.exists()) {
                Log.e(TAG, "Media file not found: " + keywordAction.getActionContent());
                return;
            }

            // Determine MIME type based on file extension
            String fileName = mediaFile.getName().toLowerCase();
            String mimeType;
            
            if (keywordAction.getActionType().equals("IMAGE")) {
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    mimeType = "image/jpeg";
                } else if (fileName.endsWith(".png")) {
                    mimeType = "image/png";
                } else if (fileName.endsWith(".gif")) {
                    mimeType = "image/gif";
                } else if (fileName.endsWith(".webp")) {
                    mimeType = "image/webp";
                } else {
                    Log.e(TAG, "Unsupported image format: " + fileName);
                    return;
                }
            } else if (keywordAction.getActionType().equals("VIDEO")) {
                if (fileName.endsWith(".mp4")) {
                    mimeType = "video/mp4";
                } else if (fileName.endsWith(".3gp")) {
                    mimeType = "video/3gpp";
                } else if (fileName.endsWith(".webm")) {
                    mimeType = "video/webm";
                } else {
                    Log.e(TAG, "Unsupported video format: " + fileName);
                    return;
                }
            } else {
                Log.e(TAG, "Unsupported action type: " + keywordAction.getActionType());
                return;
            }

            boolean isImage = keywordAction.getActionType().equals("IMAGE");
            boolean isVideo = keywordAction.getActionType().equals("VIDEO");
            
            if (!isImage && !isVideo) {
                Log.e(TAG, "Media type mismatch. File: " + mimeType + ", Action type: " + keywordAction.getActionType());
                return;
            }

            Uri contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "com.example.whatsuit.fileprovider",
                mediaFile
            );

            // Create intent with media attachment
            Intent intent = new Intent();
            intent.setClipData(android.content.ClipData.newRawUri("", contentUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            
            // Add media type to intent
            intent.setType(mimeType);
            
            // Grant permissions to WhatsApp
            grantUriPermission("com.whatsapp", contentUri, 
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            grantUriPermission("com.whatsapp.w4b", contentUri, 
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // Add to remote input
            RemoteInput[] remoteInputs = action.getRemoteInputs();
            if (remoteInputs != null && remoteInputs.length > 0) {
                // Create a special WhatsApp-style bundle
                Bundle whatsappBundle = new Bundle();
                whatsappBundle.putParcelable("android.intent.extra.STREAM", contentUri);
                whatsappBundle.putString("android.intent.extra.MIME_TYPE", mimeType);
                whatsappBundle.putBoolean("force_attach_media", true);
                
                // Create the media intent
                Intent mediaIntent = new Intent();
                mediaIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                mediaIntent.putExtras(whatsappBundle);
                mediaIntent.setClipData(android.content.ClipData.newRawUri("", contentUri));
                mediaIntent.setType(mimeType);
                
                // Create a wrapper bundle that includes both the media and remote input
                Bundle wrapperBundle = new Bundle();
                wrapperBundle.putParcelable(remoteInputs[0].getResultKey(), contentUri);
                wrapperBundle.putParcelable("media_contents", whatsappBundle);
                
                // Add results to intent
                RemoteInput.addResultsToIntent(remoteInputs, mediaIntent, wrapperBundle);
                
                try {
                    action.actionIntent.send(this, 0, mediaIntent);
                    Log.d(TAG, "Successfully sent WhatsApp media reply: " + keywordAction.getActionContent());
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to send WhatsApp media reply", e);
                }
            } else {
                Log.e(TAG, "No remote inputs available for reply action");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send media reply: " + e.getMessage(), e);
        }
    }

    private GeminiService.ResponseCallback createResponseCallback(Notification.Action action, NotificationEntity notification) {
        return new GeminiService.ResponseCallback() {
            @Override
            public void onPartialResponse(String text) {
                Log.d(TAG, "Partial response from Gemini (queued): " + text);
            }

            @Override
            public void onComplete(String fullResponse) {
                BuildersKt.launch(
                    serviceScope,
                    EmptyCoroutineContext.INSTANCE,
                    CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            // Update notification status atomically
                            BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (scope2, cont2) -> {
                                database.runInTransaction(() -> {
                                    notification.setAutoReplied(true);
                                    notification.setAutoReplyContent(fullResponse);
                                    database.notificationDao().update(notification);
                                    return Unit.INSTANCE;
                                });
                                return Unit.INSTANCE;
                            });
                            
                            sendReply(action, fullResponse);
                            Log.d(TAG, "Generated and sent queued response: " + fullResponse);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing queued auto-reply", e);
                        }
                        return Unit.INSTANCE;
                    }
                );
            }

            @Override
            public void onError(Throwable error) {
                Log.e(TAG, "Error generating queued reply from Gemini", error);
            }
        };
    }

    private void generateAndSendReply(NotificationEntity notification, Notification.Action replyAction) {
        if (notification.isAutoReplied()) {
            Log.d(TAG, "Notification already auto-replied, skipping: " + notification.getId());
            return;
        }

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
                            // Update notification status atomically
                            BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (scope2, cont2) -> {
                                database.runInTransaction(() -> {
                                    notification.setAutoReplied(true);
                                    notification.setAutoReplyContent(fullResponse);
                                    database.notificationDao().update(notification);
                                    return Unit.INSTANCE;
                                });
                                return Unit.INSTANCE;
                            });
                            
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

    private boolean isGroupMessage(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;

        // Modern API detection
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Boolean isGroup = extras.getBoolean(androidx.core.app.NotificationCompat.EXTRA_IS_GROUP_CONVERSATION);
            if (isGroup != null) return isGroup;
        }

        // Legacy fallback checks
        String text = extras.getString("android.text");
        String title = extras.getString("android.title");

        return title.matches(".*\\d+ messages?.*") 
            || text.contains(":") 
            || (extras.get("android.people") != null 
                && ((String[]) extras.get("android.people")).length > 1);
    }
}
