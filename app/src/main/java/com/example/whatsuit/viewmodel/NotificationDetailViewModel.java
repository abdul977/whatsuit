package com.example.whatsuit.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.NotificationEntity;
import java.util.List;

public class NotificationDetailViewModel extends AndroidViewModel {
    private final AppDatabase database;
    private final MutableLiveData<NotificationEntity> currentNotification;
    private final MutableLiveData<List<NotificationEntity>> relatedNotifications;

    public NotificationDetailViewModel(Application application) {
        super(application);
        database = AppDatabase.getDatabase(application);
        currentNotification = new MutableLiveData<>();
        relatedNotifications = new MutableLiveData<>();
    }

    public void loadNotification(long notificationId) {
        database.notificationDao().getNotificationById(notificationId)
                .observeForever(notification -> {
                    currentNotification.setValue(notification);
                    loadRelatedNotifications(notificationId);
                });
    }

    private void loadRelatedNotifications(long notificationId) {
        database.notificationDao().getRelatedNotifications(notificationId)
                .observeForever(notifications -> {
                    relatedNotifications.setValue(notifications);
                });
    }

    public void filterNotificationsByTimeRange(long startTime, long endTime) {
        NotificationEntity notification = currentNotification.getValue();
        if (notification == null) return;

        String phoneNumber = "";
        String titlePrefix = "";
        
        if (notification.getPackageName().contains("whatsapp") && 
            notification.getContent().matches(".*\\d.*")) {
            phoneNumber = notification.getContent().replaceAll("[^0-9]", "")
                         .substring(0, Math.min(11, notification.getContent().length()));
        } else {
            titlePrefix = notification.getTitle()
                         .substring(0, Math.min(5, notification.getTitle().length()));
        }

        database.notificationDao()
                .getRelatedNotificationsByTimeRange(
                        notification.getPackageName(),
                        phoneNumber,
                        titlePrefix,
                        startTime,
                        endTime)
                .observeForever(notifications -> {
                    relatedNotifications.setValue(notifications);
                });
    }

    public LiveData<NotificationEntity> getCurrentNotification() {
        return currentNotification;
    }

    public LiveData<List<NotificationEntity>> getRelatedNotifications() {
        return relatedNotifications;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Remove observers if needed
    }
}