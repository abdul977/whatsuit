package com.example.whatsuit.util;

import android.util.Log;
import com.example.whatsuit.data.NotificationEntity;

/**
 * Utility class for generating consistent conversation IDs across the application.
 * Uses NotificationUtils for standardized handling of package names, phone numbers,
 * and titles.
 */
public class ConversationIdGenerator {
    private static final String TAG = "ConversationIdGenerator";

    /**
     * Generates a consistent conversation ID for a notification.
     * For WhatsApp notifications with phone numbers, format is "whatsapp_[normalized_number]"
     * For WhatsApp notifications without phone numbers, format is "whatsapp_contact_[normalized_name]"
     * For other apps, format is "[package_name]_[normalized_title]"
     *
     * @param entity The notification entity to generate ID for
     * @return A consistent conversation ID string
     */
    public static String generate(NotificationEntity entity) {
        if (entity == null) {
            Log.w(TAG, "Cannot generate conversation ID for null entity");
            return "";
        }

        String conversationId;
        
        if (NotificationUtils.isWhatsAppPackage(entity.getPackageName())) {
            String title = entity.getTitle();
            if (NotificationUtils.hasPhoneNumber(title)) {
                String normalizedNumber = NotificationUtils.normalizePhoneNumber(title);
                conversationId = "whatsapp_" + normalizedNumber;
                Log.d(TAG, "Generated WhatsApp phone number based ID: " + conversationId);
            } else {
                // For contact names, normalize by converting to lowercase and replacing spaces with underscores
                String normalizedName = title.trim().toLowerCase().replaceAll("\\s+", "_");
                conversationId = "whatsapp_contact_" + normalizedName;
                Log.d(TAG, "Generated WhatsApp contact name based ID: " + conversationId);
            }
        } else {
            // For other apps, use package name and normalized title
            String normalizedTitle = entity.getTitle().trim().toLowerCase().replaceAll("\\s+", "_");
            conversationId = entity.getPackageName() + "_" + normalizedTitle;
            Log.d(TAG, "Generated generic app ID: " + conversationId);
        }

        return conversationId;
    }
}