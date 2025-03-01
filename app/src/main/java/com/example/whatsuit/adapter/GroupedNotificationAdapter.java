package com.example.whatsuit.adapter;

import android.content.Intent;
import android.content.pm.PackageManager;
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
    private static final int TYPE_APP_HEADER = 0;
    private static final int TYPE_CONVERSATION_HEADER = 1;
    private static final int TYPE_NOTIFICATION = 2;

    private final PackageManager packageManager;
    private final List<Object> items = new ArrayList<>();
    private final Map<String, Boolean> expandedApps = new HashMap<>();
    private final Map<String, Boolean> expandedConversations = new HashMap<>();
    private final AutoReplyManager autoReplyManager;

    public GroupedNotificationAdapter(PackageManager packageManager, AutoReplyManager autoReplyManager) {
        this.packageManager = packageManager;
        this.autoReplyManager = autoReplyManager;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view;
        switch (viewType) {
            case TYPE_APP_HEADER:
                view = inflater.inflate(R.layout.item_app_header, parent, false);
                return new AppHeaderViewHolder(view);
            case TYPE_CONVERSATION_HEADER:
                view = inflater.inflate(R.layout.item_app_header, parent, false); // Reuse same layout
                return new ConversationHeaderViewHolder(view);
            default:
                view = inflater.inflate(R.layout.item_notification, parent, false);
                return new NotificationViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AppHeaderViewHolder) {
            bindAppHeaderViewHolder((AppHeaderViewHolder) holder, (AppHeader) items.get(position));
        } else if (holder instanceof ConversationHeaderViewHolder) {
            bindConversationHeaderViewHolder((ConversationHeaderViewHolder) holder, (ConversationHeader) items.get(position));
        } else {
            bindNotificationViewHolder((NotificationViewHolder) holder, (NotificationEntity) items.get(position));
        }
    }

    private void bindAppHeaderViewHolder(AppHeaderViewHolder holder, AppHeader header) {
        holder.appName.setText(header.appName);
        holder.notificationCount.setText(String.valueOf(header.count));

        try {
            Drawable icon = packageManager.getApplicationIcon(header.packageName);
            holder.appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.appIcon.setImageResource(R.drawable.ic_app_placeholder);
        }

        boolean isExpanded = expandedApps.getOrDefault(header.packageName, false);
        holder.expandIcon.setRotation(isExpanded ? 180 : 0);

        holder.itemView.setOnClickListener(v -> toggleApp(header.packageName, holder.getAdapterPosition()));
    }

    private void bindConversationHeaderViewHolder(ConversationHeaderViewHolder holder, ConversationHeader header) {
        holder.appIcon.setVisibility(View.GONE); // Hide app icon for conversation header
        holder.appName.setText(header.displayTitle);
        holder.notificationCount.setText(String.valueOf(header.count));
        
        boolean isExpanded = expandedConversations.getOrDefault(header.conversationId, false);
        holder.expandIcon.setRotation(isExpanded ? 180 : 0);

        holder.itemView.setOnClickListener(v -> toggleConversation(header.conversationId, holder.getAdapterPosition()));
    }

    private void bindNotificationViewHolder(NotificationViewHolder holder, NotificationEntity notification) {
        holder.notificationTitle.setText(notification.getTitle());
        holder.notificationContent.setText(notification.getContent());
        holder.menuButton.setOnClickListener(v -> showPopupMenu(v, notification));
        updateAutoReplyStatus(holder, notification);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), NotificationDetailActivity.class);
            intent.putExtra("notification_id", notification.getId());
            v.getContext().startActivity(intent);
        });
    }

    private void updateAutoReplyStatus(NotificationViewHolder holder, NotificationEntity notification) {
        // Auto-reply status update logic remains the same
    }

    private void showPopupMenu(View view, NotificationEntity notification) {
        // Popup menu logic remains the same
    }

    public void updateNotifications(List<NotificationEntity> notifications) {
        Map<String, AppHeader> appHeaders = new HashMap<>();
        Map<String, List<NotificationEntity>> appGroups = new HashMap<>();
        
        // First, group notifications by app
        for (NotificationEntity notification : notifications) {
            String packageName = notification.getPackageName();
            appGroups.computeIfAbsent(packageName, k -> new ArrayList<>()).add(notification);
            
            if (!appHeaders.containsKey(packageName)) {
                appHeaders.put(packageName, new AppHeader(
                    packageName,
                    notification.getAppName(),
                    0 // Count will be updated later
                ));
            }
        }

        // Build the final list with proper hierarchy
        List<Object> newItems = new ArrayList<>();
        for (Map.Entry<String, List<NotificationEntity>> entry : appGroups.entrySet()) {
            String packageName = entry.getKey();
            List<NotificationEntity> appNotifications = entry.getValue();
            AppHeader appHeader = appHeaders.get(packageName);
            appHeader.count = appNotifications.size();
            
            // Add app header
            newItems.add(appHeader);
            
            // If app is expanded, add its conversations
            if (expandedApps.getOrDefault(packageName, false)) {
                Map<String, List<NotificationEntity>> conversations = new HashMap<>();
                
                // Group by conversation ID within app
                for (NotificationEntity notification : appNotifications) {
                    conversations.computeIfAbsent(
                        notification.getConversationId(),
                        k -> new ArrayList<>()
                    ).add(notification);
                }
                
                // Add conversation headers and notifications
                for (Map.Entry<String, List<NotificationEntity>> convEntry : conversations.entrySet()) {
                    String conversationId = convEntry.getKey();
                    List<NotificationEntity> conversationNotifs = convEntry.getValue();
                    NotificationEntity firstNotif = conversationNotifs.get(0);
                    
                    ConversationHeader convHeader = new ConversationHeader(
                        conversationId,
                        firstNotif.getTitle(),
                        conversationNotifs.size()
                    );
                    newItems.add(convHeader);
                    
                    // If conversation is expanded, add its notifications
                    if (expandedConversations.getOrDefault(conversationId, false)) {
                        newItems.addAll(conversationNotifs);
                    }
                }
            }
        }

        // Update the adapter
        DiffUtil.calculateDiff(new NotificationDiffCallback(items, newItems))
                .dispatchUpdatesTo(this);
        items.clear();
        items.addAll(newItems);
    }

    private void toggleApp(String packageName, int position) {
        boolean expanded = expandedApps.getOrDefault(packageName, false);
        expandedApps.put(packageName, !expanded);
        // Trigger a refresh to update the list
        notifyItemChanged(position);
    }

    private void toggleConversation(String conversationId, int position) {
        boolean expanded = expandedConversations.getOrDefault(conversationId, false);
        expandedConversations.put(conversationId, !expanded);
        // Trigger a refresh to update the list
        notifyItemChanged(position);
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof AppHeader) return TYPE_APP_HEADER;
        if (item instanceof ConversationHeader) return TYPE_CONVERSATION_HEADER;
        return TYPE_NOTIFICATION;
    }

    @Override
    public long getItemId(int position) {
        Object item = items.get(position);
        if (item instanceof AppHeader) {
            return ((AppHeader) item).packageName.hashCode();
        } else if (item instanceof ConversationHeader) {
            return ((ConversationHeader) item).conversationId.hashCode();
        } else {
            return ((NotificationEntity) item).getId();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class AppHeader {
        final String packageName;
        final String appName;
        int count;

        AppHeader(String packageName, String appName, int count) {
            this.packageName = packageName;
            this.appName = appName;
            this.count = count;
        }
    }

    static class ConversationHeader {
        final String conversationId;
        final String displayTitle;
        final int count;

        ConversationHeader(String conversationId, String displayTitle, int count) {
            this.conversationId = conversationId;
            this.displayTitle = displayTitle;
            this.count = count;
        }
    }

    static class AppHeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        Chip notificationCount;
        ImageView expandIcon;

        AppHeaderViewHolder(View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            notificationCount = itemView.findViewById(R.id.notificationCount);
            expandIcon = itemView.findViewById(R.id.expandIcon);
        }
    }

    static class ConversationHeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        Chip notificationCount;
        ImageView expandIcon;

        ConversationHeaderViewHolder(View itemView) {
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

            if (oldItem instanceof AppHeader) {
                return ((AppHeader) oldItem).packageName.equals(
                    ((AppHeader) newItem).packageName
                );
            } else if (oldItem instanceof ConversationHeader) {
                return ((ConversationHeader) oldItem).conversationId.equals(
                    ((ConversationHeader) newItem).conversationId
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

            if (oldItem instanceof AppHeader) {
                AppHeader oldHeader = (AppHeader) oldItem;
                AppHeader newHeader = (AppHeader) newItem;
                return oldHeader.count == newHeader.count &&
                       oldHeader.appName.equals(newHeader.appName);
            } else if (oldItem instanceof ConversationHeader) {
                ConversationHeader oldHeader = (ConversationHeader) oldItem;
                ConversationHeader newHeader = (ConversationHeader) newItem;
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
