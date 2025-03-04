package com.example.whatsuit.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "keyword_actions")
public class KeywordActionEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String keyword;
    private String actionType; // "IMAGE", "VIDEO", "TEXT"
    private String actionContent; // File path or content
    private boolean isEnabled;
    private long createdAt;

    public KeywordActionEntity(String keyword, String actionType, String actionContent) {
        this.keyword = keyword;
        this.actionType = actionType;
        this.actionContent = actionContent;
        this.isEnabled = true;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters
    public long getId() {
        return id;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getActionType() {
        return actionType;
    }

    public String getActionContent() {
        return actionContent;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // Setters
    public void setId(long id) {
        this.id = id;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public void setActionContent(String actionContent) {
        this.actionContent = actionContent;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
