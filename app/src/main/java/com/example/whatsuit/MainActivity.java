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
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
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

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(getPackageManager());
        recyclerView.setAdapter(adapter);

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
        
        // Load notifications
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
                adapter.setNotifications(notifications);
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
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
        String[] options = {"Clear All", "Settings", "About"};
        new AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            clearAllNotifications();
                            break;
                        case 1:
                            // TODO: Open settings
                            break;
                        case 2:
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
