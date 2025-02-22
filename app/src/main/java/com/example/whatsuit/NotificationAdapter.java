package com.example.whatsuit;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;

import com.google.android.material.chip.Chip;

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
        setHasStableIds(true);
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
            
            // Configure app header
            headerHolder.appName.setText(header.appName);
            headerHolder.notificationCount.setText(String.valueOf(header.count));
            
            // Apply material transition animations
            headerHolder.itemView.setTransitionName("header_" + header.packageName);
            
            try {
                Drawable icon = packageManager.getApplicationIcon(header.packageName);
                headerHolder.appIcon.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException e) {
                headerHolder.appIcon.setImageResource(R.drawable.ic_app_placeholder);
            }

            // Style the count chip
            Chip countChip = headerHolder.notificationCount;
            countChip.setText(String.valueOf(header.count));
            countChip.setEnsureMinTouchTargetSize(false);
            countChip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            countChip.setCloseIconVisible(false);
            countChip.setClickable(false);

        } else {
            NotificationEntity notification = (NotificationEntity) items.get(position);
            NotificationViewHolder notificationHolder = (NotificationViewHolder) holder;
            
            // Apply material transitions
            notificationHolder.itemView.setTransitionName("notification_" + notification.getId());
            
            // Bind notification data
            notificationHolder.notificationTitle.setText(notification.getTitle());
            notificationHolder.notificationContent.setText(notification.getContent());
            
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                notification.getTimestamp(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            );
            notificationHolder.timestamp.setText(timeAgo);

            // Add ripple effect
            notificationHolder.itemView.setOnClickListener(v -> {
                // TODO: Implement notification detail view
            });
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof AppHeader ? TYPE_HEADER : TYPE_NOTIFICATION;
    }

    @Override
    public long getItemId(int position) {
        Object item = items.get(position);
        if (item instanceof AppHeader) {
            return ((AppHeader) item).packageName.hashCode();
        } else {
            return ((NotificationEntity) item).getId();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setNotifications(List<NotificationEntity> notifications) {
        List<Object> oldItems = new ArrayList<>(items);
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
        
        // Calculate the difference and animate changes
        calculateDiff(oldItems, items);
    }

    private void calculateDiff(List<Object> oldItems, List<Object> newItems) {
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Object oldItem = oldItems.get(oldItemPosition);
                Object newItem = newItems.get(newItemPosition);
                
                if (oldItem instanceof AppHeader && newItem instanceof AppHeader) {
                    return ((AppHeader) oldItem).packageName.equals(
                        ((AppHeader) newItem).packageName);
                } else if (oldItem instanceof NotificationEntity && 
                         newItem instanceof NotificationEntity) {
                    return ((NotificationEntity) oldItem).getId() == 
                        ((NotificationEntity) newItem).getId();
                }
                return false;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Object oldItem = oldItems.get(oldItemPosition);
                Object newItem = newItems.get(newItemPosition);
                
                if (oldItem instanceof AppHeader && newItem instanceof AppHeader) {
                    AppHeader oldHeader = (AppHeader) oldItem;
                    AppHeader newHeader = (AppHeader) newItem;
                    return oldHeader.count == newHeader.count &&
                           oldHeader.appName.equals(newHeader.appName);
                } else if (oldItem instanceof NotificationEntity && 
                         newItem instanceof NotificationEntity) {
                    NotificationEntity oldNotif = (NotificationEntity) oldItem;
                    NotificationEntity newNotif = (NotificationEntity) newItem;
                    return oldNotif.getTitle().equals(newNotif.getTitle()) &&
                           oldNotif.getContent().equals(newNotif.getContent()) &&
                           oldNotif.getTimestamp() == newNotif.getTimestamp();
                }
                return false;
            }
        }).dispatchUpdatesTo(this);
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
        Chip notificationCount;

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
