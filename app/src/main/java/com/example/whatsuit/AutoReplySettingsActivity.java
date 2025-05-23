package com.example.whatsuit;

import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.whatsuit.adapter.AppSettingsAdapter;
import com.example.whatsuit.data.AppDatabase;
import com.example.whatsuit.data.AppSettingEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoReplySettingsActivity extends AppCompatActivity {
    private Switch autoReplySwitch;
    private Switch autoReplyGroupsSwitch;
    private EditText replyLimitEditText;
    private RecyclerView defaultAppsRecyclerView;
    private RecyclerView appSettingsRecyclerView;
    private AppSettingsAdapter defaultAppsAdapter;
    private AppSettingsAdapter otherAppsAdapter;
    private SharedPreferences prefs;
    private AppDatabase database;
    private ExecutorService executorService;

    // Package name for default app
    private static final String DEFAULT_APP = "com.whatsapp.w4b"; // WhatsApp Business

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_reply_settings);

        executorService = Executors.newSingleThreadExecutor();
        database = AppDatabase.getDatabase(this);
        prefs = getSharedPreferences("whatsuit_settings", MODE_PRIVATE);

        setupViews();
        loadAppSettings();
    }

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupViews() {
        setupToolbar();
        autoReplySwitch = findViewById(R.id.switch_auto_reply);
        autoReplyGroupsSwitch = findViewById(R.id.switch_auto_reply_groups);
        replyLimitEditText = findViewById(R.id.edit_reply_limit);

        // Enable auto-reply by default if not set
        if (!prefs.contains("auto_reply_enabled")) {
            prefs.edit().putBoolean("auto_reply_enabled", true).apply();
        }
        autoReplySwitch.setChecked(prefs.getBoolean("auto_reply_enabled", true));

        autoReplySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_reply_enabled", isChecked).apply();
            Toast.makeText(this,
                isChecked ? "Auto-reply enabled" : "Auto-reply disabled",
                Toast.LENGTH_SHORT).show();
        });

        // Enable auto-reply for groups by default if not set
        if (!prefs.contains("auto_reply_groups_enabled")) {
            prefs.edit().putBoolean("auto_reply_groups_enabled", true).apply();
        }
        autoReplyGroupsSwitch.setChecked(prefs.getBoolean("auto_reply_groups_enabled", true));

        autoReplyGroupsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_reply_groups_enabled", isChecked).apply();
            Toast.makeText(this,
                isChecked ? "Auto-reply for groups enabled" : "Auto-reply for groups disabled",
                Toast.LENGTH_SHORT).show();
        });

        // Setup reply limit setting
        setupReplyLimitSetting();

        // Setup RecyclerViews
        defaultAppsRecyclerView = findViewById(R.id.recycler_default_apps);
        defaultAppsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        defaultAppsAdapter = new AppSettingsAdapter((setting, enabled) -> onAppSettingChanged(setting, enabled));
        defaultAppsRecyclerView.setAdapter(defaultAppsAdapter);

        appSettingsRecyclerView = findViewById(R.id.recycler_app_settings);
        appSettingsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        otherAppsAdapter = new AppSettingsAdapter((setting, enabled) -> onAppSettingChanged(setting, enabled));
        appSettingsRecyclerView.setAdapter(otherAppsAdapter);
    }

    private void setupReplyLimitSetting() {
        // Set default reply limit if not set
        if (!prefs.contains("auto_reply_limit")) {
            prefs.edit().putInt("auto_reply_limit", 4).apply();
        }

        int currentLimit = prefs.getInt("auto_reply_limit", 4);
        replyLimitEditText.setText(String.valueOf(currentLimit));

        replyLimitEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String text = s.toString().trim();
                    if (!text.isEmpty()) {
                        int limit = Integer.parseInt(text);
                        if (limit > 0 && limit <= 100) { // Reasonable bounds
                            prefs.edit().putInt("auto_reply_limit", limit).apply();
                        } else if (limit > 100) {
                            // Reset to max allowed value
                            replyLimitEditText.setText("100");
                            Toast.makeText(AutoReplySettingsActivity.this,
                                "Maximum limit is 100", Toast.LENGTH_SHORT).show();
                        } else if (limit <= 0) {
                            // Reset to minimum allowed value
                            replyLimitEditText.setText("1");
                            Toast.makeText(AutoReplySettingsActivity.this,
                                "Minimum limit is 1", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (NumberFormatException e) {
                    // Invalid number, ignore
                }
            }
        });
    }

    private void loadAppSettings() {
        executorService.execute(() -> {
            List<AppSettingEntity> allApps = getMessagingApps();
            List<AppSettingEntity> defaultApps = new ArrayList<>();
            List<AppSettingEntity> otherApps = new ArrayList<>();

            // Split apps into default and other categories
            for (AppSettingEntity setting : allApps) {
                AppSettingEntity savedSetting = database.appSettingDao()
                    .getAppSetting(setting.getPackageName());

                if (savedSetting != null) {
                    setting.setAutoReplyEnabled(savedSetting.isAutoReplyEnabled());
                    setting.setAutoReplyGroupsEnabled(savedSetting.isAutoReplyGroupsEnabled());
                } else {
                    // Enable WhatsApp Business by default
                    if (setting.getPackageName().equals(DEFAULT_APP)) {
                        setting.setAutoReplyEnabled(true);
                        setting.setAutoReplyGroupsEnabled(true);
                        database.appSettingDao().insert(setting);
                    } else {
                        setting.setAutoReplyEnabled(false);
                        setting.setAutoReplyGroupsEnabled(false);
                    }
                }

                // Add to appropriate list
                if (setting.getPackageName().equals(DEFAULT_APP)) {
                    defaultApps.add(setting);
                } else {
                    otherApps.add(setting);
                }
            }

            // Update UI on main thread
            runOnUiThread(() -> {
                defaultAppsAdapter.setAppSettings(defaultApps);
                otherAppsAdapter.setAppSettings(otherApps);
            });
        });
    }

    private List<AppSettingEntity> getMessagingApps() {
        List<AppSettingEntity> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();

        // List of supported messaging app package names
        String[] supportedApps = {
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "org.telegram.messenger",
            "com.facebook.mlite",
            "com.facebook.orca.lite",
            "com.instagram.android",
            "com.twitter.android",
            "com.linkedin.android",
            "org.thoughtcrime.securesms",
            "com.facebook.pages.app",
            "com.viber.voip",
            // Additional WhatsApp variants
            "com.gbwhatsapp",
            "com.whatsapp.plus",
            // Additional Facebook Messenger variants
            "com.facebook.mlite",
            // Additional Telegram variants
            "org.telegram.messenger.web",
            "org.telegram.messenger.beta",
            // Additional Instagram variants
            "com.instagram.lite",
            "com.instagram.android.direct"
        };

        for (String packageName : supportedApps) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                String appName = pm.getApplicationLabel(info).toString();
                apps.add(new AppSettingEntity(packageName, appName, false, false));
            } catch (PackageManager.NameNotFoundException ignored) {
                // App is not installed, skip it
            }
        }

        return apps;
    }

    private void onAppSettingChanged(AppSettingEntity setting, boolean enabled) {
        executorService.execute(() -> {
            setting.setAutoReplyEnabled(enabled);
            database.appSettingDao().insert(setting);

            runOnUiThread(() -> {
                Toast.makeText(this,
                    setting.getAppName() + (enabled ? " enabled" : " disabled"),
                    Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
