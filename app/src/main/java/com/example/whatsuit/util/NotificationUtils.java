package com.example.whatsuit.util;

import android.util.Log;

/**
 * Utility class for standardizing notification handling logic across the application.
 * Provides consistent methods for WhatsApp package detection, phone number handling,
 * and title normalization.
 */
public class NotificationUtils {
    private static final String TAG = "NotificationUtils";
    private static final String WHATSAPP_PACKAGE_PATTERN = "whatsapp";
    private static final int PHONE_NUMBER_LENGTH = 11;
    private static final int TITLE_PREFIX_LENGTH = 5;
    
    /**
     * Checks if a package name is a WhatsApp variant.
     * @param packageName The package name to check
     * @return true if the package name contains "whatsapp" (case-insensitive)
     */
    public static boolean isWhatsAppPackage(String packageName) {
        boolean result = packageName != null && 
                        packageName.toLowerCase().contains(WHATSAPP_PACKAGE_PATTERN);
        Log.d(TAG, "isWhatsAppPackage check for " + packageName + ": " + result);
        return result;
    }
    
    /**
     * Checks if text contains any digits or plus signs (potential phone number).
     * @param text The text to check
     * @return true if the text contains any digits or plus signs
     */
    public static boolean hasPhoneNumber(String text) {
        boolean result = text != null && text.matches(".*[0-9+].*");
        Log.d(TAG, "hasPhoneNumber check for " + text + ": " + result);
        return result;
    }
    
    /**
     * Normalizes a phone number by removing non-digit characters and keeping last 11 digits.
     * @param text The text containing a phone number
     * @return Normalized phone number string or empty string if input is null
     */
    public static String normalizePhoneNumber(String text) {
        if (text == null) return "";
        
        // Remove all non-digit characters
        String numbers = text.replaceAll("[^0-9]", "");
        
        // Keep only the last PHONE_NUMBER_LENGTH digits if longer
        String result = numbers.length() > PHONE_NUMBER_LENGTH ? 
            numbers.substring(numbers.length() - PHONE_NUMBER_LENGTH) : numbers;
            
        Log.d(TAG, "Normalized phone number from " + text + " to " + result);
        return result;
    }
    
    /**
     * Gets a standardized prefix from a title for non-phone number based matching.
     * @param title The title to get prefix from
     * @return Title prefix or empty string if input is null
     */
    public static String getTitlePrefix(String title) {
        if (title == null) return "";
        String result = title.substring(0, Math.min(TITLE_PREFIX_LENGTH, title.length()));
        Log.d(TAG, "Got title prefix from " + title + ": " + result);
        return result;
    }
}