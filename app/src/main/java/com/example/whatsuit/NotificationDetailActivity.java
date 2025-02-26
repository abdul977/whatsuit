package com.example.whatsuit;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;

import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.NotificationEntity;

public class NotificationDetailActivity extends AppCompatActivity {
    private TextView titleTextView;
    private TextView contentTextView;
    private TextView appNameTextView;
    private TextView timestampTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);

        titleTextView = findViewById(R.id.titleTextView);
        contentTextView = findViewById(R.id.contentTextView);
        appNameTextView = findViewById(R.id.appNameTextView);
        timestampTextView = findViewById(R.id.timestampTextView);

        // Handle intent
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent.getData();
        if (data != null && "whatsuit".equals(data.getScheme())) {
            String notificationId = data.getLastPathSegment();
            if (notificationId != null) {
                try {
                    long id = Long.parseLong(notificationId);
                    loadNotification(id);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    finish();
                }
            }
        } else if (intent.hasExtra("notification_id")) {
            long notificationId = intent.getLongExtra("notification_id", -1);
            if (notificationId != -1) {
                loadNotification(notificationId);
            }
        } else {
            finish();
        }
    }

    private void loadNotification(long notificationId) {
        LiveData<NotificationEntity> notificationLiveData = 
            AppDatabase.getDatabase(this).notificationDao().getNotificationById(notificationId);
        
        notificationLiveData.observe(this, notification -> {
            if (notification != null) {
                titleTextView.setText(notification.getTitle());
                contentTextView.setText(notification.getContent());
                appNameTextView.setText(notification.getAppName());
                timestampTextView.setText(android.text.format.DateUtils.getRelativeTimeSpanString(
                    notification.getTimestamp(),
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                ));

                // Set action bar title
                setTitle(notification.getAppName());
            } else {
                finish();
            }
        });
    }
}
