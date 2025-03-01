package com.example.whatsuit;

import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.os.Bundle;
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

public class AutoReplySettingsActivity extends AppCompatActivity implements AppSettingsAdapter.OnAppSettingChangedListener {
    private Switch autoReplySwitch;
    private RecyclerView appSettingsRecyclerView;
    private AppSettingsAdapter adapter;
    private SharedPreferences prefs;
    private AppDatabase database;
    private ExecutorService executorService;

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

    private void setupViews() {
        // Setup global auto-reply switch
        autoReplySwitch = findViewById(R.id.switch_auto_reply);
        
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

        // Setup RecyclerView
        appSettingsRecyclerView = findViewById(R.id.recycler_app_settings);
        appSettingsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppSettingsAdapter(this);
        appSettingsRecyclerView.setAdapter(adapter);
    }

    private void loadAppSettings() {
        executorService.execute(() -> {
            // Get supported messaging apps
            List<AppSettingEntity> appSettings = getMessagingApps();
            
            // Load saved settings from database
            for (AppSettingEntity setting : appSettings) {
                AppSettingEntity savedSetting = database.appSettingDao()
                    .getAppSetting(setting.getPackageName());
                
                if (savedSetting != null) {
                    setting.setAutoReplyEnabled(savedSetting.isAutoReplyEnabled());
                } else {
                    // Enable WhatsApp by default when first installing
                    if (setting.getPackageName().contains("whatsapp")) {
                        setting.setAutoReplyEnabled(true);
                        database.appSettingDao().insert(setting);
                    } else {
                        setting.setAutoReplyEnabled(false);
                    }
                }
            }

            // Update UI on main thread
            runOnUiThread(() -> adapter.setAppSettings(appSettings));
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
            "com.viber.voip"
,
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
                apps.add(new AppSettingEntity(packageName, appName, false));
            } catch (PackageManager.NameNotFoundException ignored) {
                // App is not installed, skip it
            }
        }

        return apps;
    }

    @Override
    public void onAppSettingChanged(AppSettingEntity setting, boolean enabled) {
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