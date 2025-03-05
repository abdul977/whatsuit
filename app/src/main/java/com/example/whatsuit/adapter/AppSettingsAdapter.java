package com.example.whatsuit.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.whatsuit.R;
import com.example.whatsuit.data.AppSettingEntity;
import java.util.List;

public class AppSettingsAdapter extends RecyclerView.Adapter<AppSettingsAdapter.ViewHolder> {
    private List<AppSettingEntity> appSettings;
    private OnAppSettingChangedListener listener;

    public interface OnAppSettingChangedListener {
        void onAppSettingChanged(AppSettingEntity setting, boolean enabled);
    }

    public AppSettingsAdapter(OnAppSettingChangedListener listener) {
        this.listener = listener;
    }

    public void setAppSettings(List<AppSettingEntity> appSettings) {
        this.appSettings = appSettings;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_setting, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppSettingEntity setting = appSettings.get(position);
        holder.appNameTextView.setText(setting.getAppName());
        holder.enableSwitch.setChecked(setting.isAutoReplyEnabled());
        holder.enableGroupsSwitch.setChecked(setting.isAutoReplyGroupsEnabled());

        holder.enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setting.setAutoReplyEnabled(isChecked);
            listener.onAppSettingChanged(setting, isChecked);
        });

        holder.enableGroupsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setting.setAutoReplyGroupsEnabled(isChecked);
            listener.onAppSettingChanged(setting, isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return appSettings != null ? appSettings.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView appNameTextView;
        Switch enableSwitch;
        Switch enableGroupsSwitch;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            appNameTextView = itemView.findViewById(R.id.text_app_name);
            enableSwitch = itemView.findViewById(R.id.switch_app_enable);
            enableGroupsSwitch = itemView.findViewById(R.id.switch_app_enable_groups);
        }
    }
}
