package com.example.whatsuit.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whatsuit.R;
import com.example.whatsuit.data.NotificationEntity;

import java.util.ArrayList;
import java.util.List;

public class RelatedNotificationsAdapter extends RecyclerView.Adapter<RelatedNotificationsAdapter.ViewHolder> {
    private List<NotificationEntity> notifications = new ArrayList<>();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_related_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationEntity notification = notifications.get(position);

        // Format timestamp
        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
            notification.getTimestamp(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        );
        holder.timestampTextView.setText(timeAgo);
        
        // Set content
        holder.contentTextView.setText(notification.getContent());
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public void setNotifications(List<NotificationEntity> newNotifications) {
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return notifications.size();
            }

            @Override
            public int getNewListSize() {
                return newNotifications.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return notifications.get(oldItemPosition).getId() == 
                       newNotifications.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                NotificationEntity oldItem = notifications.get(oldItemPosition);
                NotificationEntity newItem = newNotifications.get(newItemPosition);
                return oldItem.getContent().equals(newItem.getContent()) &&
                       oldItem.getTimestamp() == newItem.getTimestamp();
            }
        }).dispatchUpdatesTo(this);

        notifications.clear();
        notifications.addAll(newNotifications);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView timestampTextView;
        TextView contentTextView;

        ViewHolder(View itemView) {
            super(itemView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
        }
    }
}