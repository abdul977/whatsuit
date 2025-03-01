package com.example.whatsuit.adapter;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import com.example.whatsuit.data.ConversationHistory;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whatsuit.MainActivity;
import com.example.whatsuit.NotificationDetailActivity;
import com.example.whatsuit.R;
import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.util.AutoReplyManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
        autoReplyManager.isAutoReplyDisabled(
            notification.getPackageName(),
            notification.getConversationId(),
            "",
            isDisabled -> {
                if (holder.autoReplyStatusChip != null) {
                    holder.autoReplyStatusChip.setText(isDisabled ?
                        "Auto-reply disabled" : "Auto-reply enabled");
                    holder.autoReplyStatusChip.setVisibility(View.VISIBLE);
                }
            }
        );
    }

    private void showPopupMenu(View view, NotificationEntity notification) {
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.notification_item_menu, popup.getMenu());

        // Update auto-reply menu item state
        autoReplyManager.isAutoReplyDisabled(
            notification.getPackageName(),
            notification.getConversationId(),
            "",
            isDisabled -> {
                MenuItem toggleItem = popup.getMenu().findItem(R.id.action_toggle_auto_reply);
                if (toggleItem != null) {
                    toggleItem.setTitle(isDisabled ? 
                        "Enable Auto-Reply" : "Disable Auto-Reply");
                }
            }
        );

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_toggle_auto_reply) {
                // Toggle auto-reply using AutoReplyManager
                autoReplyManager.toggleAutoReply(
                    notification.getPackageName(),
                    notification.getConversationId(),
                    "",
                    isDisabled -> notifyItemChanged(items.indexOf(notification))
                );
                return true;
            } else if (itemId == R.id.action_view_details) {
                // Launch detail activity
                Intent intent = new Intent(view.getContext(), NotificationDetailActivity.class);
                intent.putExtra("notification_id", notification.getId());
                view.getContext().startActivity(intent);
                return true;
            } else if (itemId == R.id.action_view_history) {
                // Show conversation history dialog
                showHistoryDialog(view.getContext(), notification);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showHistoryDialog(Context context, NotificationEntity notification) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        
        View dialogView = LayoutInflater.from(context).inflate(
            R.layout.dialog_conversation_history,
            null
        );
        
        RecyclerView historyRecyclerView = dialogView.findViewById(R.id.historyRecyclerView);
        TextView emptyStateText = dialogView.findViewById(R.id.emptyStateText);
        View closeButton = dialogView.findViewById(R.id.closeButton);
        
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        ConversationHistoryAdapter historyAdapter = new ConversationHistoryAdapter();
        historyRecyclerView.setAdapter(historyAdapter);
        
        AlertDialog dialog = builder
            .setView(dialogView)
            .setCancelable(true)
            .create();

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dialog.dismiss());
        }
        
        // Use LiveData to observe related notifications
        AppDatabase db = AppDatabase.getDatabase(context);
        
        // Observe related notifications
        db.notificationDao()
            .getRelatedNotifications(notification.getId())
            .observe((LifecycleOwner) context, relatedNotifications -> {
                if (relatedNotifications != null && !relatedNotifications.isEmpty()) {
                    historyAdapter.setHistory(convertToHistory(notification, relatedNotifications));
                    historyRecyclerView.setVisibility(View.VISIBLE);
                    emptyStateText.setVisibility(View.GONE);
                } else {
                    historyRecyclerView.setVisibility(View.GONE);
                    emptyStateText.setVisibility(View.VISIBLE);
                }
            });
        
        dialog.show();

        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(params);
        }
    }
    
    // Helper method to convert NotificationEntity objects to ConversationHistory objects
    private List<ConversationHistory> convertToHistory(NotificationEntity mainNotification, List<NotificationEntity> related) {
        List<ConversationHistory> historyList = new ArrayList<>();
        
        // Add current notification
        ConversationHistory current = new ConversationHistory();
        current.setNotificationId(mainNotification.getId());
        current.setConversationId(mainNotification.getConversationId());
        current.setMessage(mainNotification.getContent());
        current.setResponse(mainNotification.getAutoReplyContent() != null ? 
                         mainNotification.getAutoReplyContent() : "");
        current.setTimestamp(mainNotification.getTimestamp());
        historyList.add(current);
        
        // Add related notifications
        for (NotificationEntity notification : related) {
            ConversationHistory history = new ConversationHistory();
            history.setNotificationId(notification.getId());
            history.setConversationId(notification.getConversationId());
            history.setMessage(notification.getContent());
            history.setResponse(notification.getAutoReplyContent() != null ? 
                             notification.getAutoReplyContent() : "");
            history.setTimestamp(notification.getTimestamp());
            historyList.add(history);
        }
        
        return historyList;
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
