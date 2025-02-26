package com.example.whatsuit;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AutoReplySettingsActivity extends AppCompatActivity {
    private Switch autoReplySwitch;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_reply_settings);

        prefs = getSharedPreferences("whatsuit_settings", MODE_PRIVATE);
        
        autoReplySwitch = findViewById(R.id.switch_auto_reply);
        autoReplySwitch.setChecked(prefs.getBoolean("auto_reply_enabled", false));
        
        autoReplySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_reply_enabled", isChecked).apply();
            Toast.makeText(this, 
                isChecked ? "Auto-reply enabled" : "Auto-reply disabled", 
                Toast.LENGTH_SHORT).show();
        });
    }
}