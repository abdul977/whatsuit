package com.example.whatsuit;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.NotificationEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationService extends NotificationListenerService {
    private ExecutorService executorService;
    private AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        database = AppDatabase.getDatabase(this);
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

                // Create and save notification entity
                NotificationEntity notificationEntity = new NotificationEntity(
                    packageName,
                    appName,
                    title,
                    content,
                    sbn.getPostTime(),
                    String.valueOf(sbn.getId())
                );

                database.notificationDao().insert(notificationEntity);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
