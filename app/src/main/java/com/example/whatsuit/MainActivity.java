package com.example.whatsuit;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioGroup;
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
import com.example.whatsuit.util.AutoReplyManager;
import com.example.whatsuit.adapter.GroupedNotificationAdapter;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private GroupedNotificationAdapter notificationAdapter;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefresh;
    private NotificationDao notificationDao;
    private AutoReplyManager autoReplyManager;
    private ChipGroup filterChipGroup;
    private String selectedPackage = null;
    private List<AppInfo> appInfoList = new ArrayList<>();
    private ExtendedFloatingActionButton fab;
    private AppBarLayout appBarLayout;
    private long startTime = 0;
    private long endTime = Long.MAX_VALUE;

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

        // Initialize managers
        autoReplyManager = new AutoReplyManager(this);

        // Set up notifications RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new GroupedNotificationAdapter(getPackageManager(), autoReplyManager);
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
        
        // Load notifications
        loadNotifications();

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            showTimeFilterDialog();
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

    private LiveData<List<NotificationEntity>> currentNotificationsLiveData;

    private void loadNotifications() {
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
            if (notifications != null && !notifications.isEmpty()) {
                notificationAdapter.updateNotifications(notifications);
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                notificationAdapter.updateNotifications(new ArrayList<>());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoReplyManager != null) {
            autoReplyManager.shutdown();
        }
    }
}
