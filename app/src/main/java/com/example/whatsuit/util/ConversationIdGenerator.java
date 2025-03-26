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

        Log.d(TAG, "Generating ID from NotificationEntity:" +
                   "\nPackage: " + entity.getPackageName() +
                   "\nTitle: " + entity.getTitle());

        String conversationId;
        
        if (NotificationUtils.isWhatsAppPackage(entity.getPackageName())) {
            String title = entity.getTitle();
            if (NotificationUtils.hasPhoneNumber(title)) {
                String normalizedNumber = NotificationUtils.normalizePhoneNumber(title);
                conversationId = "whatsapp_" + normalizedNumber;
                Log.d(TAG, "Generated WhatsApp phone number based ID: " + conversationId);
            } else {
                // For contact names, normalize by converting to lowercase and replacing spaces with underscores
                String normalizedName = NotificationUtils.getTitlePrefix(title).replaceAll("\\s+", "_");
                conversationId = "whatsapp_contact_" + normalizedName;
                Log.d(TAG, "Generated WhatsApp contact name based ID: " + conversationId);
            }
        } else {
            // For other apps, use package name and normalized title
            String normalizedTitle = entity.getTitle().trim().toLowerCase().replaceAll("\\s+", "_");
            conversationId = entity.getPackageName() + "_" + normalizedTitle;
            Log.d(TAG, "Generated generic app ID: " + conversationId);
        }

        Log.d(TAG, "Result of generate(NotificationEntity):" +
                   "\nInput title: " + entity.getTitle() +
                   "\nResult ID: " + conversationId);

        return conversationId;
    }
    
    /**
     * Generates a consistent conversation ID from individual parameters.
     * For WhatsApp notifications with phone numbers, format is "whatsapp_[normalized_number]"
     * For WhatsApp notifications without phone numbers, format is "whatsapp_contact_[normalized_name]"
     * For other apps, format is "[package_name]_[normalized_title]"
     *
     * @param packageName The package name of the app
     * @param phoneNumber The phone number (for WhatsApp notifications)
     * @param titlePrefix The title prefix for non-WhatsApp notifications
     * @return A consistent conversation ID string
     */
    public static String generate(String packageName, String phoneNumber, String titlePrefix) {
        if (packageName == null) {
            Log.w(TAG, "Cannot generate conversation ID for null package name");
            return "";
        }

        Log.d(TAG, "Generating ID from parameters:" +
                   "\nPackage: " + packageName +
                   "\nPhone: " + phoneNumber +
                   "\nTitle: " + titlePrefix);

        String conversationId;
        
        if (NotificationUtils.isWhatsAppPackage(packageName)) {
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                String normalizedNumber = NotificationUtils.normalizePhoneNumber(phoneNumber);
                conversationId = "whatsapp_" + normalizedNumber;
                Log.d(TAG, "Generated WhatsApp phone number based ID: " + conversationId);
            } else {
                conversationId = "whatsapp_contact_" + NotificationUtils.getTitlePrefix(titlePrefix).replaceAll("\\s+", "_");
                Log.d(TAG, "Generated WhatsApp contact name based ID: " + conversationId);
            }
        } else {
            conversationId = packageName + "_" + titlePrefix.trim().toLowerCase().replaceAll("\\s+", "_");
            Log.d(TAG, "Generated generic app ID: " + conversationId);
        }
        
        Log.d(TAG, "Result of generate(params):" +
                   "\nInput title: " + titlePrefix +
                   "\nResult ID: " + conversationId);
        return conversationId;
    }
}