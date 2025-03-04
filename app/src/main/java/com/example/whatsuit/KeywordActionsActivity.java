package com.example.whatsuit;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whatsuit.adapter.KeywordActionsAdapter;
import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.KeywordActionEntity;
import com.example.whatsuit.data.KeywordActionDao;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class KeywordActionsActivity extends AppCompatActivity implements KeywordActionsAdapter.OnKeywordActionListener {

    private KeywordActionDao keywordActionDao;
    private KeywordActionsAdapter adapter;
    private View dialogView;
    private Uri selectedMediaUri;
    private String selectedMediaType;
    private ActivityResultLauncher<Intent> mediaPickerLauncher;
    private KeywordActionEntity currentEditAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyword_actions);

        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        keywordActionDao = AppDatabase.getDatabase(this).keywordActionDao();
        adapter = new KeywordActionsAdapter(this);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        TextView emptyView = findViewById(R.id.emptyView);
        LiveData<java.util.List<KeywordActionEntity>> actions = keywordActionDao.getAllKeywordActions();
        actions.observe(this, list -> {
            adapter.submitList(list);
            emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });

        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> showKeywordActionDialog(null));

        setupMediaPicker();
    }

    private void setupMediaPicker() {
        mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    handleMediaResult(result.getData().getData());
                }
            }
        );
    }

    private void showKeywordActionDialog(KeywordActionEntity action) {
        currentEditAction = action;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_keyword_action, null);
        
        TextInputEditText keywordInput = dialogView.findViewById(R.id.keywordInput);
        RadioGroup actionTypeGroup = dialogView.findViewById(R.id.actionTypeGroup);
        TextView selectedMediaName = dialogView.findViewById(R.id.selectedMediaName);
        ImageView mediaPreview = dialogView.findViewById(R.id.mediaPreview);
        
        dialogView.findViewById(R.id.selectMediaButton).setOnClickListener(v -> {
            selectedMediaType = actionTypeGroup.getCheckedRadioButtonId() == R.id.radioImage ? "IMAGE" : "VIDEO";
            String type = selectedMediaType.equals("IMAGE") ? "image/*" : "video/*";
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .setType(type)
                .addCategory(Intent.CATEGORY_OPENABLE);
            mediaPickerLauncher.launch(intent);
        });

        if (action != null) {
            keywordInput.setText(action.getKeyword());
            actionTypeGroup.check(action.getActionType().equals("IMAGE") ? R.id.radioImage : R.id.radioVideo);
            File existingFile = new File(action.getActionContent());
            selectedMediaName.setText(existingFile.getName());
            if (action.getActionType().equals("IMAGE")) {
                mediaPreview.setImageURI(Uri.fromFile(existingFile));
                mediaPreview.setVisibility(View.VISIBLE);
            }
        }

        builder.setTitle(action == null ? "Add Keyword Action" : "Edit Keyword Action")
               .setView(dialogView)
               .setPositiveButton("Save", (dialog, which) -> {
                   String keyword = keywordInput.getText().toString().trim();
                   if (keyword.isEmpty() || selectedMediaUri == null) {
                       return;
                   }

                   saveKeywordAction(keyword, selectedMediaType, selectedMediaUri, action);
               })
               .setNegativeButton("Cancel", null)
               .show();
    }

    private void handleMediaResult(Uri uri) {
        TextView selectedMediaName = dialogView.findViewById(R.id.selectedMediaName);
        ImageView mediaPreview = dialogView.findViewById(R.id.mediaPreview);
        
        selectedMediaUri = uri;
        selectedMediaName.setText(getFileName(uri));
        
        if (selectedMediaType.equals("IMAGE")) {
            mediaPreview.setImageURI(uri);
            mediaPreview.setVisibility(View.VISIBLE);
        } else {
            mediaPreview.setVisibility(View.GONE);
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void saveKeywordAction(String keyword, String actionType, Uri mediaUri, KeywordActionEntity existingAction) {
        new Thread(() -> {
            try {
                String mediaPath = copyMediaToAppStorage(mediaUri);
                
                KeywordActionEntity action;
                if (existingAction != null) {
                    action = existingAction;
                    // Delete old media file if it exists
                    new File(action.getActionContent()).delete();
                } else {
                    action = new KeywordActionEntity(keyword, actionType, mediaPath);
                }
                
                action.setKeyword(keyword);
                action.setActionType(actionType);
                action.setActionContent(mediaPath);

                if (existingAction != null) {
                    keywordActionDao.update(action);
                } else {
                    keywordActionDao.insert(action);
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("Failed to save media file: " + e.getMessage())
                        .setPositiveButton("OK", null)
                        .show();
                });
            }
        }).start();
    }

    private String copyMediaToAppStorage(Uri sourceUri) throws Exception {
        String fileName = UUID.randomUUID().toString() + "_" + getFileName(sourceUri);
        File destDir = new File(getFilesDir(), "media");
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        File destFile = new File(destDir, fileName);
        try (InputStream in = getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            
            // Ensure file is readable
            if (!destFile.setReadable(true, false)) {
                throw new Exception("Failed to set file as readable");
            }
            
            // On some devices, we need to ensure parent directory is also readable
            if (!destDir.setReadable(true, false)) {
                throw new Exception("Failed to set directory as readable");
            }
            
            // Verify file exists and is readable
            if (!destFile.exists() || !destFile.canRead()) {
                throw new Exception("File exists: " + destFile.exists() + ", readable: " + destFile.canRead());
            }
        }
        
        return destFile.getAbsolutePath();
    }

    @Override
    public void onEdit(KeywordActionEntity action) {
        showKeywordActionDialog(action);
    }

    @Override
    public void onDelete(KeywordActionEntity action) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete Keyword Action")
            .setMessage("Are you sure you want to delete this keyword action?")
            .setPositiveButton("Delete", (dialog, which) -> {
                new Thread(() -> {
                    // Delete the media file
                    new File(action.getActionContent()).delete();
                    // Delete the database entry
                    keywordActionDao.delete(action);
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onToggleEnabled(KeywordActionEntity action, boolean enabled) {
        new Thread(() -> {
            keywordActionDao.updateEnabled(action.getId(), enabled);
        }).start();
    }
}
