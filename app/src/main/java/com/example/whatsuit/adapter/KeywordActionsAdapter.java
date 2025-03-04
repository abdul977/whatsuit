package com.example.whatsuit.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whatsuit.R;
import com.example.whatsuit.data.KeywordActionEntity;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;

public class KeywordActionsAdapter extends ListAdapter<KeywordActionEntity, KeywordActionsAdapter.ViewHolder> {
    
    private final OnKeywordActionListener listener;

    public interface OnKeywordActionListener {
        void onEdit(KeywordActionEntity action);
        void onDelete(KeywordActionEntity action);
        void onToggleEnabled(KeywordActionEntity action, boolean enabled);
    }

    public KeywordActionsAdapter(OnKeywordActionListener listener) {
        super(new DiffUtil.ItemCallback<KeywordActionEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull KeywordActionEntity oldItem, @NonNull KeywordActionEntity newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull KeywordActionEntity oldItem, @NonNull KeywordActionEntity newItem) {
                return oldItem.getKeyword().equals(newItem.getKeyword()) &&
                       oldItem.getActionType().equals(newItem.getActionType()) &&
                       oldItem.getActionContent().equals(newItem.getActionContent()) &&
                       oldItem.isEnabled() == newItem.isEnabled();
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_keyword_action, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        KeywordActionEntity action = getItem(position);
        holder.bind(action);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView keywordText;
        private final TextView actionTypeText;
        private final TextView previewText;
        private final SwitchMaterial switchEnabled;
        private final ImageButton editButton;
        private final ImageButton deleteButton;

        ViewHolder(View itemView) {
            super(itemView);
            keywordText = itemView.findViewById(R.id.keywordText);
            actionTypeText = itemView.findViewById(R.id.actionTypeText);
            previewText = itemView.findViewById(R.id.previewText);
            switchEnabled = itemView.findViewById(R.id.switchEnabled);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }

        void bind(KeywordActionEntity action) {
            keywordText.setText(action.getKeyword());
            actionTypeText.setText(action.getActionType());
            
            File mediaFile = new File(action.getActionContent());
            previewText.setText(mediaFile.getName());

            switchEnabled.setChecked(action.isEnabled());
            switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) ->
                listener.onToggleEnabled(action, isChecked));

            editButton.setOnClickListener(v -> listener.onEdit(action));
            deleteButton.setOnClickListener(v -> listener.onDelete(action));
        }
    }
}
