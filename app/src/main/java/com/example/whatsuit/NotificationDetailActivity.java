package com.example.whatsuit;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whatsuit.adapter.ConversationHistoryAdapter;
import com.example.whatsuit.data.ConversationHistory;
import com.example.whatsuit.util.AutoReplyManager;
import com.example.whatsuit.util.TimeFilterHelper;
import com.example.whatsuit.viewmodel.NotificationDetailViewModel;

public class NotificationDetailActivity extends AppCompatActivity implements AutoReplyProvider {
    private TextView appNameTextView;
    private TextView titleTextView;
    private NotificationDetailViewModel viewModel;
    private TimeFilterHelper timeFilterHelper;
    private AutoReplyManager autoReplyManager;
    private RecyclerView conversationRecyclerView;
    private ConversationHistoryAdapter conversationAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.util.Log.d(TAG, "onCreate - starting");
        
        // Postpone transitions until content is ready
        android.util.Log.d(TAG, "postponing enter transition");
        postponeEnterTransition();
        
        setContentView(R.layout.activity_notification_detail);

        initializeViews();
        setupViewModel();
        setupHelpers();
        setupConversationHistory();
        handleIntent(getIntent());

        // Start transition once content is ready
        View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                rootView.getViewTreeObserver().removeOnPreDrawListener(this);
                android.util.Log.d(TAG, "starting postponed enter transition");
                startPostponedEnterTransition();
                return true;
            }
        });
    }

    private void initializeViews() {
        titleTextView = findViewById(R.id.titleTextView);
        appNameTextView = findViewById(R.id.appNameTextView);
        conversationRecyclerView = findViewById(R.id.conversationRecyclerView);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(NotificationDetailViewModel.class);
        
        viewModel.getCurrentNotification().observe(this, notification -> {
            if (notification != null) {
                titleTextView.setText(notification.getTitle());
                appNameTextView.setText(notification.getAppName());
                setTitle(notification.getAppName());
                autoReplyManager.setCurrentNotification(notification);
            } else {
                android.util.Log.e(TAG, "Notification data is null - invalid notification ID or database error");
                android.widget.Toast.makeText(
                    this,
                    "Could not load notification details",
                    android.widget.Toast.LENGTH_SHORT
                ).show();
                finish();
            }
        });
    }

    private void setupConversationHistory() {
        conversationAdapter = new ConversationHistoryAdapter();
        conversationRecyclerView.setAdapter(conversationAdapter);
        conversationRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set up FAB click listener
        findViewById(R.id.fabAddConversation).setOnClickListener(v -> showAddConversationDialog());

        // Set up edit listener
        conversationAdapter.setOnEditClickListener(conversation -> {
            showEditConversationDialog(conversation);
            return kotlin.Unit.INSTANCE;
        });

        // Observe conversation history changes
        viewModel.getConversationHistory().observe(this, conversations -> {
            if (conversations != null) {
                conversationAdapter.submitList(conversations);
            }
        });
    }

    private void handleIntent(Intent intent) {
        try {
            Uri data = intent.getData();
            if (data != null && "whatsuit".equals(data.getScheme())) {
                String notificationId = data.getLastPathSegment();
                if (notificationId != null) {
                    android.util.Log.d(TAG, "Loading notification from URI: " + notificationId);
                    viewModel.loadNotification(Long.parseLong(notificationId));
                } else {
                    throw new IllegalArgumentException("Missing notification ID in URI");
                }
            } else if (intent.hasExtra("notification_id")) {
                long notificationId = intent.getLongExtra("notification_id", -1);
                if (notificationId != -1) {
                    android.util.Log.d(TAG, "Loading notification from intent extra: " + notificationId);
                    viewModel.loadNotification(notificationId);
                } else {
                    throw new IllegalArgumentException("Invalid notification ID in intent extra");
                }
            } else {
                throw new IllegalArgumentException("No notification ID provided");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error handling intent: " + e.getMessage());
            android.widget.Toast.makeText(
                this,
                "Error opening notification details",
                android.widget.Toast.LENGTH_SHORT
            ).show();
            finish();
        }
    }

    private void setupHelpers() {
        timeFilterHelper = new TimeFilterHelper(this, (startTime, endTime, displayText) -> 
            viewModel.filterNotificationsByTimeRange(startTime, endTime));

        autoReplyManager = new AutoReplyManager(this);
    }

    @Override
    public AutoReplyManager getAutoReplyManager() {
        return autoReplyManager;
    }

    private static final String TAG = "NotificationDetailActivity";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.notification_detail_menu, menu);
        MenuItem autoReplyItem = menu.findItem(R.id.action_toggle_auto_reply);
        autoReplyManager.setMenuItem(autoReplyItem);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_filter) {
            timeFilterHelper.showTimeFilterDialog();
            return true;
        } else if (itemId == R.id.action_toggle_auto_reply) {
            autoReplyManager.toggleAutoReply(isDisabled -> 
                // Update UI if needed based on new auto-reply status
                invalidateOptionsMenu()
            );
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddConversationDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_conversation, null);
        
        android.widget.EditText messageEditText = dialogView.findViewById(R.id.messageEditText);
        android.widget.EditText responseEditText = dialogView.findViewById(R.id.responseEditText);
        android.widget.Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        android.widget.Button addButton = dialogView.findViewById(R.id.addButton);
        
        android.app.AlertDialog dialog = builder.setView(dialogView).create();
        
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        
        addButton.setOnClickListener(v -> {
            String message = messageEditText.getText().toString().trim();
            String response = responseEditText.getText().toString().trim();
            
            if (!message.isEmpty() || !response.isEmpty()) {
                viewModel.createConversation(message, response);
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }

    private void showEditConversationDialog(ConversationHistory conversation) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_conversation, null);
        
        android.widget.EditText messageEditText = dialogView.findViewById(R.id.messageEditText);
        android.widget.EditText responseEditText = dialogView.findViewById(R.id.responseEditText);
        
        messageEditText.setText(conversation.getMessage());
        responseEditText.setText(conversation.getResponse());
        
        builder.setView(dialogView)
               .setTitle("Edit Conversation")
               .setPositiveButton("Save", (dialog, which) -> {
                   String message = messageEditText.getText().toString().trim();
                   String response = responseEditText.getText().toString().trim();
                   
                   if (!message.isEmpty() || !response.isEmpty()) {
                       viewModel.editConversation(conversation, message, response);
                   }
               })
               .setNegativeButton("Cancel", null)
               .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Clean up any popups/menus when activity loses focus
        if (conversationAdapter != null) {
            conversationAdapter.cleanup();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.util.Log.d(TAG, "Activity destroying, cleaning up resources");
        
        // Cleanup AutoReplyManager
        if (autoReplyManager != null) {
            autoReplyManager.shutdown();
            autoReplyManager = null;
        }

        // Clear view model observers
        if (viewModel != null) {
            viewModel.getCurrentNotification().removeObservers(this);
            viewModel.getConversationHistory().removeObservers(this);
        }

        // Clear RecyclerView
        if (conversationRecyclerView != null) {
            conversationRecyclerView.setAdapter(null);
            conversationRecyclerView = null;
        }

        // Clean up adapter
        if (conversationAdapter != null) {
            conversationAdapter.cleanup();
            conversationAdapter = null;
        }

        // Clear all references
        titleTextView = null;
        appNameTextView = null;
        timeFilterHelper = null;
        viewModel = null;
    }
}
