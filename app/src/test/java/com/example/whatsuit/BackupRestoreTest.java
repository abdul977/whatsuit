package com.example.whatsuit;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.whatsuit.util.BackupRestoreManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test class for BackupRestoreManager functionality
 */
@RunWith(AndroidJUnit4.class)
public class BackupRestoreTest {
    
    private Context context;
    private BackupRestoreManager backupRestoreManager;
    
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        backupRestoreManager = new BackupRestoreManager(context);
    }
    
    @Test
    public void testBackupMetadataCreation() {
        // Test that backup metadata is created correctly
        BackupRestoreManager.BackupMetadata metadata = new BackupRestoreManager.BackupMetadata();
        metadata.version = "1.0";
        metadata.timestamp = System.currentTimeMillis();
        metadata.appVersion = "1.0";
        metadata.deviceInfo = "Test Device";
        
        assertNotNull(metadata);
        assertEquals("1.0", metadata.version);
        assertTrue(metadata.timestamp > 0);
        assertNotNull(metadata.appVersion);
        assertNotNull(metadata.deviceInfo);
    }
    
    @Test
    public void testDatabaseBackupStructure() {
        // Test that database backup structure is correct
        BackupRestoreManager.DatabaseBackup backup = new BackupRestoreManager.DatabaseBackup();
        
        assertNotNull(backup);
        // Initially all lists should be null
        assertNull(backup.notifications);
        assertNull(backup.geminiConfigs);
        assertNull(backup.conversationHistory);
        assertNull(backup.promptTemplates);
        assertNull(backup.appSettings);
        assertNull(backup.keywordActions);
        assertNull(backup.conversationReplyCounts);
    }
    
    @Test
    public void testPreferencesBackupStructure() {
        // Test that preferences backup structure is correct
        BackupRestoreManager.PreferencesBackup backup = new BackupRestoreManager.PreferencesBackup();
        
        assertNotNull(backup);
        // Initially all maps should be null
        assertNull(backup.whatsuitSettings);
        assertNull(backup.processedNotifications);
    }
    
    @Test
    public void testBackupManagerInitialization() {
        // Test that BackupRestoreManager initializes correctly
        assertNotNull(backupRestoreManager);
    }
    
    @Test
    public void testBackupCallbackInterface() {
        // Test that callback interface works correctly
        CountDownLatch latch = new CountDownLatch(3);
        
        BackupRestoreManager.BackupRestoreCallback callback = new BackupRestoreManager.BackupRestoreCallback() {
            @Override
            public void onProgress(String message, int progress) {
                assertNotNull(message);
                assertTrue(progress >= 0 && progress <= 100);
                latch.countDown();
            }
            
            @Override
            public void onSuccess(String message) {
                assertNotNull(message);
                latch.countDown();
            }
            
            @Override
            public void onError(String error) {
                assertNotNull(error);
                latch.countDown();
            }
        };
        
        // Simulate callback calls
        callback.onProgress("Test progress", 50);
        callback.onSuccess("Test success");
        callback.onError("Test error");
        
        try {
            assertTrue(latch.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Callback test interrupted");
        }
    }
}
