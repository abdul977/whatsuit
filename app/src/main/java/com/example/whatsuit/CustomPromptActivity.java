package com.example.whatsuit;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.ConversationPrompt;
import com.example.whatsuit.data.ConversationPromptDao;
import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.data.PromptTemplate;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for managing custom prompts for specific conversations.
 * Allows users to create, edit, and delete custom prompts.
 */
public class CustomPromptActivity extends AppCompatActivity {
    private static final String TAG = "CustomPromptActivity";

    private String conversationId;
    private String conversationTitle;
    private EditText promptNameEditText;
    private EditText promptTemplateEditText;
    private Button saveButton;
    private Button deleteButton;
    private TextView titleTextView;
    private TextView descriptionTextView;
    private ExecutorService executor;
    private AppDatabase database;
    private ConversationPromptDao promptDao;
    private boolean isExistingPrompt = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_custom_prompt);
        Log.d(TAG, "Content view set");

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Custom Prompt");
        }

        // Initialize views
        promptNameEditText = findViewById(R.id.promptNameEditText);
        promptTemplateEditText = findViewById(R.id.promptTemplateEditText);
        saveButton = findViewById(R.id.saveButton);
        deleteButton = findViewById(R.id.deleteButton);
        titleTextView = findViewById(R.id.titleTextView);
        descriptionTextView = findViewById(R.id.descriptionTextView);

        // Initialize database and executor
        database = AppDatabase.getDatabase(this);
        promptDao = database.conversationPromptDao();
        executor = Executors.newSingleThreadExecutor();

        // Get conversation ID from intent
        Intent intent = getIntent();
        if (intent != null) {
            Log.d(TAG, "Intent received: " + intent);
            conversationId = intent.getStringExtra("conversation_id");
            conversationTitle = intent.getStringExtra("conversation_title");

            Log.d(TAG, "Conversation ID: " + conversationId);
            Log.d(TAG, "Conversation Title: " + conversationTitle);

            if (TextUtils.isEmpty(conversationId)) {
                Log.e(TAG, "No conversation ID provided");
                Toast.makeText(this, "Error: No conversation ID provided", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // Set conversation title
            if (!TextUtils.isEmpty(conversationTitle)) {
                titleTextView.setText("Custom Prompt for: " + conversationTitle);
            } else {
                titleTextView.setText("Custom Prompt for Conversation");
            }

            // Load existing prompt if available
            loadExistingPrompt();
        } else {
            Log.e(TAG, "No intent data provided");
            Toast.makeText(this, "Error: No conversation data provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set up save button
        saveButton.setOnClickListener(v -> savePrompt());

        // Set up delete button
        deleteButton.setOnClickListener(v -> confirmDelete());
    }

    private void loadExistingPrompt() {
        executor.execute(() -> {
            ConversationPrompt prompt = promptDao.getByConversationIdBlocking(conversationId);

            runOnUiThread(() -> {
                if (prompt != null) {
                    // Existing prompt found
                    isExistingPrompt = true;
                    promptNameEditText.setText(prompt.getName());
                    promptTemplateEditText.setText(prompt.getPromptTemplate());
                    deleteButton.setVisibility(View.VISIBLE);
                    descriptionTextView.setText(R.string.edit_custom_prompt_description);
                } else {
                    // No existing prompt, load default template as starting point
                    isExistingPrompt = false;
                    promptNameEditText.setText("Custom Prompt for " +
                        (TextUtils.isEmpty(conversationTitle) ? "Conversation" : conversationTitle));

                    // Load default template from database
                    executor.execute(() -> {
                        PromptTemplate defaultTemplate = database.geminiDao().getActiveTemplateBlocking();
                        String templateText = defaultTemplate != null ?
                            defaultTemplate.getTemplate() :
                            PromptTemplate.Companion.createDefault().getTemplate();

                        runOnUiThread(() -> {
                            promptTemplateEditText.setText(templateText);
                            deleteButton.setVisibility(View.GONE);
                            descriptionTextView.setText(R.string.new_custom_prompt_description);
                        });
                    });
                }
            });
        });
    }

    private void savePrompt() {
        String name = promptNameEditText.getText().toString().trim();
        String template = promptTemplateEditText.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Please enter a name for the prompt", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(template)) {
            Toast.makeText(this, "Please enter a template", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate template contains required placeholders
        if (!template.contains("{context}") || !template.contains("{message}")) {
            new AlertDialog.Builder(this)
                .setTitle("Invalid Template")
                .setMessage("The template must contain both {context} and {message} placeholders.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        // Save prompt to database
        executor.execute(() -> {
            ConversationPrompt prompt = new ConversationPrompt(
                conversationId,
                template,
                name,
                System.currentTimeMillis()
            );

            promptDao.insertBlocking(prompt);

            runOnUiThread(() -> {
                Toast.makeText(this, "Custom prompt saved", Toast.LENGTH_SHORT).show();
                isExistingPrompt = true;
                deleteButton.setVisibility(View.VISIBLE);
                descriptionTextView.setText(R.string.edit_custom_prompt_description);
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    private void confirmDelete() {
        if (!isExistingPrompt) {
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Delete Custom Prompt")
            .setMessage("Are you sure you want to delete this custom prompt? The conversation will use the default prompt template.")
            .setPositiveButton("Delete", (dialog, which) -> deletePrompt())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deletePrompt() {
        executor.execute(() -> {
            promptDao.deleteByConversationIdBlocking(conversationId);

            runOnUiThread(() -> {
                Toast.makeText(this, "Custom prompt deleted", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
