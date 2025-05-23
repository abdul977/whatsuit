package com.example.whatsuit.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Transaction;

/**
 * Data Access Object for ConversationReplyCount operations.
 * Handles tracking and managing auto-reply counts per conversation.
 */
@Dao
public interface ConversationReplyCountDao {

    /**
     * Get the reply count for a specific conversation
     */
    @Query("SELECT * FROM conversation_reply_count WHERE conversationId = :conversationId")
    ConversationReplyCount getReplyCount(String conversationId);

    /**
     * Get the current reply count number for a conversation (returns 0 if not found)
     */
    @Query("SELECT COALESCE(replyCount, 0) FROM conversation_reply_count WHERE conversationId = :conversationId")
    int getCurrentReplyCount(String conversationId);

    /**
     * Insert or update reply count record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(ConversationReplyCount replyCount);

    /**
     * Update an existing reply count record
     */
    @Update
    void update(ConversationReplyCount replyCount);

    /**
     * Increment reply count for a conversation, creating record if it doesn't exist
     */
    @Transaction
    default void incrementReplyCount(String conversationId) {
        ConversationReplyCount existing = getReplyCount(conversationId);
        if (existing != null) {
            existing.incrementReplyCount();
            update(existing);
        } else {
            insertOrUpdate(ConversationReplyCount.createNew(conversationId));
        }
    }

    /**
     * Check if a conversation has reached the reply limit
     */
    default boolean hasReachedLimit(String conversationId, int maxReplies) {
        int currentCount = getCurrentReplyCount(conversationId);
        return currentCount >= maxReplies;
    }

    /**
     * Reset reply count for a conversation (useful for testing or manual reset)
     */
    @Query("DELETE FROM conversation_reply_count WHERE conversationId = :conversationId")
    void resetReplyCount(String conversationId);

    /**
     * Get all reply count records (useful for debugging/admin)
     */
    @Query("SELECT * FROM conversation_reply_count ORDER BY lastReplyTimestamp DESC")
    java.util.List<ConversationReplyCount> getAllReplyCounts();

    /**
     * Clean up old reply count records (older than specified days)
     */
    @Query("DELETE FROM conversation_reply_count WHERE lastReplyTimestamp < :cutoffTimestamp")
    void cleanupOldRecords(long cutoffTimestamp);

    // Synchronous methods for backup/restore
    @Query("SELECT * FROM conversation_reply_count ORDER BY lastReplyTimestamp DESC")
    List<ConversationReplyCount> getAllReplyCountsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ConversationReplyCount> replyCounts);

    /**
     * Get conversations that are near the limit (for potential warnings)
     */
    @Query("SELECT * FROM conversation_reply_count WHERE replyCount >= :threshold ORDER BY lastReplyTimestamp DESC")
    java.util.List<ConversationReplyCount> getConversationsNearLimit(int threshold);
}
