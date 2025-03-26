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
     * Checks if text represents a phone number (not just contains numbers).
     * This method distinguishes between actual phone numbers and text that just happens
     * to contain numbers (like group names).
     * @param text The text to check
     * @return true if the text appears to be a phone number
     */
    public static boolean hasPhoneNumber(String text) {
        // Match strings that are primarily numeric (contains at least 7 digits)
        // and optionally have plus signs, spaces, or dashes between numbers
        boolean result = text != null && text.matches(".*(?:\\d[\\s-]*){7,}.*");
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
    private static final int PREFIX_LENGTH = 5;

    /**
     * Gets a standardized prefix from a title for non-phone number based matching.
     * Takes the first 5 letters of the title for matching similar titles.
     * @param title The title to get prefix from
     * @return First 5 letters of title in lowercase or empty string if input is null/too short
     */
    public static String getTitlePrefix(String title) {
        if (title == null || title.trim().isEmpty()) return "";
        
        String normalized = title.toLowerCase().trim();
        // If title is shorter than PREFIX_LENGTH, use the entire title
        if (normalized.length() < PREFIX_LENGTH) {
            Log.d(TAG, "Got short title prefix from " + title + ": " + normalized);
            return normalized;
        }
        
        String prefix = normalized.substring(0, PREFIX_LENGTH);
        Log.d(TAG, "Got title prefix from " + title + ": " + prefix);
        return prefix;
    }
}
