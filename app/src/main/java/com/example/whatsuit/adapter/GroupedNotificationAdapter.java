package com.example.whatsuit.adapter;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whatsuit.NotificationDetailActivity;
import com.example.whatsuit.R;
import com.example.whatsuit.data.NotificationEntity;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupedNotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_NOTIFICATION = 1;

    private final PackageManager packageManager;
    private final List<Object> items = new ArrayList<>();
    private final Map<String, Boolean> expandedGroups = new HashMap<>();
    private final Map<String, List<NotificationEntity>> groupedNotifications = new HashMap<>();

    public GroupedNotificationAdapter(PackageManager packageManager) {
        this.packageManager = packageManager;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_app_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_notification, parent, false);
            return new NotificationViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            bindHeaderViewHolder((HeaderViewHolder) holder, (GroupHeader) items.get(position));
        } else {
            bindNotificationViewHolder((NotificationViewHolder) holder, (NotificationEntity) items.get(position));
        }
    }

    private void bindHeaderViewHolder(HeaderViewHolder holder, GroupHeader header) {
        holder.appName.setText(header.appName);
        holder.notificationCount.setText(String.valueOf(header.count));

        try {
            Drawable icon = packageManager.getApplicationIcon(header.packageName);
            holder.appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.appIcon.setImageResource(R.drawable.ic_app_placeholder);
        }

        boolean isExpanded = expandedGroups.getOrDefault(header.packageName, false);
        holder.itemView.setOnClickListener(v -> toggleGroup(header.packageName, holder.getAdapterPosition()));

        // Rotate expand icon based on expanded state
        holder.expandIcon.setRotation(isExpanded ? 180 : 0);
    }

    private void bindNotificationViewHolder(NotificationViewHolder holder, NotificationEntity notification) {
        holder.notificationTitle.setText(notification.getTitle());
        holder.notificationContent.setText(notification.getContent());
        
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), NotificationDetailActivity.class);
            intent.putExtra("notification_id", notification.getId());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof GroupHeader ? TYPE_HEADER : TYPE_NOTIFICATION;
    }

    @Override
    public long getItemId(int position) {
        Object item = items.get(position);
        if (item instanceof GroupHeader) {
            return ((GroupHeader) item).packageName.hashCode();
        } else {
            return ((NotificationEntity) item).getId();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateNotifications(List<NotificationEntity> notifications) {
        // Save current expanded states
        Map<String, Boolean> previousExpandedStates = new HashMap<>(expandedGroups);
        
        // Group notifications by package name
        groupedNotifications.clear();
        for (NotificationEntity notification : notifications) {
            String key = notification.getPackageName();
            groupedNotifications.computeIfAbsent(key, k -> new ArrayList<>()).add(notification);
        }

        // Build new items list with headers and notifications
        List<Object> newItems = new ArrayList<>();
        for (Map.Entry<String, List<NotificationEntity>> entry : groupedNotifications.entrySet()) {
            String packageName = entry.getKey();
            List<NotificationEntity> groupNotifications = entry.getValue();
            
            // Add header
            GroupHeader header = new GroupHeader(
                packageName,
                groupNotifications.get(0).getAppName(),
                groupNotifications.size()
            );
            newItems.add(header);

            // Preserve expanded state or collapse by default
            boolean wasExpanded = previousExpandedStates.getOrDefault(packageName, false);
            expandedGroups.put(packageName, wasExpanded);

            // Add notifications if group is expanded
            if (wasExpanded) {
                newItems.addAll(groupNotifications);
            }
        }

        // Calculate diff and update
        DiffUtil.calculateDiff(new NotificationDiffCallback(items, newItems))
               .dispatchUpdatesTo(this);
        
        items.clear();
        items.addAll(newItems);
    }

    private void toggleGroup(String packageName, int headerPosition) {
        boolean expanded = expandedGroups.getOrDefault(packageName, false);
        expandedGroups.put(packageName, !expanded);
        
        List<NotificationEntity> notifications = groupedNotifications.get(packageName);
        if (notifications != null) {
            if (!expanded) {
                // Insert notifications after header
                items.addAll(headerPosition + 1, notifications);
                notifyItemRangeInserted(headerPosition + 1, notifications.size());
            } else {
                // Remove notifications after header
                items.subList(headerPosition + 1, headerPosition + 1 + notifications.size()).clear();
                notifyItemRangeRemoved(headerPosition + 1, notifications.size());
            }
            // Update header to reflect new expanded state
            notifyItemChanged(headerPosition);
        }
    }

    static class GroupHeader {
        String packageName;
        String appName;
        int count;

        GroupHeader(String packageName, String appName, int count) {
            this.packageName = packageName;
            this.appName = appName;
            this.count = count;
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        Chip notificationCount;
        ImageView expandIcon;

        HeaderViewHolder(View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            notificationCount = itemView.findViewById(R.id.notificationCount);
            expandIcon = itemView.findViewById(R.id.expandIcon);
        }
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView notificationTitle;
        TextView notificationContent;

        NotificationViewHolder(View itemView) {
            super(itemView);
            notificationTitle = itemView.findViewById(R.id.notificationTitle);
            notificationContent = itemView.findViewById(R.id.notificationContent);
        }
    }

    static class NotificationDiffCallback extends DiffUtil.Callback {
        private final List<Object> oldItems;
        private final List<Object> newItems;

        NotificationDiffCallback(List<Object> oldItems, List<Object> newItems) {
            this.oldItems = oldItems;
            this.newItems = newItems;
        }

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

            if (oldItem.getClass() != newItem.getClass()) {
                return false;
            }

            if (oldItem instanceof GroupHeader) {
                return ((GroupHeader) oldItem).packageName.equals(
                    ((GroupHeader) newItem).packageName
                );
            } else {
                return ((NotificationEntity) oldItem).getId() ==
                    ((NotificationEntity) newItem).getId();
            }
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Object oldItem = oldItems.get(oldItemPosition);
            Object newItem = newItems.get(newItemPosition);

            if (oldItem instanceof GroupHeader) {
                GroupHeader oldHeader = (GroupHeader) oldItem;
                GroupHeader newHeader = (GroupHeader) newItem;
                return oldHeader.count == newHeader.count &&
                       oldHeader.appName.equals(newHeader.appName);
            } else {
                NotificationEntity oldNotif = (NotificationEntity) oldItem;
                NotificationEntity newNotif = (NotificationEntity) newItem;
                return oldNotif.getTitle().equals(newNotif.getTitle()) &&
                       oldNotif.getContent().equals(newNotif.getContent());
            }
        }
    }
}
