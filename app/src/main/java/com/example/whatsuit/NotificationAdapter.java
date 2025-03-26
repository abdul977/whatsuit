package com.example.whatsuit;

import android.content.Intent;
import android.util.Log;
import android.view.MenuItem;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;

import com.google.android.material.chip.Chip;

import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.util.AutoReplyManager;
import com.example.whatsuit.util.ConversationIdGenerator;
import com.example.whatsuit.util.NotificationUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {
    private static final String TAG = NotificationAdapter.class.getSimpleName();

    private List<NotificationEntity> notifications = new ArrayList<>();
    private List<NotificationEntity> allNotifications = new ArrayList<>();
    private AutoReplyManager autoReplyManager;
    private PopupMenu activePopupMenu;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public NotificationAdapter(AutoReplyManager autoReplyManager) {
        this.autoReplyManager = autoReplyManager;
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

        // Add ripple effect and handle clicks
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), NotificationDetailActivity.class);
            intent.putExtra("notification_id", notification.getId());
            
            // Start activity with shared element transition
            v.getContext().startActivity(intent);
        });

        // Show popup menu when menu button is clicked
        holder.menuButton.setOnClickListener(menuView -> showPopupMenu(menuView, notification));

        // Update auto-reply status
        updateAutoReplyStatusAsync(holder, notification);
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
        allNotifications.clear();
        allNotifications.addAll(newNotifications);
        calculateDiff(oldNotifications, newNotifications);
    }

    public void filterNotifications(String query) {
        List<NotificationEntity> filteredList = new ArrayList<>();
        for (NotificationEntity notification : allNotifications) {
            if (notification.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                notification.getContent().toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(notification);
            }
        }
        setNotifications(filteredList);
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

    private void showPopupMenu(View view, NotificationEntity notification) {
        if (activePopupMenu != null) {
            activePopupMenu.dismiss();
        }
        
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        activePopupMenu = popup;
        
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.notification_item_menu, popup.getMenu());
        
        MenuItem autoReplyItem = popup.getMenu().findItem(R.id.action_toggle_auto_reply);
        autoReplyItem.setEnabled(false);
        
        popup.setOnDismissListener(menu -> activePopupMenu = null);
        
        ExtractedInfo info = extractIdentifierInfo(notification);
        
        autoReplyManager.isAutoReplyDisabled(
            notification.getPackageName(),
            info.phoneNumber,
            info.titlePrefix,
            isDisabled -> mainHandler.post(() -> {
                autoReplyItem.setTitle(isDisabled ? "Enable Auto-Reply" : "Disable Auto-Reply");
                autoReplyItem.setEnabled(true);
            })
        );

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_toggle_auto_reply) {
                toggleAutoReply(notification);
                return true;
            } else if (item.getItemId() == R.id.action_view_details) {
                Intent intent = new Intent(view.getContext(), NotificationDetailActivity.class);
                intent.putExtra("notification_id", notification.getId());
                intent.putExtra("show_conversations_tab", true);
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
        // Log both the stored and generated IDs to verify if they match
        String conversationId = notification.getConversationId();
        String generatedId = ConversationIdGenerator.generate(notification);
        
        Log.d(TAG, "Toggling auto-reply:" +
            "\nStored ID: " + conversationId +
            "\nGenerated ID: " + generatedId +
            "\nTitle: " + notification.getTitle());
        autoReplyManager.toggleAutoReply(
            notification.getPackageName(),
            info.phoneNumber,
            info.titlePrefix,
            generatedId,
            isDisabled -> mainHandler.post(this::notifyDataSetChanged)
        );
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
            isDisabled -> mainHandler.post(() -> {
                holder.autoReplyStatusChip.setText(isDisabled ? "Auto-reply disabled" : "Auto-reply enabled");
                holder.autoReplyStatusChip.setEnabled(true);
                holder.autoReplyStatusChip.setChipBackgroundColorResource(
                    isDisabled ? R.color.md_theme_errorContainer : R.color.md_theme_primaryContainer
                );
                holder.autoReplyStatusChip.setTextColor(
                    holder.autoReplyStatusChip.getContext().getColor(
                        isDisabled ? R.color.md_theme_onErrorContainer : R.color.md_theme_onPrimaryContainer
                    )
                );
            })
        );
    }

    private ExtractedInfo extractIdentifierInfo(NotificationEntity notification) {
        String phoneNumber = "";
        String titlePrefix = "";
        
        if (notification.getPackageName().contains("whatsapp") && 
            notification.getTitle() != null && 
            notification.getTitle().matches(".*[0-9+].*")) {
            try {
                phoneNumber = NotificationUtils.normalizePhoneNumber(notification.getTitle());
            } catch (Exception e) {
                titlePrefix = NotificationUtils.getTitlePrefix(notification.getTitle());
            }
        } else {
            titlePrefix = NotificationUtils.getTitlePrefix(notification.getTitle());
        }
        
        return new ExtractedInfo(phoneNumber, titlePrefix);
    }

    private static class ExtractedInfo {
        final String phoneNumber;
        final String titlePrefix;

        ExtractedInfo(String phoneNumber, String titlePrefix) {
            this.phoneNumber = phoneNumber;
            this.titlePrefix = titlePrefix;
        }
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView timestamp;
        TextView notificationTitle;
        TextView notificationContent;
        ImageButton menuButton;
        Chip autoReplyStatusChip;

        NotificationViewHolder(View itemView) {
            super(itemView);
            timestamp = itemView.findViewById(R.id.timestamp);
            notificationTitle = itemView.findViewById(R.id.notificationTitle);
            notificationContent = itemView.findViewById(R.id.notificationContent);
            menuButton = itemView.findViewById(R.id.menuButton);
            autoReplyStatusChip = itemView.findViewById(R.id.autoReplyStatusChip);
        }
    }

    public void cleanup() {
        if (activePopupMenu != null) {
            activePopupMenu.dismiss();
            activePopupMenu = null;
        }
    }
}
