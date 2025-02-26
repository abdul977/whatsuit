package com.example.whatsuit;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.AppInfo;
import com.example.whatsuit.data.NotificationDao;
import com.example.whatsuit.data.NotificationEntity;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private RecyclerView appHeadersRecyclerView;
    private NotificationAdapter notificationAdapter;
    private AppHeaderAdapter appHeaderAdapter;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefresh;
    private NotificationDao notificationDao;
    private ChipGroup filterChipGroup;
    private String selectedPackage = null;
    private List<AppInfo> appInfoList = new ArrayList<>();
    private ExtendedFloatingActionButton fab;
    private AppBarLayout appBarLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        recyclerView = findViewById(R.id.notificationsRecyclerView);
        filterChipGroup = findViewById(R.id.filterChipGroup);
        emptyView = findViewById(R.id.emptyView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        fab = findViewById(R.id.fab);
        appBarLayout = findViewById(R.id.appBarLayout);

        // Set up app headers RecyclerView
        appHeadersRecyclerView = findViewById(R.id.appHeadersRecyclerView);
        appHeaderAdapter = new AppHeaderAdapter(getPackageManager());
        appHeaderAdapter.setOnAppHeaderClickListener(header -> {
            // Find and select corresponding chip
            int chipCount = filterChipGroup.getChildCount();
            for (int i = 1; i < chipCount; i++) { // Start from 1 to skip "All Apps"
                Chip chip = (Chip) filterChipGroup.getChildAt(i);
                if (chip.getText().equals(header.appName)) {
                    chip.setChecked(true);
                    break;
                }
            }
        });
        appHeadersRecyclerView.setAdapter(appHeaderAdapter);

        // Set up notifications RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new NotificationAdapter();
        recyclerView.setAdapter(notificationAdapter);

        // Initialize database
        notificationDao = AppDatabase.getDatabase(this).notificationDao();

        // Set up swipe to refresh
        swipeRefresh.setOnRefreshListener(() -> {
            loadNotifications();
            swipeRefresh.setRefreshing(false);
        });

        // Set up collapsing toolbar behavior
        setupCollapsingToolbar();

        // FAB click listener
        fab.setOnClickListener(v -> showOptionsMenu());

        // Check for notification access permission
        if (!isNotificationServiceEnabled()) {
            showNotificationAccessDialog();
        }

        // Setup app filter
        setupAppFilter();
        
        // Load notifications and update headers
        loadNotifications();

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupCollapsingToolbar() {
        // Show/Hide FAB based on scroll
        appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (Math.abs(verticalOffset) == appBarLayout.getTotalScrollRange()) {
                // Collapsed
                fab.shrink();
            } else {
                // Expanded or in between
                fab.extend();
            }
        });
    }

    private void setupAppFilter() {
        // Add "All Apps" chip
        Chip allAppsChip = new Chip(this);
        allAppsChip.setText("All Apps");
        allAppsChip.setCheckable(true);
        allAppsChip.setChecked(true);
        filterChipGroup.addView(allAppsChip);

        // Load app list and create chips
        notificationDao.getDistinctApps().observe(this, apps -> {
            // Clear existing chips except "All Apps"
            filterChipGroup.removeViews(1, filterChipGroup.getChildCount() - 1);
            appInfoList.clear();
            appInfoList.addAll(apps);

            // Add chip for each app
            for (AppInfo app : apps) {
                Chip chip = new Chip(this);
                chip.setText(app.getAppName());
                chip.setCheckable(true);
                filterChipGroup.addView(chip);
            }
        });

        // Handle chip selection
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                // Ensure "All Apps" is selected if nothing else is
                allAppsChip.setChecked(true);
                selectedPackage = null;
            } else {
                int selectedChipId = checkedIds.get(0);
                Chip selectedChip = group.findViewById(selectedChipId);
                int chipIndex = group.indexOfChild(selectedChip) - 1; // -1 for "All Apps" chip

                if (chipIndex == -1) { // "All Apps" selected
                    selectedPackage = null;
                } else if (chipIndex < appInfoList.size()) {
                    selectedPackage = appInfoList.get(chipIndex).getPackageName();
                }
            }
            loadNotifications();
        });
    }

    private void loadNotifications() {
        LiveData<List<NotificationEntity>> notificationsLiveData = selectedPackage == null ?
            notificationDao.getAllNotifications() :
            notificationDao.getNotificationsForApp(selectedPackage);
        notificationsLiveData.observe(this, notifications -> {
            if (notifications != null && !notifications.isEmpty()) {
                notificationAdapter.setNotifications(notifications);
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);

                // Update app headers
                Map<String, Integer> appCounts = new LinkedHashMap<>();
                Map<String, String> appNames = new LinkedHashMap<>();
                for (NotificationEntity notification : notifications) {
                    appCounts.merge(notification.getPackageName(), 1, Integer::sum);
                    appNames.putIfAbsent(notification.getPackageName(), notification.getAppName());
                }

                List<AppHeaderAdapter.AppHeader> headers = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : appCounts.entrySet()) {
                    headers.add(new AppHeaderAdapter.AppHeader(
                        entry.getKey(),
                        appNames.get(entry.getKey()),
                        entry.getValue()
                    ));
                }
                appHeaderAdapter.setHeaders(headers);
            } else {
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                appHeaderAdapter.setHeaders(new ArrayList<>());
            }
        });
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

    private void showOptionsMenu() {
        String[] options = {"Clear All", "Auto-Reply Settings", "Settings", "About"};
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
                            // TODO: Open general settings
                            break;
                        case 3:
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
}
