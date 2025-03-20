package com.example.whatsuit.util;

import android.content.Context;
import android.view.MenuItem;
import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.AutoReplyRule;
import com.example.whatsuit.data.AutoReplyRuleDao;
import com.example.whatsuit.data.NotificationEntity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.lang.ref.WeakReference;

public class AutoReplyManager {
    private static volatile AutoReplyManager instance;
    private WeakReference<Context> contextRef;
    private volatile ExecutorService executor;
    private volatile NotificationEntity currentNotification;
    private volatile MenuItem autoReplyMenuItem;

    public static AutoReplyManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AutoReplyManager.class) {
                if (instance == null) {
                    instance = new AutoReplyManager(context);
                }
            }
        }
        return instance;
    }

    public interface AutoReplyCallback {
        void onStatusChanged(boolean isDisabled);
    }

    public AutoReplyManager(Context context) {
        this.contextRef = new WeakReference<>(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void setCurrentNotification(NotificationEntity notification) {
        this.currentNotification = notification;
    }

    public void setMenuItem(MenuItem menuItem) {
        this.autoReplyMenuItem = menuItem;
        updateMenuTitle();
    }

    private void updateMenuTitle() {
        if (autoReplyMenuItem != null && currentNotification != null) {
            ExtractedInfo info = extractIdentifierInfo(currentNotification);
            
            executor.execute(() -> {
                Context context = contextRef.get();
                if (context == null) return;

                String identifier;
                String identifierType;
                if (info.phoneNumber != null && !info.phoneNumber.isEmpty()) {
                    identifier = info.phoneNumber;
                    identifierType = AutoReplyRule.TYPE_PHONE_NUMBER;
                } else {
                    identifier = info.titlePrefix;
                    identifierType = AutoReplyRule.TYPE_TITLE;
                }

                boolean isDisabled = AppDatabase.getDatabase(context).autoReplyRuleDao()
                    .isAutoReplyDisabled(currentNotification.getPackageName(), 
                                       identifier, 
                                       identifierType);
                
                autoReplyMenuItem.setTitle(isDisabled ? "Enable Auto-Reply" : "Disable Auto-Reply");
            });
        }
    }

    public void toggleAutoReply(AutoReplyCallback callback) {
        if (currentNotification == null) return;
        ExtractedInfo info = extractIdentifierInfo(currentNotification);
        toggleAutoReply(currentNotification.getPackageName(), info.phoneNumber, info.titlePrefix, callback);
    }

    public void toggleAutoReply(String packageName, String phoneNumber, String titlePrefix, AutoReplyCallback callback) {
        executor.execute(() -> {
            Context context = contextRef.get();
            if (context == null) return;
            AppDatabase db = AppDatabase.getDatabase(context);
            AutoReplyRuleDao ruleDao = db.autoReplyRuleDao();

            String identifier;
            String identifierType;
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                identifier = phoneNumber;
                identifierType = AutoReplyRule.TYPE_PHONE_NUMBER;
            } else {
                identifier = titlePrefix;
                identifierType = AutoReplyRule.TYPE_TITLE;
            }

            AutoReplyRule rule = ruleDao.findRule(packageName, identifier, identifierType);
            if (rule == null) {
                // Create new rule
                rule = new AutoReplyRule(packageName, identifier, identifierType, true);
                ruleDao.insert(rule);
            } else {
                // Toggle existing rule
                rule.setDisabled(!rule.isDisabled());
                ruleDao.update(rule);
            }
            
            callback.onStatusChanged(rule.isDisabled());
        });
    }

    public void isAutoReplyDisabled(String packageName, String phoneNumber, String titlePrefix, AutoReplyCallback callback) {
        executor.execute(() -> {
            Context context = contextRef.get();
            if (context == null) return;
            
            String identifier;
            String identifierType;
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                identifier = phoneNumber;
                identifierType = AutoReplyRule.TYPE_PHONE_NUMBER;
            } else {
                identifier = titlePrefix;
                identifierType = AutoReplyRule.TYPE_TITLE;
            }

            boolean isDisabled = AppDatabase.getDatabase(context).autoReplyRuleDao()
                .isAutoReplyDisabled(packageName, identifier, identifierType);
            
            if (callback != null) {
                callback.onStatusChanged(isDisabled);
            }
        });
    }

    private static class ExtractedInfo {
        final String phoneNumber;
        final String titlePrefix;

        ExtractedInfo(String phoneNumber, String titlePrefix) {
            this.phoneNumber = phoneNumber;
            this.titlePrefix = titlePrefix;
        }
    }

    private ExtractedInfo extractIdentifierInfo(NotificationEntity notification) {
        String phoneNumber = "";
        String titlePrefix = "";
        
        if (notification.getPackageName().contains("whatsapp") && 
            notification.getContent() != null && 
            notification.getContent().matches(".*[0-9+].*")) {
            
            String content = notification.getContent();
            // Extract numbers, plus signs, and hyphens, then clean up
            String extracted = content.replaceAll("[^0-9+\\-]", "");
            // Remove all non-digits for final comparison
            phoneNumber = extracted.replaceAll("[^0-9]", "");
        } else {
            String title = notification.getTitle();
            if (title != null && !title.isEmpty()) {
                titlePrefix = title.substring(0, Math.min(10, title.length()));
            }
        }
        
        return new ExtractedInfo(phoneNumber, titlePrefix);
    }

    public void shutdown() {
        ExecutorService executorToShutdown = executor;
        if (executorToShutdown != null) {
            try {
                // First attempt to finish any pending operations
                executorToShutdown.submit(() -> {}).get();
                executorToShutdown.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
            executor = null;
        }
        // Clear references
        contextRef.clear();
        currentNotification = null;
        autoReplyMenuItem = null;
    }
}
