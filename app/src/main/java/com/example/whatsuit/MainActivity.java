package com.example.whatsuit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.widget.ImageButton;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.AppInfo;
import com.example.whatsuit.data.ConversationHistory;
import com.example.whatsuit.service.GeminiService;
import com.example.whatsuit.data.NotificationDao;
import com.example.whatsuit.data.NotificationEntity;
import com.example.whatsuit.data.ConversationHistoryDao;
import com.example.whatsuit.util.AutoReplyManager;
import com.example.whatsuit.util.BackupRestoreManager;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.example.whatsuit.adapter.GroupedNotificationAdapter;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    // Animation related fields
    private boolean isAppReady = false;
    private View splashOverlay;
    private ImageView splashIcon;
    private ProgressBar splashProgress;
    private TextView splashText;
    private RecyclerView recyclerView;
    private GroupedNotificationAdapter notificationAdapter;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefresh;
    private NotificationDao notificationDao;
    private ConversationHistoryDao conversationHistoryDao;
    private AutoReplyManager autoReplyManager;
    private ChipGroup filterChipGroup;
    private String selectedPackage = null;
    private List<AppInfo> appInfoList = new ArrayList<>();
    private ExtendedFloatingActionButton fab;
    private AppBarLayout appBarLayout;
    private long startTime = 0;
    private long endTime = Long.MAX_VALUE;
    private SearchView searchView;

    // Backup/Restore functionality
    private BackupRestoreManager backupRestoreManager;
    private ActivityResultLauncher<String> createBackupLauncher;
    private ActivityResultLauncher<String[]> selectBackupLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install splash screen and keep it visible during initialization
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> !isAppReady);

        super.onCreate(savedInstanceState);

        // Initialize backup/restore functionality
        initializeBackupRestore();

        // Set up our custom splash overlay first
        setContentView(R.layout.splash_screen_overlay);

        // Initialize splash screen views
        splashOverlay = findViewById(android.R.id.content);
        splashIcon = findViewById(R.id.splash_icon);
        splashProgress = findViewById(R.id.splash_progress);
        splashText = findViewById(R.id.splash_text);

        // Start initialization process in background
        initializeApp();
    }

    private void initializeApp() {
        // Simulate app initialization in background thread
        new Thread(() -> {
            try {
                // Initialize database with error handling
                AppDatabase db = null;
                int retryCount = 0;
                final int MAX_RETRIES = 3;

                while (retryCount < MAX_RETRIES && db == null) {
                    try {
                        db = AppDatabase.getDatabase(this);
                        // Test database connection
                        db.notificationDao().getCount();
                    } catch (Exception e) {
                        Log.e("MainActivity", "Database initialization attempt " + (retryCount + 1) + " failed", e);
                        retryCount++;
                        if (retryCount < MAX_RETRIES) {
                            Thread.sleep(1000); // Wait before retry
                        }
                    }
                }

                if (db == null) {
                    throw new RuntimeException("Failed to initialize database after " + MAX_RETRIES + " attempts");
                }

                notificationDao = db.notificationDao();
                conversationHistoryDao = db.conversationHistoryDao();

                // Initialize managers with error handling
                try {
                    autoReplyManager = new AutoReplyManager(this);
                } catch (Exception e) {
                    Log.e("MainActivity", "AutoReplyManager initialization failed", e);
                    // Continue without auto-reply functionality
                }

                // Delay for splash animation
                Thread.sleep(1200);

                // Signal successful completion
                new Handler(Looper.getMainLooper()).post(() -> {
                    startSplashAnimations();
                    // Show success message
                    Toast.makeText(this, "Initialization successful", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Critical error during initialization", e);
                // Show error dialog on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    new AlertDialog.Builder(this)
                        .setTitle("Initialization Error")
                        .setMessage("Failed to initialize app: " + e.getMessage())
                        .setPositiveButton("Retry", (dialog, which) -> {
                            // Retry initialization
                            initializeApp();
                        })
                        .setNegativeButton("Exit", (dialog, which) -> {
                            finish();
                        })
                        .setCancelable(false)
                        .show();
                });
            }
        }).start();
    }

    private void startSplashAnimations() {
        // Mark app as ready (this removes the system splash screen)
        isAppReady = true;

        // Start pulse animation on icon
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(splashIcon, View.SCALE_X, 1f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(splashIcon, View.SCALE_Y, 1f, 1.2f, 1f);

        AnimatorSet pulseSet = new AnimatorSet();
        pulseSet.playTogether(scaleX, scaleY);
        pulseSet.setDuration(1000);

        // After pulse, trigger particle animation
        pulseSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Hide progress and text
                splashProgress.animate().alpha(0f).setDuration(300).start();
                splashText.animate().alpha(0f).setDuration(300).start();

                // Create particle animation
                createParticleEffect();
            }
        });

        // Start the animation sequence
        pulseSet.start();
    }

    private void createParticleEffect() {
        // Simple fade out animation
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(splashIcon, View.ALPHA, 1f, 0f);
        fadeOut.setDuration(700);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                switchToMainContent();
            }
        });
        fadeOut.start();
    }

    private void switchToMainContent() {
        // Enable edge-to-edge and set main layout
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialize views from regular layout
        recyclerView = findViewById(R.id.notificationsRecyclerView);
        filterChipGroup = findViewById(R.id.filterChipGroup);
        emptyView = findViewById(R.id.emptyView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        fab = findViewById(R.id.fab);
        appBarLayout = findViewById(R.id.appBarLayout);
        searchView = findViewById(R.id.searchView);

        // Hide all initially for animation
        appBarLayout.setAlpha(0f);
        recyclerView.setAlpha(0f);
        recyclerView.setTranslationY(100f);
        if (fab != null) {
            fab.setScaleX(0f);
            fab.setScaleY(0f);
        }

        // Get the center point for circular reveal
        View rootView = findViewById(android.R.id.content);

        // Wait for layout to be ready
        rootView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                rootView.getViewTreeObserver().removeOnPreDrawListener(this);

                // Perform circular reveal animation
                int cx = rootView.getWidth() / 2;
                int cy = rootView.getHeight() / 2;
                float finalRadius = (float) Math.hypot(cx, cy);

                Animator circularReveal = ViewAnimationUtils.createCircularReveal(
                        rootView, cx, cy, 0f, finalRadius);
                circularReveal.setDuration(800);
                circularReveal.setInterpolator(new DecelerateInterpolator());

                circularReveal.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Start staggered animations for UI elements
                        animateUIElements();
                    }
                });

                circularReveal.start();
                return true;
            }
        });
    }

    private void animateUIElements() {
        // Animate toolbar first
        appBarLayout.animate()
            .alpha(1f)
            .setDuration(300)
            .start();

        // Animate recyclerView with delay
        recyclerView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(150)
            .start();

        // Animate FAB with additional delay and overshoot
        if (fab != null) {
            fab.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(300)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator())
                .start();
        }

        // Continue with regular MainActivity initialization
        finishMainActivityInitialization();
    }

    private void finishMainActivityInitialization() {
        // Set up notifications RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new GroupedNotificationAdapter(getPackageManager(), autoReplyManager);
        recyclerView.setAdapter(notificationAdapter);

        // Set up swipe to refresh
        swipeRefresh.setOnRefreshListener(() -> {
            loadNotifications();
            swipeRefresh.setRefreshing(false);
        });

        // Set up collapsing toolbar behavior
        setupCollapsingToolbar();

        // FAB click listener
        if (fab != null) {
            fab.setOnClickListener(v -> showOptionsMenu());
        }

        // Check for notification access permission
        if (!isNotificationServiceEnabled()) {
            showNotificationAccessDialog();
        }

        // Setup app filter
        setupAppFilter();

        // Setup search view
        setupSearchView();

        // Load notifications
        loadNotifications();

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public void showConversationHistory(NotificationEntity notification) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_conversation_history, null);
        TextView historyView = dialogView.findViewById(R.id.historyJson);
        TextView titleView = dialogView.findViewById(R.id.historyTitle);

        titleView.setText("Conversation History - " + notification.getTitle());
        historyView.setText("Loading conversation history...");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .setNegativeButton("Analyze", (d, which) -> {
                    showAnalysisDialog(notification);
                })
                .setNeutralButton("Copy", (d, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Conversation History", historyView.getText().toString());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "History copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .create();

        dialog.show();

        // Use a background thread to get conversation history directly
        new Thread(() -> {
            final AppDatabase db = AppDatabase.getDatabase(this);
            final long notificationId = notification.getId();

            // Get conversation history directly using Room's synchronous query
            final List<ConversationHistory> history = db.getConversationHistoryDao()
                    .getHistoryForNotificationSync(notificationId);

            // Update UI on main thread
            runOnUiThread(() -> {
                if (history != null && !history.isEmpty()) {
                    StringBuilder historyText = new StringBuilder();
                    for (int i = history.size() - 1; i >= 0; i--) {
                        ConversationHistory entry = history.get(i);
                        historyText.append("➤ Message: ").append(entry.getMessage()).append("\n\n");
                        historyText.append("↳ Response: ").append(entry.getResponse()).append("\n\n");
                        if (i > 0) historyText.append("-------------------\n\n");
                    }
                    historyView.setText(historyText.toString());
                } else {
                    historyView.setText("No conversation history available");
                }
            });
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_filter) {
            showTimeFilterDialog();
            return true;
        } else if (itemId == R.id.action_backup) {
            showBackupDialog();
            return true;
        } else if (itemId == R.id.action_restore) {
            showRestoreDialog();
            return true;
        } else if (itemId == R.id.action_gemini_config) {
            startActivity(new Intent(this, GeminiConfigActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showTimeFilterDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_time_filter, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter by Time");
        builder.setView(dialogView);

        RadioGroup timeFilterGroup = dialogView.findViewById(R.id.timeFilterRadioGroup);
        View customRangeLayout = dialogView.findViewById(R.id.customRangeLayout);
        TextView startDateInput = dialogView.findViewById(R.id.startDateInput);
        TextView endDateInput = dialogView.findViewById(R.id.endDateInput);

        // Show/hide custom range inputs
        timeFilterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            customRangeLayout.setVisibility(
                    checkedId == R.id.customRangeRadio ? View.VISIBLE : View.GONE
            );
        });

        // Setup date picker listeners
        startDateInput.setOnClickListener(v -> showDatePicker(startDateInput, true));
        endDateInput.setOnClickListener(v -> showDatePicker(endDateInput, false));

        builder.setPositiveButton("Apply", (dialog, which) -> {
            int selectedId = timeFilterGroup.getCheckedRadioButtonId();
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            if (selectedId == R.id.allTimeRadio) {
                startTime = 0;
                endTime = Long.MAX_VALUE;
            } else if (selectedId == R.id.yesterdayRadio) {
                cal.add(Calendar.DAY_OF_YEAR, -1);
                startTime = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_YEAR, 1);
                endTime = cal.getTimeInMillis();
            } else if (selectedId == R.id.lastWeekRadio) {
                cal.add(Calendar.WEEK_OF_YEAR, -1);
                startTime = cal.getTimeInMillis();
                endTime = System.currentTimeMillis();
            } else if (selectedId == R.id.lastMonthRadio) {
                cal.add(Calendar.MONTH, -1);
                startTime = cal.getTimeInMillis();
                endTime = System.currentTimeMillis();
            } else if (selectedId == R.id.lastYearRadio) {
                cal.add(Calendar.YEAR, -1);
                startTime = cal.getTimeInMillis();
                endTime = System.currentTimeMillis();
            }
            loadNotifications();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDatePicker(TextView dateInput, boolean isStartDate) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth, 0, 0, 0);
                    if (isStartDate) startTime = selectedDate.getTimeInMillis();
                    else endTime = selectedDate.getTimeInMillis() + 86400000; // Add 24 hours
                    dateInput.setText(String.format("%d/%d/%d", month + 1, dayOfMonth, year));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void setupCollapsingToolbar() {
        appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (Math.abs(verticalOffset) == appBarLayout.getTotalScrollRange()) {
                fab.shrink();
            } else {
                fab.extend();
            }
        });
    }

    private void setupAppFilter() {
        Chip allAppsChip = new Chip(this);
        allAppsChip.setText("All Apps");
        allAppsChip.setCheckable(true);
        allAppsChip.setChecked(true);
        filterChipGroup.addView(allAppsChip);

        notificationDao.getDistinctApps().observe(this, apps -> {
            filterChipGroup.removeViews(1, filterChipGroup.getChildCount() - 1);
            appInfoList.clear();
            appInfoList.addAll(apps);

            for (AppInfo app : apps) {
                Chip chip = new Chip(this);
                chip.setText(app.getAppName());
                chip.setCheckable(true);
                filterChipGroup.addView(chip);
            }
        });

        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                allAppsChip.setChecked(true);
                selectedPackage = null;
            } else {
                int selectedChipId = checkedIds.get(0);
                Chip selectedChip = group.findViewById(selectedChipId);
                int chipIndex = group.indexOfChild(selectedChip) - 1;

                if (chipIndex == -1) {
                    selectedPackage = null;
                } else if (chipIndex < appInfoList.size()) {
                    selectedPackage = appInfoList.get(chipIndex).getPackageName();
                }
            }
            loadNotifications();
        });
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterNotifications(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterNotifications(newText);
                return true;
            }
        });
    }

    private void filterNotifications(String query) {
        new Thread(() -> {
            List<NotificationEntity> filteredList = new ArrayList<>();
            for (NotificationEntity notification : notificationAdapter.getNotifications()) {
                if (notification.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                    notification.getContent().toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(notification);
                }
            }
            runOnUiThread(() -> notificationAdapter.updateNotifications(filteredList));
        }).start();
    }

    private LiveData<List<NotificationEntity>> currentNotificationsLiveData;

    private void loadNotifications() {
        try {
            // Remove any existing observer from the previous LiveData
            if (currentNotificationsLiveData != null) {
                currentNotificationsLiveData.removeObservers(this);
            }

            // Get new LiveData based on current filters
            currentNotificationsLiveData = selectedPackage == null ?
                    notificationDao.getSmartGroupedNotificationsInRange(startTime, endTime) :
                    notificationDao.getNotificationsForApp(selectedPackage);

            // Observe the new LiveData
            currentNotificationsLiveData.observe(this, notifications -> {
                try {
                    if (notifications != null && !notifications.isEmpty()) {
                        notificationAdapter.updateNotifications(notifications);
                        emptyView.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    } else {
                        notificationAdapter.updateNotifications(new ArrayList<>());
                        emptyView.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Error updating notifications", e);
                    Toast.makeText(this, "Error loading notifications. Please try again.", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e("MainActivity", "Error setting up notifications", e);
            Toast.makeText(this, "Error initializing notifications. Please restart the app.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isNotificationServiceEnabled() {
        String packageName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (flat != null) {
            return flat.contains(packageName);
        }
        return false;
    }

    private void showNotificationAccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notification Access Required")
                .setMessage("Please grant notification access to enable notification monitoring")
                .setPositiveButton("Grant Access", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check notification permission and restart service if needed
        if (isNotificationServiceEnabled()) {
            ensureNotificationServiceRunning();
        }
    }

    private void ensureNotificationServiceRunning() {
        // Toggle notification listener service to ensure it gets rebound
        toggleNotificationListenerService();

        // Start service explicitly
        Intent serviceIntent = new Intent(this, NotificationService.class);
        startService(serviceIntent);

        Log.d("MainActivity", "Ensuring notification service is running");
    }

    private void toggleNotificationListenerService() {
        // This is a workaround to force Android to rebind the notification listener service
        // Toggling the enabled state forces Android to rebind the service
        ComponentName componentName = new ComponentName(this, NotificationService.class);

        // Get package manager
        PackageManager pm = getPackageManager();

        // Disable component
        pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        // Enable component
        pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        Log.d("MainActivity", "Toggled notification listener service");
    }

    private void showAnalysisDialog(NotificationEntity notification) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_conversation_analysis, null);
        TextView nextStepsView = dialogView.findViewById(R.id.analysisNextSteps);
        TextView followupView = dialogView.findViewById(R.id.analysisFollowup);
        TextView negotiationView = dialogView.findViewById(R.id.analysisNegotiation);
        TextView suggestedReplyView = dialogView.findViewById(R.id.analysisSuggestedReply);
        TextView timestampView = dialogView.findViewById(R.id.analysisTimestamp);
        Button reAnalyzeButton = dialogView.findViewById(R.id.reAnalyzeButton);
        ImageButton copyButton = dialogView.findViewById(R.id.copyButton);

        // Handle copying analysis
        copyButton.setOnClickListener(v -> {
            StringBuilder fullAnalysis = new StringBuilder();
            fullAnalysis.append("Next Steps:\n").append(nextStepsView.getText()).append("\n\n");
            fullAnalysis.append("Follow-up Actions:\n").append(followupView.getText()).append("\n\n");
            fullAnalysis.append("Negotiation Points:\n").append(negotiationView.getText()).append("\n\n");
            fullAnalysis.append("Suggested Reply:\n").append(suggestedReplyView.getText());

            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Analysis", fullAnalysis.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(MainActivity.this, "Analysis copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .create();

        // Set initial loading state
        nextStepsView.setText("Loading analysis...");
        followupView.setText("");
        negotiationView.setText("");
        timestampView.setText("");

        // Function to update analysis UI
        Runnable updateAnalysis = () -> {
            // Use background thread for database operation
            new Thread(() -> {
                try {
                    final ConversationHistory history = AppDatabase.getDatabase(this)
                        .getConversationHistoryDao()
                        .getLatestHistoryForConversationSync(notification.getConversationId());

                    // Update UI on main thread
                    runOnUiThread(() -> {
                        if (history != null) {
                            String analysis = history.getAnalysis();
                            if (analysis != null) {
                                String[] sections = analysis.split("\\d\\.");
                                if (sections.length >= 5) {
                                    // Format the text by replacing dashes/asterisks with bullet points
                                    nextStepsView.setText(formatAnalysisSection(sections[1]));
                                    followupView.setText(formatAnalysisSection(sections[2]));
                                    negotiationView.setText(formatAnalysisSection(sections[3]));
                                    String suggestedReply = formatAnalysisSection(sections[4]);
                                    suggestedReplyView.setText(suggestedReply);
                                } else {
                                    nextStepsView.setText(analysis);
                                    followupView.setText("");
                                    negotiationView.setText("");
                                }
                                Long analysisTimestamp = history.getAnalysisTimestamp();
                                if (analysisTimestamp != null) {
                                    String timestamp = DateUtils.getRelativeTimeSpanString(
                                        analysisTimestamp,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    ).toString();
                                    timestampView.setText("Last analyzed: " + timestamp);
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e("MainActivity", "Error loading analysis", e);
                    runOnUiThread(() -> {
                        nextStepsView.setText("Error loading analysis");
                    });
                }
            }).start();
        };

        // Initial analysis load and check
        new Thread(() -> {
            try {
                final ConversationHistory history = AppDatabase.getDatabase(this)
                    .getConversationHistoryDao()
                    .getLatestHistoryForConversationSync(notification.getConversationId());

                runOnUiThread(() -> {
                    if (history != null) {
                        if (history.getAnalysis() == null) {
                            performAnalysis(notification.getConversationId(), updateAnalysis);
                        } else {
                            updateAnalysis.run();
                        }
                    } else {
                        nextStepsView.setText("No conversation history available");
                    }
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Error checking conversation history", e);
                runOnUiThread(() -> {
                    nextStepsView.setText("Error loading conversation history");
                });
            }
        }).start();

        // Re-analyze button click handler
        reAnalyzeButton.setOnClickListener(v ->
            performAnalysis(notification.getConversationId(), updateAnalysis));

        dialog.show();
    }

    private void performAnalysis(String conversationId, Runnable onComplete) {
        new GeminiService(this).analyzeConversation(
            conversationId,
            new GeminiService.ResponseCallback() {
                @Override
                public void onPartialResponse(String text) {}

                @Override
                public void onComplete(String fullResponse) {
                    runOnUiThread(() -> {
                        onComplete.run();
                        Toast.makeText(MainActivity.this,
                            "Analysis complete", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(Throwable error) {
                    runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                            "Analysis failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show());
                }
            }
        );
    }

    private void showOptionsMenu() {
        String[] options = {"Clear All", "Auto-Reply Settings", "Keyword Actions", "Gemini Configuration", "Backup Data", "Restore Data", "About"};
        new AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            clearAllNotifications();
                            break;
                        case 1:
                            startActivity(new Intent(this, AutoReplySettingsActivity.class));
                            break;
                        case 2:
                            startActivity(new Intent(this, KeywordActionsActivity.class));
                            break;
                        case 3:
                            startActivity(new Intent(this, GeminiConfigActivity.class));
                            break;
                        case 4:
                            showBackupDialog();
                            break;
                        case 5:
                            showRestoreDialog();
                            break;
                        case 6:
                            showAboutDialog();
                            break;
                    }
                })
                .show();
    }

    private void clearAllNotifications() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Notifications")
                .setMessage("Are you sure you want to clear all notifications?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    AppDatabase.getDatabase(this).notificationDao().deleteAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About")
                .setMessage("Notification History App\nVersion 1.0")
                .setPositiveButton("OK", null)
                .show();
    }

    private String formatAnalysisSection(String text) {
        if (text == null) return "";
        return text.trim()
            .replaceAll("(?m)^[-*]\\s*", "")  // Remove bullet points
            .replaceAll("(?m)^\\s+", "")      // Remove leading whitespace
            .replaceAll("(?m)^", "\u2022 ")   // Add consistent bullet points
            .replaceAll("\n{3,}", "\n\n")     // Normalize multiple line breaks
            .replaceAll("(?m)^\\s*([A-Z][^.!?:]*(?:[.!?:]\\s*)?)$", "\n$1\n") // Add spacing around headings
            .trim();
    }

    private void initializeBackupRestore() {
        backupRestoreManager = new BackupRestoreManager(this);

        // Initialize file picker for creating backup
        createBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            uri -> {
                if (uri != null) {
                    performBackup(uri);
                }
            }
        );

        // Initialize file picker for selecting backup to restore
        selectBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    performRestore(uri);
                }
            }
        );
    }

    private void showBackupDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Create Backup")
            .setMessage("This will create a backup of all your application data including:\n\n" +
                       "• All notifications\n" +
                       "• Conversation history\n" +
                       "• App settings\n" +
                       "• Gemini configuration\n" +
                       "• Keyword actions\n" +
                       "• Media files\n\n" +
                       "Choose a location to save the backup file.")
            .setPositiveButton("Create Backup", (dialog, which) -> {
                String fileName = "WhatSuit_Backup_" +
                    new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
                        .format(new java.util.Date()) + ".zip";
                createBackupLauncher.launch(fileName);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showRestoreDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Restore from Backup")
            .setMessage("⚠️ WARNING: This will replace ALL current data with data from the backup file.\n\n" +
                       "Current data that will be replaced:\n" +
                       "• All notifications\n" +
                       "• Conversation history\n" +
                       "• App settings\n" +
                       "• Gemini configuration\n" +
                       "• Keyword actions\n" +
                       "• Media files\n\n" +
                       "This action cannot be undone. Consider creating a backup first.")
            .setPositiveButton("Select Backup File", (dialog, which) -> {
                selectBackupLauncher.launch(new String[]{"application/zip", "application/octet-stream"});
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void performBackup(Uri destinationUri) {
        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("Creating Backup")
            .setMessage("Preparing backup...")
            .setCancelable(false)
            .create();
        progressDialog.show();

        backupRestoreManager.createBackup(destinationUri, new BackupRestoreManager.BackupRestoreCallback() {
            @Override
            public void onProgress(String message, int progress) {
                runOnUiThread(() -> {
                    progressDialog.setMessage(message + " (" + progress + "%)");
                });
            }

            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Backup Successful")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Backup Failed")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show();
                });
            }
        });
    }

    private void performRestore(Uri backupUri) {
        // Show final confirmation
        new AlertDialog.Builder(this)
            .setTitle("Final Confirmation")
            .setMessage("Are you absolutely sure you want to restore from this backup?\n\n" +
                       "This will permanently replace all current data and cannot be undone.")
            .setPositiveButton("Yes, Restore", (dialog, which) -> {
                // Show progress dialog
                AlertDialog progressDialog = new AlertDialog.Builder(this)
                    .setTitle("Restoring Backup")
                    .setMessage("Extracting backup...")
                    .setCancelable(false)
                    .create();
                progressDialog.show();

                backupRestoreManager.restoreBackup(backupUri, new BackupRestoreManager.BackupRestoreCallback() {
                    @Override
                    public void onProgress(String message, int progress) {
                        runOnUiThread(() -> {
                            progressDialog.setMessage(message + " (" + progress + "%)");
                        });
                    }

                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Restore Successful")
                                .setMessage(message + "\n\nThe app will now restart to apply the changes.")
                                .setPositiveButton("Restart App", (d, w) -> {
                                    // Restart the app
                                    Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                                    if (intent != null) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        finish();
                                    }
                                })
                                .setCancelable(false)
                                .show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Restore Failed")
                                .setMessage(error)
                                .setPositiveButton("OK", null)
                                .show();
                        });
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoReplyManager != null) {
            autoReplyManager.shutdown();
        }
    }
}
