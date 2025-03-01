package com.example.whatsuit.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications")
public class NotificationEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private String packageName;
    private String appName;
    private String title;
    private String content;
    private long timestamp;
    private String icon;
    private boolean autoReplied;
    private String autoReplyContent;
    private boolean autoReplyDisabled;
    private String conversationId;
    
    @Ignore
    private long group_timestamp;
    
    @Ignore
    private int group_count;

    public NotificationEntity(String packageName, String appName, String title, String content,
                            String conversationId, long timestamp, String icon) {
        this.packageName = packageName;
        this.appName = appName;
        this.title = title;
        this.content = content;
        this.conversationId = conversationId;
        this.timestamp = timestamp;
        this.icon = icon;
        this.autoReplied = false;
        this.autoReplyContent = null;
        this.autoReplyDisabled = false;
    }

    // Getters and Setters
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    
    public boolean isAutoReplied() { return autoReplied; }
    public void setAutoReplied(boolean autoReplied) { this.autoReplied = autoReplied; }
    
    public String getAutoReplyContent() { return autoReplyContent; }
    public void setAutoReplyContent(String autoReplyContent) { this.autoReplyContent = autoReplyContent; }
    
    public boolean isAutoReplyDisabled() { return autoReplyDisabled; }
    public void setAutoReplyDisabled(boolean autoReplyDisabled) { this.autoReplyDisabled = autoReplyDisabled; }
    
    public long getGroupTimestamp() { return group_timestamp; }
    public void setGroupTimestamp(long group_timestamp) { this.group_timestamp = group_timestamp; }
    
    public int getGroupCount() { return group_count; }
    public void setGroupCount(int group_count) { this.group_count = group_count; }
}
