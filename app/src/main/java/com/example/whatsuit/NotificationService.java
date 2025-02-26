package com.example.whatsuit;

import android.app.Notification;
import android.app.PendingIntent;
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
import com.example.whatsuit.service.GeminiService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationService extends NotificationListenerService {
    private static final String TAG = "AutoReplySystem";
    private ExecutorService executorService;
    private GeminiService geminiService;
    private AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        database = AppDatabase.getDatabase(this);
        geminiService = new GeminiService(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        executorService.execute(() -> {
            try {
                Notification notification = sbn.getNotification();
                String packageName = sbn.getPackageName();
                
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
                }

                // Create notification entity
                NotificationEntity notificationEntity = new NotificationEntity(
                    packageName,
                    appName,
                    title,
                    content,
                    sbn.getPostTime(),
                    String.valueOf(sbn.getId())
                );

                // Check if this is a messaging app notification and auto-reply is enabled
                SharedPreferences prefs = getSharedPreferences("whatsuit_settings", Context.MODE_PRIVATE);
                if (isMessagingApp(packageName) && prefs.getBoolean("auto_reply_enabled", false)) {
                    Log.d(TAG, "Processing message for auto-reply. App: " + appName + ", Content: " + content);
                    handleAutoReply(sbn, notificationEntity);
                }

                // Insert and get the ID
                long id = database.notificationDao().insert(notificationEntity);

                // Create deep link intent
                Intent deepLinkIntent = new Intent(Intent.ACTION_VIEW, 
                    Uri.parse("whatsuit://notification/" + id));
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    deepLinkIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                // Add the deep link to the original notification
                if (notification.extras != null) {
                    Notification.Builder builder;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        String channelId = notification.getChannelId();
                        builder = new Notification.Builder(this, channelId);
                    } else {
                        builder = new Notification.Builder(this);
                    }

                    // Copy original notification details
                    builder.setContentTitle(title)
                           .setContentText(content)
                           .setSmallIcon(sbn.getNotification().getSmallIcon())
                           .setAutoCancel(true)
                           .setWhen(sbn.getPostTime())
                           .setContentIntent(pendingIntent);

                    // Update the notification
                    try {
                        if (sbn.getNotification().getSmallIcon() != null) {
                            builder.setSmallIcon(sbn.getNotification().getSmallIcon());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    notification = builder.build();
                    android.app.NotificationManager notificationManager = 
                        (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.notify(sbn.getId(), notification);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        });
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
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                    generateAndSendReply(notificationEntity, action);
                    break;
                }
            }
        }
    }

    private void generateAndSendReply(NotificationEntity notification, Notification.Action replyAction) {
        geminiService.generateReply(notification.getContent(), new GeminiService.ResponseCallback() {
            @Override
            public void onPartialResponse(String text) {
                Log.d(TAG, "Partial response from Gemini: " + text);
            }

            @Override
            public void onComplete(String fullResponse) {
                executorService.execute(() -> {
                    // Send the reply
                    sendReply(replyAction, fullResponse);
                    Log.d(TAG, "Generated full response: " + fullResponse);
                    
                    // Update notification in database
                    notification.setAutoReplied(true);
                    notification.setAutoReplyContent(fullResponse);
                    database.notificationDao().update(notification);
                    Log.d(TAG, "Updated notification in database with auto-reply");
                });
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

        RemoteInput remoteInput = remoteInputs[0];
        Intent intent = new Intent();
        Bundle results = new Bundle();
        results.putCharSequence(remoteInput.getResultKey(), replyText);
        RemoteInput.addResultsToIntent(remoteInputs, intent, results);

        try {
            action.actionIntent.send(this, 0, intent);
            Log.d(TAG, "Successfully sent auto-reply: " + replyText);
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to send auto-reply", e);
        }
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
