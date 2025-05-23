package com.example.whatsuit.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Entity class representing auto-reply count tracking per conversation.
 * Tracks how many auto-replies have been sent for each conversation to enforce limits.
 */
@Entity(
    tableName = "conversation_reply_count",
    indices = {
        @Index(value = {"conversationId"}, unique = true)
    }
)
public class ConversationReplyCount {
    @PrimaryKey
    @NonNull
    private String conversationId;
    
    private int replyCount;
    private long lastReplyTimestamp;
    private long firstReplyTimestamp;

    public ConversationReplyCount() {
        // Required no-args constructor for Room
    }

    public ConversationReplyCount(@NonNull String conversationId, int replyCount, 
                                 long lastReplyTimestamp, long firstReplyTimestamp) {
        this.conversationId = conversationId;
        this.replyCount = replyCount;
        this.lastReplyTimestamp = lastReplyTimestamp;
        this.firstReplyTimestamp = firstReplyTimestamp;
    }

    @NonNull
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(@NonNull String conversationId) {
        this.conversationId = conversationId;
    }

    public int getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(int replyCount) {
        this.replyCount = replyCount;
    }

    public long getLastReplyTimestamp() {
        return lastReplyTimestamp;
    }

    public void setLastReplyTimestamp(long lastReplyTimestamp) {
        this.lastReplyTimestamp = lastReplyTimestamp;
    }

    public long getFirstReplyTimestamp() {
        return firstReplyTimestamp;
    }

    public void setFirstReplyTimestamp(long firstReplyTimestamp) {
        this.firstReplyTimestamp = firstReplyTimestamp;
    }

    /**
     * Increments the reply count and updates the timestamp
     */
    public void incrementReplyCount() {
        this.replyCount++;
        this.lastReplyTimestamp = System.currentTimeMillis();
        if (this.firstReplyTimestamp == 0) {
            this.firstReplyTimestamp = this.lastReplyTimestamp;
        }
    }

    /**
     * Creates a new ConversationReplyCount with count 1
     */
    public static ConversationReplyCount createNew(@NonNull String conversationId) {
        long currentTime = System.currentTimeMillis();
        return new ConversationReplyCount(conversationId, 1, currentTime, currentTime);
    }
}
