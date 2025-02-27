package com.example.whatsuit.adapter;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whatsuit.NotificationDetailActivity;
import com.example.whatsuit.R;
import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.util.AutoReplyManager;
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
    private final AutoReplyManager autoReplyManager;
    private final Handler mainHandler;

    public GroupedNotificationAdapter(PackageManager packageManager, AutoReplyManager autoReplyManager) {
        this.packageManager = packageManager;
        this.autoReplyManager = autoReplyManager;
        this.mainHandler = new Handler(Looper.getMainLooper());
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
            bindNotificationViewHolder((NotificationViewHolder) holder, position);
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

    private void bindNotificationViewHolder(NotificationViewHolder holder, int position) {
        NotificationEntity notification = (NotificationEntity) items.get(position);
        holder.notificationTitle.setText(notification.getTitle());
        holder.notificationContent.setText(notification.getContent());
        
        // Set up menu button
        holder.menuButton.setOnClickListener(v -> showPopupMenu(v, notification));
        
        // Update auto-reply status chip
        updateAutoReplyStatusAsync(holder, notification);

        // Set click listener for the whole card
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), NotificationDetailActivity.class);
            intent.putExtra("notification_id", notification.getId());
            v.getContext().startActivity(intent);
        });
    }

    private void showPopupMenu(View view, NotificationEntity notification) {
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.notification_item_menu, popup.getMenu());

        // Update menu item text based on current auto-reply status
        MenuItem autoReplyItem = popup.getMenu().findItem(R.id.action_toggle_auto_reply);
        autoReplyItem.setEnabled(false); // Disable until we get the status
        ExtractedInfo info = extractIdentifierInfo(notification);
        
        autoReplyManager.isAutoReplyDisabled(notification.getPackageName(), info.phoneNumber, info.titlePrefix,
            isDisabled -> mainHandler.post(() -> {
        
        autoReplyItem.setTitle(isDisabled ? "Enable Auto-Reply" : "Disable Auto-Reply");
                autoReplyItem.setEnabled(true);
            }));

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_toggle_auto_reply) {
                toggleAutoReply(notification);
                return true;
            } else if (item.getItemId() == R.id.action_view_details) {
                Intent intent = new Intent(view.getContext(), NotificationDetailActivity.class);
                intent.putExtra("notification_id", notification.getId());
                view.getContext().startActivity(intent);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void toggleAutoReply(NotificationEntity notification) {
        ExtractedInfo info = extractIdentifierInfo(notification);
        autoReplyManager.toggleAutoReply(notification.getPackageName(), info.phoneNumber, info.titlePrefix, 
            isDisabled -> notifyDataSetChanged());
    }

    private void updateAutoReplyStatusAsync(NotificationViewHolder holder, NotificationEntity notification) {
        // Set a loading state or default state if needed
        holder.autoReplyStatusChip.setEnabled(false); // Disable until we get the result
        holder.autoReplyStatusChip.setText("Loading...");
        holder.autoReplyStatusChip.setVisibility(View.VISIBLE);

        ExtractedInfo info = extractIdentifierInfo(notification);
        
        autoReplyManager.isAutoReplyDisabled(
notification.getPackageName(), 
info.phoneNumber, info.titlePrefix,
            isDisabled -> {
                mainHandler.post(() -> {
                holder.autoReplyStatusChip.setText(isDisabled ? "Auto-reply disabled" : "Auto-reply enabled");
                holder.autoReplyStatusChip.setEnabled(true);
                
                holder.autoReplyStatusChip.setChipBackgroundColorResource(isDisabled ? 
                    R.color.md_theme_errorContainer : R.color.md_theme_primaryContainer);
                holder.autoReplyStatusChip.setTextColor(holder.autoReplyStatusChip.getContext().getColor(isDisabled ? 
                    R.color.md_theme_onErrorContainer : R.color.md_theme_onPrimaryContainer));
                });
            });
    }

    private static class ExtractedInfo {
        final String phoneNumber;
        final String titlePrefix;

        ExtractedInfo(String phoneNumber, String titlePrefix) {
            this.phoneNumber = phoneNumber;
            this.titlePrefix = titlePrefix;
        }
    }

    private ExtractedInfo extractIdentifierInfo(NotificationEntity notification) {
        String phoneNumber = "";
        String titlePrefix = "";
        
        if (notification.getPackageName().contains("whatsapp") && 
            notification.getContent() != null && 
            notification.getContent().matches(".*[0-9+].*")) {
            String content = notification.getContent();
            String extracted = content.replaceAll("[^0-9+\\-]", "");
            phoneNumber = extracted.replaceAll("[^0-9]", "");
        } else if (notification.getTitle() != null && !notification.getTitle().isEmpty()) {
            titlePrefix = notification.getTitle().substring(0, 
                Math.min(5, notification.getTitle().length()));
        }
        
        return new ExtractedInfo(phoneNumber, titlePrefix);
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
        Map<String, Boolean> previousExpandedStates = new HashMap<>(expandedGroups);
        
        groupedNotifications.clear();
        for (NotificationEntity notification : notifications) {
            String key = notification.getPackageName();
            groupedNotifications.computeIfAbsent(key, k -> new ArrayList<>()).add(notification);
        }

        List<Object> newItems = new ArrayList<>();
        for (Map.Entry<String, List<NotificationEntity>> entry : groupedNotifications.entrySet()) {
            String packageName = entry.getKey();
            List<NotificationEntity> groupNotifications = entry.getValue();
            
            GroupHeader header = new GroupHeader(
                packageName,
                groupNotifications.get(0).getAppName(),
                groupNotifications.size()
            );
            newItems.add(header);

            boolean wasExpanded = previousExpandedStates.getOrDefault(packageName, false);
            expandedGroups.put(packageName, wasExpanded);

            if (wasExpanded) {
                newItems.addAll(groupNotifications);
            }
        }

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
                items.addAll(headerPosition + 1, notifications);
                notifyItemRangeInserted(headerPosition + 1, notifications.size());
            } else {
                items.subList(headerPosition + 1, headerPosition + 1 + notifications.size()).clear();
                notifyItemRangeRemoved(headerPosition + 1, notifications.size());
            }
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
        ImageButton menuButton;
        Chip autoReplyStatusChip;

        NotificationViewHolder(View itemView) {
            super(itemView);
            notificationTitle = itemView.findViewById(R.id.notificationTitle);
            notificationContent = itemView.findViewById(R.id.notificationContent);
            menuButton = itemView.findViewById(R.id.menuButton);
            autoReplyStatusChip = itemView.findViewById(R.id.autoReplyStatusChip);
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
