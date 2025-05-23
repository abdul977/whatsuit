# Backup and Restore Functionality Guide

## Overview

The WhatSuit application now includes comprehensive backup and restore functionality that allows users to:

1. **Create backups** of all application data and save them to local storage
2. **Restore data** from previously created backup files
3. **Transfer data** between devices or app installations

## Features

### What Gets Backed Up

The backup includes ALL application data:

- **Database Tables:**
  - All notifications and their metadata
  - Conversation history and AI responses
  - Gemini API configuration
  - Custom prompt templates
  - Per-app auto-reply settings
  - Keyword actions and triggers
  - Reply count tracking data

- **SharedPreferences:**
  - Global auto-reply settings
  - Processed notification tracking
  - User preferences

- **Media Files:**
  - Keyword action media files (images, videos)
  - All files stored in the app's media directory

### Backup Format

- Backups are created as **ZIP files** for easy storage and transfer
- Files are named with timestamp: `WhatSuit_Backup_YYYY-MM-DD_HH-MM-SS.zip`
- Contains JSON files for structured data and original media files
- Includes metadata for version compatibility checking

## How to Use

### Creating a Backup

1. **Via Menu Bar:**
   - Tap the menu icon (three dots) in the top toolbar
   - Select "Backup Data"

2. **Via FAB Menu:**
   - Tap the floating action button (FAB) at bottom right
   - Select "Backup Data" from the options

3. **Backup Process:**
   - Review the backup confirmation dialog
   - Tap "Create Backup"
   - Choose a location to save the backup file
   - Wait for the backup to complete
   - Receive confirmation when successful

### Restoring from Backup

1. **Via Menu Bar:**
   - Tap the menu icon (three dots) in the top toolbar
   - Select "Restore Data"

2. **Via FAB Menu:**
   - Tap the floating action button (FAB) at bottom right
   - Select "Restore Data" from the options

3. **Restore Process:**
   - Read the warning about data replacement
   - Tap "Select Backup File"
   - Choose your backup ZIP file
   - Confirm the final warning
   - Wait for the restore to complete
   - App will restart automatically

## Important Notes

### ⚠️ Warnings

- **Data Replacement:** Restore completely replaces ALL current data
- **No Undo:** Restore operations cannot be undone
- **Create Backup First:** Always create a backup before restoring
- **App Restart:** App automatically restarts after successful restore

### 💡 Best Practices

1. **Regular Backups:** Create backups regularly to avoid data loss
2. **Before Updates:** Always backup before app updates
3. **Device Transfer:** Use backup/restore to transfer data between devices
4. **Safe Storage:** Store backup files in secure, accessible locations
5. **Test Restores:** Occasionally test restore process with old backups

### 🔧 Technical Details

- **File Format:** ZIP archive containing JSON and media files
- **Compatibility:** Backup version 1.0 format
- **Storage:** Uses Android's Storage Access Framework (SAF)
- **Permissions:** Requires file access permissions
- **Size:** Backup size depends on amount of data and media files

## Troubleshooting

### Common Issues

1. **Backup Failed:**
   - Check available storage space
   - Ensure write permissions to selected location
   - Try a different storage location

2. **Restore Failed:**
   - Verify backup file is not corrupted
   - Check backup file format (must be ZIP)
   - Ensure backup was created by same app version

3. **File Not Found:**
   - Verify backup file still exists
   - Check file permissions
   - Try copying file to internal storage

4. **Permission Denied:**
   - Grant file access permissions in Android settings
   - Try selecting a different storage location
   - Restart app and try again

### Error Messages

- **"Invalid backup: Missing metadata file"** - Backup file is corrupted or not a valid backup
- **"Incompatible backup version"** - Backup was created by different app version
- **"Backup failed: Permission denied"** - Insufficient file access permissions
- **"Restore failed: File not found"** - Selected backup file cannot be accessed

## Support

If you encounter issues with backup/restore functionality:

1. Check this guide for troubleshooting steps
2. Verify you have the latest app version
3. Ensure sufficient storage space
4. Try creating a new backup to test functionality
5. Contact support with specific error messages

## Version History

- **v1.0** - Initial backup/restore implementation
  - Full database backup/restore
  - SharedPreferences backup/restore
  - Media files backup/restore
  - ZIP archive format
  - Progress tracking
  - Error handling and validation
