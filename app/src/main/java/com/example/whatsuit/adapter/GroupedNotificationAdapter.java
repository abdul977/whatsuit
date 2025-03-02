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

import com.example.whatsuit.MainActivity;
import com.example.whatsuit.NotificationDetailActivity;
import com.example.whatsuit.R;
import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.util.AutoReplyManager;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GroupedNotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_NOTIFICATION = 1;

    private final PackageManager packageManager;
    private final List<Object> items = new ArrayList<>();
    private final Map<GroupKey, Boolean> expandedGroups = new HashMap<>();
    private final Map<GroupKey, List<NotificationEntity>> groupedNotifications = new HashMap<>();
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
        holder.appName.setText(header.displayTitle);
        holder.notificationCount.setText(String.valueOf(header.count));

        try {
            Drawable icon = packageManager.getApplicationIcon(header.getPackageName());
            holder.appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.appIcon.setImageResource(R.drawable.ic_app_placeholder);
        }

        boolean isExpanded = expandedGroups.getOrDefault(header.groupKey, false);
        holder.itemView.setOnClickListener(v -> toggleGroup(header.groupKey, holder.getAdapterPosition()));

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

        MenuItem autoReplyItem = popup.getMenu().findItem(R.id.action_toggle_auto_reply);
        autoReplyItem.setEnabled(false);
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
            } else if (item.getItemId() == R.id.action_view_history) {
                if (view.getContext() instanceof MainActivity) {
                    ((MainActivity) view.getContext()).showConversationHistory(notification);
                }
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
        holder.autoReplyStatusChip.setEnabled(false);
        holder.autoReplyStatusChip.setText("Loading...");
        holder.autoReplyStatusChip.setVisibility(View.VISIBLE);

        ExtractedInfo info = extractIdentifierInfo(notification);
        
        autoReplyManager.isAutoReplyDisabled(
            notification.getPackageName(), 
            info.phoneNumber,
            info.titlePrefix,
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
            return ((GroupHeader) item).groupKey.hashCode();
        } else {
            return ((NotificationEntity) item).getId();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateNotifications(List<NotificationEntity> notifications) {
        Map<GroupKey, Boolean> previousExpandedStates = new HashMap<>(expandedGroups);

        groupedNotifications.clear();
        
        // Group notifications by either phone number or title prefix
        for (NotificationEntity notification : notifications) {
            GroupKey groupKey = createGroupKey(notification);
            groupedNotifications.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(notification);
        }
        
        List<Object> newItems = new ArrayList<>();
        for (Map.Entry<GroupKey, List<NotificationEntity>> entry : groupedNotifications.entrySet()) {
            GroupKey groupKey = entry.getKey();
            List<NotificationEntity> groupNotifications = entry.getValue();
            
            // Create group header with appropriate title based on group type
            String headerTitle;
            if (groupKey.isPhoneNumberGroup) {
                // For phone number groups, use the full number as title
                headerTitle = groupKey.identifier;
            } else {
                // For text groups, use the original title
                headerTitle = groupNotifications.get(0).getTitle();
            }
            
            GroupHeader header = new GroupHeader(
                groupKey,
                groupNotifications.get(0).getAppName(),
                headerTitle,
                groupNotifications.size()
            );
            newItems.add(header);

            boolean wasExpanded = previousExpandedStates.getOrDefault(header.groupKey, false);
            expandedGroups.put(header.groupKey, wasExpanded);

            if (wasExpanded) {
                newItems.addAll(groupNotifications);
            }
        }

        DiffUtil.calculateDiff(new NotificationDiffCallback(items, newItems))
               .dispatchUpdatesTo(this);
        
        items.clear();
        items.addAll(newItems);
    }

    private void toggleGroup(GroupKey groupKey, int headerPosition) {
        boolean expanded = expandedGroups.getOrDefault(groupKey, false);
        expandedGroups.put(groupKey, !expanded);
        
        List<NotificationEntity> notifications = groupedNotifications.get(groupKey);
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

    private GroupKey createGroupKey(NotificationEntity notification) {
        String identifier;
        boolean isPhoneNumberGroup = false;

        if (notification.getPackageName().contains("whatsapp") && 
            notification.getTitle() != null && 
            notification.getTitle().matches(".*[0-9+].*")) {
            // Extract and clean phone number from the title
            String extracted = notification.getTitle().replaceAll("[^0-9+\\-]", "");
            identifier = extracted.replaceAll("[^0-9]", "");
            // Only create phone number group if we have 11 digits
            if (identifier.length() == 11) {
                isPhoneNumberGroup = true;
            } else {
                // Fallback to title prefix if not a valid phone number
                identifier = notification.getTitle().substring(0, 
                    Math.min(5, notification.getTitle().length()));
            }
        } else {
            // Use title prefix for non-phone number notifications
            identifier = notification.getTitle().substring(0, 
                Math.min(10, notification.getTitle().length()));
        }

        return new GroupKey(notification.getPackageName(), identifier, isPhoneNumberGroup);
    }

    private static class GroupKey {
        final String packageName;
        final String identifier;
        final boolean isPhoneNumberGroup;

        GroupKey(String packageName, String identifier, boolean isPhoneNumberGroup) {
            this.packageName = packageName;
            this.identifier = identifier;
            this.isPhoneNumberGroup = isPhoneNumberGroup;
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, identifier);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroupKey other = (GroupKey) o;
            return Objects.equals(packageName, other.packageName) &&
                   Objects.equals(identifier, other.identifier);
        }
    }

    static class GroupHeader {
        GroupKey groupKey;
        String appName;
        String displayTitle;
        int count;

        GroupHeader(GroupKey groupKey, String appName, String displayTitle, int count) {
            this.groupKey = groupKey;
            this.appName = appName;
            this.displayTitle = displayTitle;
            this.count = count;
        }

        String getPackageName() {
            return groupKey.packageName;
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
                return ((GroupHeader) oldItem).groupKey.equals(
                    ((GroupHeader) newItem).groupKey
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
                       oldHeader.displayTitle.equals(newHeader.displayTitle);
            } else {
                NotificationEntity oldNotif = (NotificationEntity) oldItem;
                NotificationEntity newNotif = (NotificationEntity) newItem;
                return oldNotif.getTitle().equals(newNotif.getTitle()) &&
                       oldNotif.getContent().equals(newNotif.getContent());
            }
        }
    }
}
