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
import com.example.whatsuit.data.AutoReplySettings;
import com.example.whatsuit.util.ConversationIdGenerator;
import com.example.whatsuit.util.NotificationUtils;

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

    private static final String PREF_THROTTLE_DELAY = "auto_reply_throttle_delay_ms";
    private static final int DEFAULT_THROTTLE_DELAY_MS = 500;

    @Override
    public void onCreate() {
        super.onCreate();
        database = AppDatabase.getDatabase(this);
        executorService = Executors.newSingleThreadExecutor();
        geminiService = new GeminiService(this);
        prefs = getSharedPreferences("whatsuit_settings", MODE_PRIVATE);

        executorService.execute(() -> {
            try {
                geminiService.initializeFromJava();
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

            if (SentMessageTracker.getInstance().isLikelyOwnMessage(text)) {
                Log.d(TAG, "Skipping processing of our own message: " + text);
                return;
            }

            String packageName = sbn.getPackageName();

            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            String appName = packageManager.getApplicationLabel(applicationInfo).toString();

            NotificationEntity entity = new NotificationEntity();
            entity.setTitle(title);
            entity.setContent(text);
            entity.setPackageName(packageName);
            entity.setAppName(appName);
            entity.setTimestamp(sbn.getPostTime());

            String conversationId = ConversationIdGenerator.generate(entity);
            entity.setConversationId(conversationId);

            Log.i(TAG, "\n====== NEW NOTIFICATION RECEIVED ======" +
                "\nApp: " + appName +
                "\nPackage: " + packageName +
                "\nTitle: " + title +
                "\nContent: " + text +
                "\nConversation ID: " + conversationId +
                "\nTimestamp: " + new java.util.Date(sbn.getPostTime()) +
                "\n===================================");

            executorService.execute(() -> {
                try {
                    long notificationId = database.notificationDao().upsertNotification(entity);
                    entity.setId(notificationId);

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

    private boolean shouldAutoReply(String packageName) {
        if (!prefs.getBoolean("auto_reply_enabled", true)) {
            return false;
        }

        try {
            AppSettingEntity appSetting = database.appSettingDao().getAppSetting(packageName);
            return appSetting != null && appSetting.isAutoReplyEnabled();
        } catch (Exception e) {
            Log.e(TAG, "Error checking auto-reply settings", e);
            return false;
        }
    }

    private void handleAutoReply(StatusBarNotification sbn, NotificationEntity entity, Notification notification) {
        try {
            if (entity.isAutoReplied()) {
                Log.d(TAG, "Notification already auto-replied, skipping duplicate reply");
                return;
            }

            String phoneNumber = "";
            String titlePrefix = "";

            if (NotificationUtils.isWhatsAppPackage(entity.getPackageName()) &&
                NotificationUtils.hasPhoneNumber(entity.getTitle())) {
                phoneNumber = NotificationUtils.normalizePhoneNumber(entity.getTitle());
            } else {
                titlePrefix = NotificationUtils.getTitlePrefix(entity.getTitle());
            }

            AutoReplySettings settings = database.autoReplySettingsDao().getByConversationIdBlocking(entity.getConversationId());
            if (settings != null && settings.isDisabled()) {
                Log.d(TAG, "Auto-reply is disabled for this conversation, skipping reply");
                return;
            }

            String conversationId = entity.getConversationId();

            if (recentlyProcessedConversations.contains(conversationId)) {
                Log.d(TAG, "Conversation ID " + conversationId + " was just processed, skipping to prevent duplicate replies");
                return;
            }

            recentlyProcessedConversations.add(conversationId);

            int throttleDelay = prefs.getInt(PREF_THROTTLE_DELAY, DEFAULT_THROTTLE_DELAY_MS);
            handler.postDelayed(() -> recentlyProcessedConversations.remove(conversationId), throttleDelay);

            // Handle notification actions differently based on Android version
            Action[] actions = notification.actions;
            if (actions == null) {
                Log.d(TAG, "No actions found for auto-reply");
                return;
            }

            Action replyAction = null;
            RemoteInput remoteInput = null;

            // For Android 7+ (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                for (Action action : actions) {
                    RemoteInput[] remoteInputs = action.getRemoteInputs();
                    if (remoteInputs != null && remoteInputs.length > 0) {
                        replyAction = action;
                        remoteInput = remoteInputs[0];
                        break;
                    }
                }
            } else {
                // For Android 6 (API 23) - not needed for our minSdk 24, but kept for future reference
                Log.d(TAG, "Running on Android 6 or lower, direct reply not supported");
                // Could implement a fallback mechanism here if needed
                return;
            }

            if (replyAction == null || remoteInput == null) {
                Log.d(TAG, "No reply action or remote input found");
                return;
            }

            final Action finalReplyAction = replyAction;
            final RemoteInput finalRemoteInput = remoteInput;

            geminiService.generateReply(entity.getId(), entity.getContent(), new GeminiService.ResponseCallback() {
                @Override
                public void onPartialResponse(String text) {}

                @Override
                public void onComplete(String response) {
                    try {
                        Bundle resultsBundle = new Bundle();
                        resultsBundle.putString(finalRemoteInput.getResultKey(), response);

                        Intent intent = new Intent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            RemoteInput.addResultsToIntent(new RemoteInput[]{finalRemoteInput}, intent, resultsBundle);
                        } else {
                            Bundle wrapper = new Bundle();
                            wrapper.putBundle(RemoteInput.RESULTS_CLIP_LABEL, resultsBundle);
                            intent.putExtra(RemoteInput.EXTRA_RESULTS_DATA, wrapper);
                        }

                        finalReplyAction.actionIntent.send(NotificationService.this, 0, intent);
                        SentMessageTracker.getInstance().recordSentMessage(response);

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

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}

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
}
