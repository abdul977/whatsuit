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
    private final MutableLiveData<Boolean> isAddingConversation;
    
    private LiveData<NotificationEntity> notificationObserver;
    private LiveData<List<NotificationEntity>> relatedNotificationsObserver;
    private LiveData<List<ConversationHistory>> conversationsObserver;

    public NotificationDetailViewModel(Application application) {
        super(application);
        database = AppDatabase.getDatabase(application);
        currentNotification = new MutableLiveData<>();
        relatedNotifications = new MutableLiveData<>();
        conversations = new MutableLiveData<>();
        executor = Executors.newSingleThreadExecutor();
        isAddingConversation = new MutableLiveData<>(false);
    }

    private androidx.lifecycle.Observer<NotificationEntity> notificationObserverCallback;
    private androidx.lifecycle.Observer<List<NotificationEntity>> relatedNotificationsObserverCallback;
    private androidx.lifecycle.Observer<List<ConversationHistory>> conversationsObserverCallback;

    public void loadNotification(long notificationId) {
        // Remove previous observers
        cleanupObservers();

        // Set up new observers with stored callbacks for proper cleanup
        notificationObserverCallback = notification -> {
            if (notification != null) {
                currentNotification.setValue(notification);
                loadRelatedNotifications(notificationId);
                loadConversations(notificationId);
            } else {
                // Handle null notification case
                currentNotification.setValue(null);
            }
        };
        notificationObserver = database.notificationDao().getNotificationByIdNumeric(notificationId);
        notificationObserver.observeForever(notificationObserverCallback);
    }

    private void loadRelatedNotifications(long notificationId) {
        relatedNotificationsObserverCallback = notifications -> 
            relatedNotifications.setValue(notifications != null ? notifications : java.util.Collections.emptyList());
        relatedNotificationsObserver = database.notificationDao().getRelatedNotifications(String.valueOf(notificationId));
        relatedNotificationsObserver.observeForever(relatedNotificationsObserverCallback);
    }

    private void loadConversations(long notificationId) {
        conversationsObserverCallback = history -> 
            conversations.setValue(history != null ? history : java.util.Collections.emptyList());
        conversationsObserver = database.getConversationHistoryDao().getHistoryForNotification(notificationId);
        conversationsObserver.observeForever(conversationsObserverCallback);
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

    public LiveData<List<ConversationHistory>> getConversationHistory() {
        return conversations;
    }

    public LiveData<Boolean> isAddingConversation() {
        return isAddingConversation;
    }

    public void setAddingConversation(boolean isAdding) {
        isAddingConversation.setValue(isAdding);
    }

    public void createConversation(String message, String response) {
        NotificationEntity notification = currentNotification.getValue();
        if (notification == null) return;

        String conversationId = String.valueOf(notification.getId());
        ConversationHistory conversation = new ConversationHistory(
            false,           // isModified
            null,           // analysis
            null,           // analysisTimestamp
            conversationId, // conversationId
            response,       // response
            notification.getId(), // notificationId
            0,             // id (auto-generated)
            message,       // message
            System.currentTimeMillis()  // timestamp
        );

        executor.execute(() -> {
            try {
                database.getConversationHistoryDao().insertSync(conversation);
                // Update LiveData on the main thread
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    loadConversations(notification.getId());
                });
            } catch (Exception e) {
                e.printStackTrace();
                // Handle error if needed
            }
        });
    }

    private void cleanupObservers() {
        if (notificationObserver != null && notificationObserverCallback != null) {
            notificationObserver.removeObserver(notificationObserverCallback);
        }
        if (relatedNotificationsObserver != null && relatedNotificationsObserverCallback != null) {
            relatedNotificationsObserver.removeObserver(relatedNotificationsObserverCallback);
        }
        if (conversationsObserver != null && conversationsObserverCallback != null) {
            conversationsObserver.removeObserver(conversationsObserverCallback);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        
        // Cancel background tasks
        if (executor instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) executor).shutdown();
        }

        // Clean up all observers
        cleanupObservers();

        // Clear any stored data
        currentNotification.setValue(null);
        relatedNotifications.setValue(null);
        conversations.setValue(null);
    }
}
