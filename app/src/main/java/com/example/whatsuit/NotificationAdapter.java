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

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {
    private List<NotificationEntity> notifications = new ArrayList<>();

    public NotificationAdapter() {
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationEntity notification = notifications.get(position);
            
        // Apply material transitions
        holder.itemView.setTransitionName("notification_" + notification.getId());
        
        // Bind notification data
        holder.notificationTitle.setText(notification.getTitle());
        holder.notificationContent.setText(notification.getContent());
        
        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
            notification.getTimestamp(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        );
        holder.timestamp.setText(timeAgo);

        // Add ripple effect
        holder.itemView.setOnClickListener(v -> {
            // TODO: Implement notification detail view
        });
    }

    @Override
    public long getItemId(int position) {
        return notifications.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public void setNotifications(List<NotificationEntity> newNotifications) {
        List<NotificationEntity> oldNotifications = new ArrayList<>(notifications);
        notifications.clear();
        notifications.addAll(newNotifications);
        calculateDiff(oldNotifications, newNotifications);
    }

    private void calculateDiff(List<NotificationEntity> oldItems, List<NotificationEntity> newItems) {
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
                return oldItems.get(oldItemPosition).getId() == 
                    newItems.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                NotificationEntity oldNotif = oldItems.get(oldItemPosition);
                NotificationEntity newNotif = newItems.get(newItemPosition);
                return oldNotif.getTitle().equals(newNotif.getTitle()) &&
                       oldNotif.getContent().equals(newNotif.getContent()) &&
                       oldNotif.getTimestamp() == newNotif.getTimestamp();
            }
        }).dispatchUpdatesTo(this);
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
