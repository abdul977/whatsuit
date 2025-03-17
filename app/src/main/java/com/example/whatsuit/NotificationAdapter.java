package com.example.whatsuit;

import android.content.Intent;
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
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import com.example.whatsuit.data.NotificationEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NotificationAdapter extends ListAdapter<NotificationEntity, NotificationAdapter.NotificationViewHolder> {
    private List<NotificationEntity> allNotifications = new ArrayList<>();

    private static final DiffUtil.ItemCallback<NotificationEntity> DIFF_CALLBACK = 
        new DiffUtil.ItemCallback<NotificationEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull NotificationEntity oldItem, @NonNull NotificationEntity newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull NotificationEntity oldItem, @NonNull NotificationEntity newItem) {
                return oldItem.getTitle().equals(newItem.getTitle()) &&
                       oldItem.getContent().equals(newItem.getContent()) &&
                       oldItem.getTimestamp() == newItem.getTimestamp();
            }
        };

    public NotificationAdapter() {
        super(DIFF_CALLBACK);
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
        NotificationEntity notification = getItem(position);
            
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

        // Add ripple effect and handle clicks
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), NotificationDetailActivity.class);
            intent.putExtra("notification_id", notification.getId());
            
            // Start activity with shared element transition
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    public void setAllNotifications(List<NotificationEntity> newNotifications) {
        allNotifications.clear();
        allNotifications.addAll(newNotifications);
        submitList(newNotifications);
    }

    public void filterNotifications(String query) {
        List<NotificationEntity> filteredList = new ArrayList<>();
        for (NotificationEntity notification : allNotifications) {
            if (notification.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                notification.getContent().toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(notification);
            }
        }
        submitList(filteredList);
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
