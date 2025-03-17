package com.example.whatsuit;

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

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class AppHeaderAdapter extends RecyclerView.Adapter<AppHeaderAdapter.AppHeaderViewHolder> {
    private List<AppHeader> headers = new ArrayList<>();
    private final PackageManager packageManager;
    private OnAppHeaderClickListener listener;

    public AppHeaderAdapter(PackageManager packageManager) {
        this.packageManager = packageManager;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public AppHeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_header, parent, false);
        return new AppHeaderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppHeaderViewHolder holder, int position) {
        AppHeader header = headers.get(position);
        
        // Configure app header
        holder.appName.setText(header.appName);
        holder.notificationCount.setText(String.valueOf(header.count));
        
        // Apply material transition animations
        holder.itemView.setTransitionName("header_" + header.packageName);
        
        try {
            Drawable icon = packageManager.getApplicationIcon(header.packageName);
            holder.appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.appIcon.setImageResource(R.drawable.ic_app_placeholder);
        }

        // Style the count chip
        Chip countChip = holder.notificationCount;
        countChip.setText(String.valueOf(header.count));
        countChip.setEnsureMinTouchTargetSize(false);
        countChip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        countChip.setCloseIconVisible(false);
        countChip.setClickable(false);

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppHeaderClick(header);
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return headers.get(position).packageName.hashCode();
    }

    @Override
    public int getItemCount() {
        return headers.size();
    }

    public void setHeaders(List<AppHeader> newHeaders) {
        List<AppHeader> oldHeaders = new ArrayList<>(headers);
        headers.clear();
        headers.addAll(newHeaders);
        
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldHeaders.size();
            }

            @Override
            public int getNewListSize() {
                return newHeaders.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldHeaders.get(oldItemPosition).packageName.equals(
                    newHeaders.get(newItemPosition).packageName);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                AppHeader oldHeader = oldHeaders.get(oldItemPosition);
                AppHeader newHeader = newHeaders.get(newItemPosition);
                return oldHeader.count == newHeader.count &&
                       oldHeader.appName.equals(newHeader.appName);
            }
        }).dispatchUpdatesTo(this);
    }

    public void setOnAppHeaderClickListener(OnAppHeaderClickListener listener) {
        this.listener = listener;
    }

    public interface OnAppHeaderClickListener {
        void onAppHeaderClick(AppHeader header);
    }

    public static class AppHeader {
        public final String packageName;
        public final String appName;
        public int count;

        public AppHeader(String packageName, String appName, int count) {
            this.packageName = packageName;
            this.appName = appName;
            this.count = count;
        }

        public AppHeader copy(String packageName, String appName, int count) {
            return new AppHeader(
                packageName != null ? packageName : this.packageName,
                appName != null ? appName : this.appName,
                count >= 0 ? count : this.count
            );
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
}
