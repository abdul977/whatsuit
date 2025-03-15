package com.example.whatsuit.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.data.ConversationHistory;

public class NotificationDetailViewModel extends AndroidViewModel {
    private final AppDatabase database;
    private final MutableLiveData<NotificationEntity> currentNotification;
    private final MutableLiveData<List<NotificationEntity>> relatedNotifications;
    private final MutableLiveData<List<ConversationHistory>> conversations;
    private final Executor executor;

    public NotificationDetailViewModel(Application application) {
        super(application);
        database = AppDatabase.getDatabase(application);
        currentNotification = new MutableLiveData<>();
        relatedNotifications = new MutableLiveData<>();
        conversations = new MutableLiveData<>();
        executor = Executors.newSingleThreadExecutor();
    }

    public void loadNotification(long notificationId) {
        database.notificationDao().getNotificationById(String.valueOf(notificationId))
                .observeForever(notification -> {
                    currentNotification.setValue(notification);
                    loadRelatedNotifications(notificationId);
                    loadConversations(notificationId);
                });
    }

    private void loadRelatedNotifications(long notificationId) {
        database.notificationDao().getRelatedNotifications(String.valueOf(notificationId))
                .observeForever(notifications -> {
                    relatedNotifications.setValue(notifications);
                });
    }

    private void loadConversations(long notificationId) {
        database.getConversationHistoryDao()
               .getHistoryForNotification(notificationId)
               .observeForever(history -> {
                   conversations.setValue(history);
               });
    }

    public void editConversation(ConversationHistory conversation, String newMessage, String newResponse) {
        executor.execute(() -> {
            try {
                // Use the synchronous version of the DAO method instead of the suspending function
                database.getConversationHistoryDao().updateConversationContentSync(
                    conversation.getId(),
                    newMessage,
                    newResponse,
                    System.currentTimeMillis()
                );
            } catch (Exception e) {
                e.printStackTrace();
                // Handle error if needed
            }
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

    public LiveData<List<ConversationHistory>> getConversations() {
        return conversations;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Clean up observers
    }
}
