package com.example.whatsuit;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.whatsuit.adapter.DetailPagerAdapter;
import com.example.whatsuit.util.AutoReplyManager;
import com.example.whatsuit.util.TimeFilterHelper;
import com.example.whatsuit.viewmodel.NotificationDetailViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class NotificationDetailActivity extends AppCompatActivity implements AutoReplyProvider {
    private TextView appNameTextView;
    private TextView titleTextView;
    private NotificationDetailViewModel viewModel;
    private TimeFilterHelper timeFilterHelper;
    private AutoReplyManager autoReplyManager;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private DetailPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);

        initializeViews();
        setupViewModel();
        setupHelpers();
        setupViewPager();
        handleIntent(getIntent());
    }

    private void initializeViews() {
        titleTextView = findViewById(R.id.titleTextView);
        appNameTextView = findViewById(R.id.appNameTextView);
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

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
                finish();
            }
        });
    }

    private void setupHelpers() {
        timeFilterHelper = new TimeFilterHelper(this, (startTime, endTime) -> 
            viewModel.filterNotificationsByTimeRange(startTime, endTime));

        autoReplyManager = new AutoReplyManager(this);
    }

    @Override
    public AutoReplyManager getAutoReplyManager() {
        return autoReplyManager;
    }

    private void setupViewPager() {
        pagerAdapter = new DetailPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager,
            (tab, position) -> {
                switch (position) {
                    case DetailPagerAdapter.POSITION_NOTIFICATIONS:
                        tab.setText(R.string.tab_notifications);
                        break;
                    case DetailPagerAdapter.POSITION_CONVERSATIONS:
                        tab.setText(R.string.tab_conversations);
                        break;
                }
            }
        ).attach();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent.getData();
        if (data != null && "whatsuit".equals(data.getScheme())) {
            String notificationId = data.getLastPathSegment();
            if (notificationId != null) {
                try {
                    viewModel.loadNotification(Long.parseLong(notificationId));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    finish();
                }
            }
        } else if (intent.hasExtra("notification_id")) {
            long notificationId = intent.getLongExtra("notification_id", -1);
            if (notificationId != -1) {
                viewModel.loadNotification(notificationId);
            }
        } else {
            finish();
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup AutoReplyManager
        if (autoReplyManager != null) {
            autoReplyManager.shutdown();
            autoReplyManager = null;
        }

        // Clear view model observers
        if (viewModel != null) {
            viewModel.getCurrentNotification().removeObservers(this);
        }

        // Release ViewPager2 resources
        if (viewPager != null) {
            viewPager.setAdapter(null);
            viewPager = null;
        }

        // Clear TabLayout
        if (tabLayout != null) {
            tabLayout.removeAllTabs();
            tabLayout = null;
        }

        // Cleanup fragment references
        if (pagerAdapter != null) {
            pagerAdapter = null;
        }

        // Clear UI references
        titleTextView = null;
        appNameTextView = null;
        
        // Clear helper references
        timeFilterHelper = null;
        viewModel = null;
    }
}
