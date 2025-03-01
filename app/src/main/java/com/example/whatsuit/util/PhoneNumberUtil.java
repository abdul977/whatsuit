package com.example.whatsuit.util;

public class PhoneNumberUtil {
    /**
     * Extracts phone number from text by checking both international and local formats.
     * Handles various formats like:
     * - +234 123 456 7890
     * - +2341234567890
     * - 08012345678
     * - 234 812 345 6789
     */
    public static String extractPhoneNumber(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // Remove all non-numeric and '+' characters
        String cleaned = text.replaceAll("[^0-9+]", "");
        
        // Must have at least some digits
        if (!cleaned.matches(".*\\d+.*")) {
            return null;
        }

        // Handle international format (starting with + or country code)
        if (cleaned.startsWith("+") || cleaned.startsWith("234")) {
            // Remove + if present
            cleaned = cleaned.startsWith("+") ? cleaned.substring(1) : cleaned;
            
            // Convert local format to international if it starts with 0
            if (cleaned.startsWith("0")) {
                cleaned = "234" + cleaned.substring(1);
            }
            
            // Should have country code (234) plus at least 10 digits
            if (cleaned.length() >= 13) {
                return cleaned;
            }
        }
        // Handle local format (starting with 0)
        else if (cleaned.startsWith("0") && cleaned.length() >= 11) {
            return "234" + cleaned.substring(1);
        }

        return null;
    }
}