package com.example.whatsuit;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whatsuit.data.NotificationEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_NOTIFICATION = 1;

    private List<Object> items = new ArrayList<>();
    private final PackageManager packageManager;

    public NotificationAdapter(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_header, parent, false);
            return new AppHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false);
            return new NotificationViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AppHeaderViewHolder) {
            AppHeader header = (AppHeader) items.get(position);
            AppHeaderViewHolder headerHolder = (AppHeaderViewHolder) holder;
            
            headerHolder.appName.setText(header.appName);
            headerHolder.notificationCount.setText(String.valueOf(header.count));
            
            try {
                Drawable icon = packageManager.getApplicationIcon(header.packageName);
                headerHolder.appIcon.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException e) {
                headerHolder.appIcon.setImageResource(R.drawable.ic_app_placeholder);
            }
        } else {
            NotificationEntity notification = (NotificationEntity) items.get(position);
            NotificationViewHolder notificationHolder = (NotificationViewHolder) holder;
            
            notificationHolder.notificationTitle.setText(notification.getTitle());
            notificationHolder.notificationContent.setText(notification.getContent());
            
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                notification.getTimestamp(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            );
            notificationHolder.timestamp.setText(timeAgo);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof AppHeader ? TYPE_HEADER : TYPE_NOTIFICATION;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setNotifications(List<NotificationEntity> notifications) {
        items.clear();
        
        // Group notifications by app
        Map<String, List<NotificationEntity>> groupedNotifications = new LinkedHashMap<>();
        for (NotificationEntity notification : notifications) {
            groupedNotifications.computeIfAbsent(notification.getPackageName(), k -> new ArrayList<>())
                .add(notification);
        }
        
        // Create items list with headers and notifications
        for (Map.Entry<String, List<NotificationEntity>> entry : groupedNotifications.entrySet()) {
            List<NotificationEntity> appNotifications = entry.getValue();
            if (!appNotifications.isEmpty()) {
                // Add header
                items.add(new AppHeader(
                    appNotifications.get(0).getPackageName(),
                    appNotifications.get(0).getAppName(),
                    appNotifications.size()
                ));
                // Add notifications
                items.addAll(appNotifications);
            }
        }
        
        notifyDataSetChanged();
    }

    static class AppHeader {
        String packageName;
        String appName;
        int count;

        AppHeader(String packageName, String appName, int count) {
            this.packageName = packageName;
            this.appName = appName;
            this.count = count;
        }
    }

    static class AppHeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView notificationCount;

        AppHeaderViewHolder(View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            notificationCount = itemView.findViewById(R.id.notificationCount);
        }
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView timestamp;
        TextView notificationTitle;
        TextView notificationContent;

        NotificationViewHolder(View itemView) {
            super(itemView);
            timestamp = itemView.findViewById(R.id.timestamp);
            notificationTitle = itemView.findViewById(R.id.notificationTitle);
            notificationContent = itemView.findViewById(R.id.notificationContent);
        }
    }
}
