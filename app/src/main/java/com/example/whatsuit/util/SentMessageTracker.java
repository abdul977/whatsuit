package com.example.whatsuit.util;

import android.util.Log;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * Tracks recently sent auto-reply messages to prevent feedback loops
 */
public class SentMessageTracker {
    private static final String TAG = "SentMessageTracker";
    private static final int MAX_TRACKED_MESSAGES = 20;
    private static final long MESSAGE_TTL_MS = TimeUnit.MINUTES.toMillis(5); // Track for 5 minutes
    
    private static final SentMessageTracker instance = new SentMessageTracker();
    
    public static SentMessageTracker getInstance() {
        return instance;
    }
    
    // Store sent messages with timestamps
    private final LinkedList<TrackedMessage> sentMessages = new LinkedList<>();
    
    // Record a sent message
    public synchronized void recordSentMessage(String content) {
        sentMessages.addFirst(new TrackedMessage(content, System.currentTimeMillis()));
        pruneOldMessages();
        
        if (sentMessages.size() > 1) {
            Log.d(TAG, "Currently tracking " + sentMessages.size() + " sent messages");
        }
    }
    
    // Check if the provided content matches any recently sent message
    public synchronized boolean isLikelyOwnMessage(String content) {
        pruneOldMessages();
        
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String normalizedInput = normalizeContent(content);
        for (TrackedMessage msg : sentMessages) {
            String normalizedSent = normalizeContent(msg.content);
            
            // Check for exact match or high similarity
            if (normalizedInput.equals(normalizedSent) || 
                (normalizedInput.length() > 10 && calculateSimilarity(normalizedInput, normalizedSent) > 0.85)) {
                Log.d(TAG, "Detected likely own message: " + content);
                return true;
            }
        }
        
        return false;
    }
    
    // Remove expired messages
    private void pruneOldMessages() {
        long cutoffTime = System.currentTimeMillis() - MESSAGE_TTL_MS;
        while (!sentMessages.isEmpty() && sentMessages.getLast().timestamp < cutoffTime) {
            sentMessages.removeLast();
        }
        
        // Also ensure we don't track too many messages
        while (sentMessages.size() > MAX_TRACKED_MESSAGES) {
            sentMessages.removeLast();
        }
    }
    
    // Normalize message content for better comparison
    private String normalizeContent(String content) {
        return content.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }
    
    // Calculate similarity between two strings (simple Dice coefficient)
    private double calculateSimilarity(String s1, String s2) {
        // Create sets of character bigrams
        java.util.Set<String> set1 = new java.util.HashSet<>();
        java.util.Set<String> set2 = new java.util.HashSet<>();
        
        for (int i = 0; i < s1.length() - 1; i++) {
            set1.add(s1.substring(i, i + 2));
        }
        
        for (int i = 0; i < s2.length() - 1; i++) {
            set2.add(s2.substring(i, i + 2));
        }
        
        // Find intersection size
        int intersectionSize = 0;
        for (String bigram : set1) {
            if (set2.contains(bigram)) {
                intersectionSize++;
            }
        }
        
        // Calculate Dice coefficient
        return (2.0 * intersectionSize) / (set1.size() + set2.size());
    }
    
    // Internal class to store message with timestamp
    private static class TrackedMessage {
        final String content;
        final long timestamp;
        
        TrackedMessage(String content, long timestamp) {
            this.content = content;
            this.timestamp = timestamp;
        }
    }
}
