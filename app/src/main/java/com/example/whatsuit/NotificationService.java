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
import com.example.whatsuit.util.PhoneNumberUtil;
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
        requestRebind(new ComponentName(this, NotificationService.class));
    }
    
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationManager notificationManager = 
                    getSystemService(android.app.NotificationManager.class);
            
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "whatsuit_channel",
                    "WhatSuit Notifications",
                    android.app.NotificationManager.NotificationPriority.DEFAULT);
            
            channel.setDescription("Notifications from WhatSuit");
            notificationManager.createNotificationChannel(channel);
            
            Log.d(TAG, "Notification channel created");
        }
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
                        Boolean result = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (coroutineScope, cont) -> {
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

        if (isRecentNotification(sbn, currentTime)) {
            Log.d(TAG, "Skipping very recent notification: " + sbn.getKey());
            return;
        }

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
        
        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
        String appName = packageManager.getApplicationLabel(applicationInfo).toString();

        String title = "";
        String content = "";
        String threadId;
        
        if (notification.extras != null) {
            CharSequence titleSequence = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence textSequence = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            
            if (titleSequence != null) title = titleSequence.toString();
            if (textSequence != null) content = textSequence.toString();

            // Try to extract phone number from title for WhatsApp
            if (packageName.contains("whatsapp")) {
                String phoneNumber = PhoneNumberUtil.extractPhoneNumber(title);
                // If no phone number in title, try content
                if (phoneNumber == null && content != null) {
                    phoneNumber = PhoneNumberUtil.extractPhoneNumber(content);
                }
                if (phoneNumber != null) {
                    title = phoneNumber;
                }
            }
        }

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
            
            id = database.notificationDao().upsertNotification(notificationEntity);
            Log.d(TAG, "Successfully processed notification with ID: " + id);
        } catch (Exception e) {
            Log.e(TAG, "Error handling notification (Ask Gemini)", e);
            return;
        }

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
            appSpecificEnabled;
            
        Log.d(TAG, "Should auto-reply: " + shouldAutoReply + 
            " (isMessagingApp=" + isMessagingApp(packageName) + 
            ", globalEnabled=" + globalAutoReplyEnabled + 
            ", appEnabled=" + appSpecificEnabled + 
            ", geminiStatus=" + (geminiInitialized ? "initialized" : "not initialized") + ")");

        if (shouldAutoReply) {
            String phoneNumber = PhoneNumberUtil.extractPhoneNumber(title);
            String titlePrefix = "";
            
            if (phoneNumber == null) {
                titlePrefix = title.substring(0, Math.min(5, title.length()));
            }
            
            boolean autoReplyEnabled = !database.notificationDao().isAutoReplyDisabled(packageName, phoneNumber != null ? phoneNumber : "", titlePrefix);
            
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

    private String generateThreadId(String packageName, String title) {
        if (packageName.contains("whatsapp") && title != null) {
            String phoneNumber = PhoneNumberUtil.extractPhoneNumber(title);
            if (phoneNumber != null) {
                return packageName + "_" + phoneNumber;
            }
        }
        
        return packageName + "_" + (title != null ? title.replaceAll("[^a-zA-Z0-9]", "") : "unknown");
    }
    
    private boolean isMessagingApp(String packageName) {
        String[] messagingApps = {
            "com.whatsapp", 
            "org.telegram.messenger", 
            "com.facebook.orca", 
            "com.instagram.android", 
            "com.facebook.mlite", 
            "com.google.android.apps.messaging",
            "com.viber.voip",
            "kik.android",
            "jp.naver.line.android",
            "com.snapchat.android",
            "com.discord"
        };
        
        for (String app : messagingApps) {
            if (packageName.contains(app)) {
                return true;
            }
        }
        
        SharedPreferences prefs = getSharedPreferences("whatsuit_settings", Context.MODE_PRIVATE);
        Set<String> customMessagingApps = prefs.getStringSet("custom_messaging_apps", new HashSet<>());
        
        return customMessagingApps.contains(packageName);
    }
    
    private void handleAutoReply(StatusBarNotification sbn, NotificationEntity notificationEntity) {
        if (!geminiInitialized) {
            Log.d(TAG, "Cannot handle auto-reply as Gemini is not initialized");
            return;
        }
        
        Notification notification = sbn.getNotification();
        if (notification.actions == null) {
            Log.d(TAG, "No actions available for auto-reply");
            return;
        }
        
        boolean replyActionFound = false;
        for (Notification.Action action : notification.actions) {
            if (action != null && action.getRemoteInputs() != null && 
                action.getRemoteInputs().length > 0 &&
                (action.title.toString().toLowerCase().contains("reply") || 
                 action.title.toString().toLowerCase().contains("respond"))) {
                
                try {
                    replyActionFound = true;
                    
                    BuildersKt.launch(
                        serviceScope,
                        EmptyCoroutineContext.INSTANCE,
                        CoroutineStart.DEFAULT,
                        (scope, continuation) -> {
                            try {
                                String replyText = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (coroutineScope, cont) -> {
                                    return geminiService.generateReply(notificationEntity.getContent(), cont);
                                });
                                
                                if (replyText != null && !replyText.isEmpty()) {
                                    RemoteInput[] remoteInputs = action.getRemoteInputs();
                                    RemoteInput remoteInput = remoteInputs[0];
                                    
                                    Intent intent = new Intent();
                                    Bundle bundle = new Bundle();
                                    bundle.putCharSequence(remoteInput.getResultKey(), replyText);
                                    
                                    RemoteInput.addResultsToIntent(remoteInputs, intent, bundle);
                                    try {
                                        action.actionIntent.send(this, 0, intent);
                                        
                                        // Update notification with auto-reply info
                                        notificationEntity.setAutoReplied(true);
                                        notificationEntity.setAutoReplyContent(replyText);
                                        database.notificationDao().update(notificationEntity);
                                        
                                        Log.d(TAG, "Auto-reply sent: " + replyText);
                                    } catch (PendingIntent.CanceledException e) {
                                        Log.e(TAG, "Failed to send auto-reply", e);
                                    }
                                } else {
                                    Log.d(TAG, "Gemini did not generate a reply");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error generating or sending auto-reply", e);
                            }
                            return Unit.INSTANCE;
                        }
                    );
                    
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to execute auto-reply", e);
                }
            }
        }
        
        if (!replyActionFound) {
            Log.d(TAG, "No suitable reply action found in the notification");
        }
    }
    
    private void updateNotificationWithDeepLink(StatusBarNotification sbn, long id) {
        try {
            // Create an intent to open the NotificationDetailActivity
            Intent detailIntent = new Intent(this, NotificationDetailActivity.class);
            detailIntent.putExtra("notification_id", id);
            detailIntent.putExtra("package_name", sbn.getPackageName());
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                (int) id, 
                detailIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Get the original notification
            Notification original = sbn.getNotification();
            
            // Build a new notification with the same content but adding our deep link
            Notification.Builder builder;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, original.getChannelId());
            } else {
                builder = new Notification.Builder(this);
            }
            
            // Copy basic properties
            builder.setContentTitle(original.extras.getCharSequence(Notification.EXTRA_TITLE))
                   .setContentText(original.extras.getCharSequence(Notification.EXTRA_TEXT))
                   .setSmallIcon(original.icon)
                   .setContentIntent(pendingIntent);
            
            // Only attempt to update if we have permission
            if (isNotificationListenerConnected()) {
                android.service.notification.StatusBarNotification[] sbnArray = getActiveNotifications();
                for (android.service.notification.StatusBarNotification activeNotification : sbnArray) {
                    if (activeNotification.getId() == sbn.getId() && 
                        activeNotification.getPackageName().equals(sbn.getPackageName())) {
                        // Update the notification
                        // Note: This is commented out as it may not be desired to actually replace
                        // the original notifications, but just to store them for our app to display
                        // cancelNotification(sbn.getKey());
                        // NotificationManager notificationManager = getSystemService(NotificationManager.class);
                        // notificationManager.notify(sbn.getId(), builder.build());
                        
                        Log.d(TAG, "Updated notification with deep link: " + id);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update notification with deep link", e);
        }
    }
    
    private boolean isNotificationListenerConnected() {
        try {
            return android.provider.Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
            ).contains(getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "Error checking notification listener status", e);
            return false;
        }
    }
}
